package de.bund.zrb.service;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class OnnxChatManager implements ChatManager {

    private static final Logger LOG = Logger.getLogger(OnnxChatManager.class.getName());

    private static final String CONFIG_MODEL_PATH = "onnx.model.path";
    private static final String CONFIG_EXECUTION_PROVIDER = "onnx.execution.provider";
    private static final String CONFIG_MAX_TOKENS = "onnx.max.tokens";
    private static final String CONFIG_TEMPERATURE = "onnx.temperature";
    private static final String CONFIG_TOP_P = "onnx.top.p";
    private static final String CONFIG_TOP_K = "onnx.top.k";

    private static final String PROVIDER_DIRECTML = "directml";

    private final Supplier<Map<String, String>> configSupplier;
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<UUID, ChatHistory>();
    private final Map<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<UUID, AtomicBoolean>();
    private final ExecutorService executor;
    private final Random random = new Random();

    private volatile OrtEnvironment environment;
    private volatile OrtSession session;
    private volatile String loadedModelPath;
    private volatile String loadedExecutionProvider;
    private volatile OnnxTokenizer tokenizer;

    public OnnxChatManager(Supplier<Map<String, String>> configSupplier) {
        this.configSupplier = configSupplier != null
                ? configSupplier
                : new Supplier<Map<String, String>>() {
                    @Override
                    public Map<String, String> get() {
                        return Collections.emptyMap();
                    }
                };

        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "onnx-chat-manager");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private Map<String, String> config() {
        Map<String, String> config = configSupplier.get();
        return config != null ? config : Collections.<String, String>emptyMap();
    }

    @Override
    public UUID newSession() {
        UUID sessionId = UUID.randomUUID();
        sessionHistories.put(sessionId, new ChatHistory(sessionId));
        return sessionId;
    }

    @Override
    public List<String> getFormattedHistory(UUID sessionId) {
        ChatHistory history = sessionHistories.get(sessionId);
        return history != null ? history.getFormattedHistory() : Collections.<String>emptyList();
    }

    @Override
    public ChatHistory getHistory(UUID sessionId) {
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
    }

    @Override
    public void clearHistory(UUID sessionId) {
        ChatHistory history = sessionHistories.get(sessionId);
        if (history != null) {
            history.clear();
        }
    }

    @Override
    public void addUserMessage(UUID sessionId, String message) {
        getHistory(sessionId).addUserMessage(message);
    }

    @Override
    public void addBotMessage(UUID sessionId, String message) {
        getHistory(sessionId).addBotMessage(message);
    }

    @Override
    public boolean streamAnswer(UUID sessionId,
                                boolean useContext,
                                String userInput,
                                ChatStreamListener listener,
                                boolean keepAlive) throws IOException {
        if (sessionId == null) {
            return false;
        }

        final Map<String, String> currentConfig = config();
        final String modelPath = trimToEmpty(currentConfig.get(CONFIG_MODEL_PATH));
        final String executionProvider = normalizeExecutionProvider(
                currentConfig.getOrDefault(CONFIG_EXECUTION_PROVIDER, "")
        );
        final int maxTokens = parseInt(currentConfig.get(CONFIG_MAX_TOKENS), 256);
        final double temperature = parseDouble(currentConfig.get(CONFIG_TEMPERATURE), 0.0d);
        final double topP = parseDouble(currentConfig.get(CONFIG_TOP_P), 0.9d);
        final int topK = parseInt(currentConfig.get(CONFIG_TOP_K), 40);

        if (modelPath.isEmpty()) {
            listener.onError(new IOException("ONNX model path is missing."));
            return false;
        }

        if (!PROVIDER_DIRECTML.equals(executionProvider)) {
            listener.onError(new IOException(
                    "Invalid ONNX execution provider. Expected 'directml' but got '" + executionProvider + "'."
            ));
            return false;
        }

        final ChatHistory history = useContext
                ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new)
                : null;

        final String prompt = buildPrompt(history, userInput);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(sessionId, cancelled);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureModelLoaded(modelPath, executionProvider);

                    if (history != null) {
                        history.addUserMessage(userInput);
                    }

                    listener.onStreamStart();

                    String response = generate(
                            prompt,
                            maxTokens,
                            temperature,
                            topP,
                            topK,
                            cancelled,
                            listener
                    );

                    if (history != null) {
                        history.addBotMessage(response);
                    }

                    listener.onStreamEnd();
                } catch (Exception exception) {
                    LOG.warning("ONNX inference failed: " + exception.getMessage());
                    listener.onError(exception);
                } finally {
                    cancelFlags.remove(sessionId);
                }
            }
        });

        return true;
    }

    @Override
    public void cancel(UUID sessionId) {
        AtomicBoolean flag = cancelFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
        }
    }

    @Override
    public void onDispose() {
        executor.shutdownNow();
        closeOrtSession();
    }

    @Override
    public void closeSession(UUID sessionId) {
        sessionHistories.remove(sessionId);
        cancelFlags.remove(sessionId);
    }

    private synchronized void ensureModelLoaded(String modelPath, String executionProvider)
            throws OrtException, IOException {
        if (session != null
                && modelPath.equals(loadedModelPath)
                && executionProvider.equals(loadedExecutionProvider)) {
            return;
        }

        closeOrtSession();

        Path modelDirectoryOrFile = Paths.get(modelPath);
        Path modelFile = resolveModelFile(modelDirectoryOrFile);
        if (modelFile == null) {
            throw new IOException(
                    "No ONNX model found in: " + modelPath
                            + ". Expect a .onnx file or a directory containing model.onnx."
            );
        }

        Path modelDirectory = Files.isDirectory(modelDirectoryOrFile)
                ? modelDirectoryOrFile
                : modelDirectoryOrFile.getParent();

        if (modelDirectory == null) {
            throw new IOException("Cannot resolve model directory for: " + modelPath);
        }

        // DirectML: download native DLLs from NuGet if needed, set onnxruntime.native.path
        // MUST happen BEFORE OrtEnvironment.getEnvironment() (static native lib loading)
        if (PROVIDER_DIRECTML.equals(executionProvider)) {
            DirectMlNativeLoader.ensureAvailable(new DirectMlNativeLoader.ProgressCallback() {
                @Override
                public void onMessage(String message) {
                    LOG.info("[DirectML Setup] " + message);
                }
            });
        }

        environment = OrtEnvironment.getEnvironment();

        OrtSession.SessionOptions sessionOptions = createDirectMlSessionOptions();

        long startedAt = System.currentTimeMillis();
        LOG.info("Load ONNX model from: " + modelFile);
        LOG.info("Force execution provider: DirectML");

        session = environment.createSession(modelFile.toString(), sessionOptions);
        loadedModelPath = modelPath;
        loadedExecutionProvider = executionProvider;
        tokenizer = new OnnxTokenizer(modelDirectory);

        long durationMs = System.currentTimeMillis() - startedAt;
        LOG.info("Model loaded in " + durationMs + " ms");
        LOG.info("Model inputs: " + session.getInputNames());
        LOG.info("Model outputs: " + session.getOutputNames());
    }

    private OrtSession.SessionOptions createDirectMlSessionOptions() throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setMemoryPatternOptimization(false);
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
        options.addDirectML(0);
        return options;
    }

    private Path resolveModelFile(Path modelDirectoryOrFile) {
        if (modelDirectoryOrFile == null) {
            return null;
        }

        if (Files.isRegularFile(modelDirectoryOrFile)
                && modelDirectoryOrFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".onnx")) {
            return modelDirectoryOrFile;
        }

        if (!Files.isDirectory(modelDirectoryOrFile)) {
            return null;
        }

        Path preferredModel = modelDirectoryOrFile.resolve("model.onnx");
        if (Files.isRegularFile(preferredModel)) {
            return preferredModel;
        }

        java.io.File[] files = modelDirectoryOrFile.toFile().listFiles();
        if (files == null) {
            return null;
        }

        Path firstOnnx = null;
        for (java.io.File file : files) {
            String fileName = file.getName().toLowerCase(Locale.ROOT);
            if (!fileName.endsWith(".onnx")) {
                continue;
            }
            if ("model.onnx".equals(fileName)) {
                return file.toPath();
            }
            if (fileName.contains("decoder")) {
                return file.toPath();
            }
            if (firstOnnx == null) {
                firstOnnx = file.toPath();
            }
        }

        return firstOnnx;
    }

    private void closeOrtSession() {
        if (session != null) {
            try {
                session.close();
            } catch (Throwable ignore) {
            }
            session = null;
        }

        loadedModelPath = null;
        loadedExecutionProvider = null;
        tokenizer = null;
    }

    private String generate(String prompt,
                            int maxTokens,
                            double temperature,
                            double topP,
                            int topK,
                            AtomicBoolean cancelled,
                            ChatStreamListener listener) throws OrtException {

        long[] promptTokenIds = tokenizer.encode(prompt);
        StringBuilder generatedText = new StringBuilder();

        Set<Integer> eosTokenIds = tokenizer.getEosTokenIds();
        int fallbackEosTokenId = tokenizer.getEosTokenId();
        int layerCount = tokenizer.getNumLayers();
        int headSize = tokenizer.getHeadSize();
        int kvHeadCount = tokenizer.getNumKvHeads();

        boolean needsPositionIds = session.getInputNames().contains("position_ids");
        boolean usesKvCache = session.getInputNames().contains("past_key_values.0.key");

        Set<String> requestedOutputs = createRequestedOutputs(usesKvCache, layerCount);

        ByteBuffer[] kvKeyBuffers = null;
        ByteBuffer[] kvValueBuffers = null;

        int cachedSequenceLength = 0;
        int totalSequenceLength = promptTokenIds.length;
        long lastGeneratedTokenId = -1L;

        long generationStartedAt = System.currentTimeMillis();

        LOG.info("Start generation with promptTokens=" + promptTokenIds.length
                + ", maxTokens=" + maxTokens
                + ", provider=" + loadedExecutionProvider
                + ", kvCache=" + usesKvCache
                + ", positionIds=" + needsPositionIds);

        for (int step = 0; step < maxTokens; step++) {
            if (cancelled.get()) {
                LOG.info("Generation cancelled");
                break;
            }

            Map<String, OnnxTensor> inputs = new HashMap<String, OnnxTensor>();
            OrtSession.Result result = null;

            try {
                addInputIds(inputs, promptTokenIds, lastGeneratedTokenId, step);
                addAttentionMask(inputs, totalSequenceLength);
                addPositionIds(inputs, promptTokenIds, totalSequenceLength, needsPositionIds, step);
                addKvCacheInputs(inputs, usesKvCache, kvKeyBuffers, kvValueBuffers, cachedSequenceLength, kvHeadCount, headSize);

                long stepStartedAt = System.currentTimeMillis();
                result = session.run(inputs, requestedOutputs);

                if (step == 0) {
                    LOG.info("Prefill finished in " + (System.currentTimeMillis() - stepStartedAt) + " ms");
                }

                float[] lastTokenLogits = extractLastLogits(result);

                if (usesKvCache) {
                    KvCacheState kvCacheState = copyPresentKvCache(result, layerCount);
                    kvKeyBuffers = kvCacheState.keyBuffers;
                    kvValueBuffers = kvCacheState.valueBuffers;
                    cachedSequenceLength = kvCacheState.sequenceLength;
                }

                int nextTokenId = selectNextToken(lastTokenLogits, temperature, topP, topK);

                if (eosTokenIds.contains(Integer.valueOf(nextTokenId)) || nextTokenId == fallbackEosTokenId) {
                    LOG.info("Encounter EOS after " + (step + 1) + " generated tokens");
                    break;
                }

                lastGeneratedTokenId = nextTokenId;
                totalSequenceLength++;

                String tokenText = tokenizer.decode(new long[] { nextTokenId });
                // Strip leading space from first generated token (normalizer prepend artifact)
                if (step == 0 && tokenText.startsWith(" ")) {
                    tokenText = tokenText.substring(1);
                }
                generatedText.append(tokenText);
                listener.onStreamChunk(tokenText);
            } finally {
                closeInputs(inputs);
                if (result != null) {
                    result.close();
                }
            }
        }

        long totalDurationMs = System.currentTimeMillis() - generationStartedAt;
        int generatedTokenCount = totalSequenceLength - promptTokenIds.length;
        if (generatedTokenCount > 0 && totalDurationMs > 0L) {
            double tokensPerSecond = generatedTokenCount * 1000.0d / totalDurationMs;
            LOG.info("Generated " + generatedTokenCount + " tokens in " + totalDurationMs
                    + " ms (" + String.format(Locale.US, "%.2f", tokensPerSecond) + " tok/s)");
        }

        return generatedText.toString();
    }

    private Set<String> createRequestedOutputs(boolean usesKvCache, int layerCount) {
        Set<String> outputs = new LinkedHashSet<String>();
        outputs.add("logits");

        if (usesKvCache) {
            for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
                outputs.add("present." + layerIndex + ".key");
                outputs.add("present." + layerIndex + ".value");
            }
        }

        return outputs;
    }

    private void addInputIds(Map<String, OnnxTensor> inputs,
                             long[] promptTokenIds,
                             long lastGeneratedTokenId,
                             int step) throws OrtException {
        if (step == 0) {
            inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][] { promptTokenIds }));
            return;
        }

        inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][] { new long[] { lastGeneratedTokenId } }));
    }

    private void addAttentionMask(Map<String, OnnxTensor> inputs, int totalSequenceLength) throws OrtException {
        long[] mask = new long[totalSequenceLength];
        Arrays.fill(mask, 1L);
        inputs.put("attention_mask", OnnxTensor.createTensor(environment, new long[][] { mask }));
    }

    private void addPositionIds(Map<String, OnnxTensor> inputs,
                                long[] promptTokenIds,
                                int totalSequenceLength,
                                boolean needsPositionIds,
                                int step) throws OrtException {
        if (!needsPositionIds) {
            return;
        }

        if (step == 0) {
            long[] positions = new long[promptTokenIds.length];
            for (int index = 0; index < promptTokenIds.length; index++) {
                positions[index] = index;
            }
            inputs.put("position_ids", OnnxTensor.createTensor(environment, new long[][] { positions }));
            return;
        }

        inputs.put("position_ids", OnnxTensor.createTensor(
                environment,
                new long[][] { new long[] { totalSequenceLength - 1L } }
        ));
    }

    private void addKvCacheInputs(Map<String, OnnxTensor> inputs,
                                  boolean usesKvCache,
                                  ByteBuffer[] kvKeyBuffers,
                                  ByteBuffer[] kvValueBuffers,
                                  int cachedSequenceLength,
                                  int kvHeadCount,
                                  int headSize) throws OrtException {
        if (!usesKvCache) {
            return;
        }

        if (kvKeyBuffers == null || kvValueBuffers == null) {
            long[] emptyShape = new long[] { 1L, kvHeadCount, 0L, headSize };
            for (int layerIndex = 0; layerIndex < tokenizer.getNumLayers(); layerIndex++) {
                ByteBuffer emptyKeyBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
                ByteBuffer emptyValueBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

                inputs.put("past_key_values." + layerIndex + ".key",
                        OnnxTensor.createTensor(environment, emptyKeyBuffer, emptyShape, OnnxJavaType.FLOAT16));
                inputs.put("past_key_values." + layerIndex + ".value",
                        OnnxTensor.createTensor(environment, emptyValueBuffer, emptyShape, OnnxJavaType.FLOAT16));
            }
            return;
        }

        long[] pastShape = new long[] { 1L, kvHeadCount, cachedSequenceLength, headSize };
        for (int layerIndex = 0; layerIndex < tokenizer.getNumLayers(); layerIndex++) {
            kvKeyBuffers[layerIndex].rewind();
            kvValueBuffers[layerIndex].rewind();

            inputs.put("past_key_values." + layerIndex + ".key",
                    OnnxTensor.createTensor(environment, kvKeyBuffers[layerIndex], pastShape, OnnxJavaType.FLOAT16));
            inputs.put("past_key_values." + layerIndex + ".value",
                    OnnxTensor.createTensor(environment, kvValueBuffers[layerIndex], pastShape, OnnxJavaType.FLOAT16));
        }
    }

    private KvCacheState copyPresentKvCache(OrtSession.Result result, int layerCount) throws OrtException {
        ByteBuffer[] keyBuffers = new ByteBuffer[layerCount];
        ByteBuffer[] valueBuffers = new ByteBuffer[layerCount];
        int sequenceLength = 0;

        for (int layerIndex = 0; layerIndex < layerCount; layerIndex++) {
            OnnxTensor presentKeyTensor = (OnnxTensor) result.get("present." + layerIndex + ".key").get();
            OnnxTensor presentValueTensor = (OnnxTensor) result.get("present." + layerIndex + ".value").get();

            keyBuffers[layerIndex] = copyDirectBuffer(presentKeyTensor.getByteBuffer());
            valueBuffers[layerIndex] = copyDirectBuffer(presentValueTensor.getByteBuffer());

            if (layerIndex == 0) {
                long[] shape = presentKeyTensor.getInfo().getShape();
                sequenceLength = shape.length > 2 ? (int) shape[2] : 0;
            }
        }

        return new KvCacheState(keyBuffers, valueBuffers, sequenceLength);
    }

    private float[] extractLastLogits(OrtSession.Result result) throws OrtException {
        OnnxTensor logitsTensor = (OnnxTensor) result.get("logits").get();
        long[] shape = logitsTensor.getInfo().getShape();
        int sequenceLength = (int) shape[1];
        int vocabularySize = (int) shape[2];

        try {
            float[][][] logits = (float[][][]) logitsTensor.getValue();
            return logits[0][sequenceLength - 1];
        } catch (OrtException ignored) {
            ByteBuffer fp16Buffer = logitsTensor.getByteBuffer();
            fp16Buffer.order(ByteOrder.LITTLE_ENDIAN);

            int tokenOffset = (sequenceLength - 1) * vocabularySize * 2;
            float[] lastLogits = new float[vocabularySize];

            for (int index = 0; index < vocabularySize; index++) {
                short fp16Value = fp16Buffer.getShort(tokenOffset + index * 2);
                lastLogits[index] = fp16ToFloat(fp16Value);
            }

            return lastLogits;
        }
    }

    private int selectNextToken(float[] logits, double temperature, double topP, int topK) {
        if (temperature <= 0.0d) {
            return argMax(logits);
        }

        float[] probabilities = softmaxWithTemperature(logits, temperature);
        applyTopK(probabilities, topK);
        applyTopP(probabilities, topP);

        float probabilitySum = 0.0f;
        for (int index = 0; index < probabilities.length; index++) {
            probabilitySum += probabilities[index];
        }

        if (probabilitySum <= 0.0f) {
            return argMax(logits);
        }

        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] = probabilities[index] / probabilitySum;
        }

        float randomThreshold = random.nextFloat();
        float cumulativeProbability = 0.0f;

        for (int index = 0; index < probabilities.length; index++) {
            cumulativeProbability += probabilities[index];
            if (cumulativeProbability >= randomThreshold) {
                return index;
            }
        }

        return probabilities.length - 1;
    }

    private float[] softmaxWithTemperature(float[] logits, double temperature) {
        float[] scaled = new float[logits.length];
        float max = Float.NEGATIVE_INFINITY;

        for (int index = 0; index < logits.length; index++) {
            scaled[index] = (float) (logits[index] / temperature);
            if (scaled[index] > max) {
                max = scaled[index];
            }
        }

        float sum = 0.0f;
        for (int index = 0; index < scaled.length; index++) {
            scaled[index] = (float) Math.exp(scaled[index] - max);
            sum += scaled[index];
        }

        if (sum <= 0.0f) {
            return scaled;
        }

        for (int index = 0; index < scaled.length; index++) {
            scaled[index] = scaled[index] / sum;
        }

        return scaled;
    }

    private void applyTopK(float[] probabilities, int topK) {
        if (topK <= 0 || topK >= probabilities.length) {
            return;
        }

        float[] sorted = probabilities.clone();
        Arrays.sort(sorted);
        float threshold = sorted[sorted.length - topK];

        for (int index = 0; index < probabilities.length; index++) {
            if (probabilities[index] < threshold) {
                probabilities[index] = 0.0f;
            }
        }
    }

    private void applyTopP(float[] probabilities, double topP) {
        if (topP <= 0.0d || topP >= 1.0d) {
            return;
        }

        Integer[] indices = new Integer[probabilities.length];
        for (int index = 0; index < indices.length; index++) {
            indices[index] = Integer.valueOf(index);
        }

        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                return Float.compare(probabilities[right.intValue()], probabilities[left.intValue()]);
            }
        });

        boolean[] keep = new boolean[probabilities.length];
        float cumulativeProbability = 0.0f;

        for (int sortedIndex = 0; sortedIndex < indices.length; sortedIndex++) {
            int tokenIndex = indices[sortedIndex].intValue();
            keep[tokenIndex] = true;
            cumulativeProbability += probabilities[tokenIndex];

            if (cumulativeProbability >= topP) {
                break;
            }
        }

        for (int index = 0; index < probabilities.length; index++) {
            if (!keep[index]) {
                probabilities[index] = 0.0f;
            }
        }
    }

    private int argMax(float[] values) {
        int bestIndex = 0;
        float bestValue = Float.NEGATIVE_INFINITY;

        for (int index = 0; index < values.length; index++) {
            if (values[index] > bestValue) {
                bestValue = values[index];
                bestIndex = index;
            }
        }

        return bestIndex;
    }

    private void closeInputs(Map<String, OnnxTensor> inputs) {
        for (OnnxTensor tensor : inputs.values()) {
            try {
                tensor.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private String buildPrompt(ChatHistory history, String userInput) {
        StringBuilder prompt = new StringBuilder();

        if (history != null) {
            String systemPrompt = history.getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                prompt.append("<|system|>\n")
                        .append(systemPrompt)
                        .append("<|end|>\n");
            }

            for (ChatHistory.Message message : history.getMessages()) {
                if ("user".equals(message.role)) {
                    prompt.append("<|user|>\n")
                            .append(message.content)
                            .append("<|end|>\n");
                } else if ("assistant".equals(message.role)) {
                    prompt.append("<|assistant|>\n")
                            .append(message.content)
                            .append("<|end|>\n");
                }
            }
        }

        prompt.append("<|user|>\n")
                .append(userInput)
                .append("<|end|>\n")
                .append("<|assistant|>\n");

        return prompt.toString();
    }

    private static ByteBuffer copyDirectBuffer(ByteBuffer source) {
        source.rewind();
        ByteBuffer copy = ByteBuffer.allocateDirect(source.remaining()).order(source.order());
        copy.put(source);
        copy.flip();
        return copy;
    }

    private static float fp16ToFloat(short fp16) {
        int half = fp16 & 0xFFFF;
        int sign = (half >>> 15) & 0x1;
        int exponent = (half >>> 10) & 0x1F;
        int mantissa = half & 0x03FF;

        if (exponent == 0) {
            if (mantissa == 0) {
                return sign == 1 ? -0.0f : 0.0f;
            }
            float value = (float) (Math.pow(2.0d, -14.0d) * (mantissa / 1024.0d));
            return sign == 1 ? -value : value;
        }

        if (exponent == 0x1F) {
            if (mantissa == 0) {
                return sign == 1 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            }
            return Float.NaN;
        }

        int floatBits = (sign << 31) | ((exponent - 15 + 127) << 23) | (mantissa << 13);
        return Float.intBitsToFloat(floatBits);
    }

    private String normalizeExecutionProvider(String provider) {
        return trimToEmpty(provider).toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : defaultValue;
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        try {
            return value != null ? Double.parseDouble(value.trim()) : defaultValue;
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private static final class KvCacheState {
        private final ByteBuffer[] keyBuffers;
        private final ByteBuffer[] valueBuffers;
        private final int sequenceLength;

        private KvCacheState(ByteBuffer[] keyBuffers, ByteBuffer[] valueBuffers, int sequenceLength) {
            this.keyBuffers = keyBuffers;
            this.valueBuffers = valueBuffers;
            this.sequenceLength = sequenceLength;
        }
    }
}
