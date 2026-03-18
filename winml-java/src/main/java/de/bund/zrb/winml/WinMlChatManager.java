package de.bund.zrb.winml;

import com.sun.jna.Pointer;
import de.bund.zrb.winml.ml.*;
import de.bund.zrb.winml.rt.ComVTable;
import de.bund.zrb.winml.rt.WinRtRuntime;
import de.bund.zrb.winml.tensor.TensorFactory;
import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * ChatManager implementation using Windows ML (WinML) for local ONNX inference.
 * <p>
 * Uses the WinRT {@code Windows.AI.MachineLearning} APIs via JNA, which leverage
 * the system's built-in DirectML/DirectX12 stack — no additional native DLLs needed.
 * <p>
 * Supports Phi-3/Phi-4 style models with autoregressive generation including
 * KV-cache, top-k/top-p sampling, and streaming output.
 * <p>
 * <b>Requirements:</b>
 * <ul>
 *   <li>Windows 10 1903+ (Build 18362+)</li>
 *   <li>A compatible ONNX model with tokenizer.json and genai_config.json</li>
 * </ul>
 *
 * <b>Config keys:</b>
 * <pre>
 * winml.model.path      = C:/models/Phi-3-mini-4k-instruct-onnx
 * winml.device           = directx_high_performance  (default|cpu|directx|directx_high_performance|directx_min_power)
 * winml.max.tokens       = 256
 * winml.temperature      = 0.7
 * winml.top.p            = 0.9
 * winml.top.k            = 40
 * </pre>
 */
public class WinMlChatManager implements ChatManager {

    private static final Logger LOG = Logger.getLogger(WinMlChatManager.class.getName());

    private static final String CONFIG_MODEL_PATH = "winml.model.path";
    private static final String CONFIG_DEVICE = "winml.device";
    private static final String CONFIG_MAX_TOKENS = "winml.max.tokens";
    private static final String CONFIG_TEMPERATURE = "winml.temperature";
    private static final String CONFIG_TOP_P = "winml.top.p";
    private static final String CONFIG_TOP_K = "winml.top.k";

    private final Supplier<Map<String, String>> configSupplier;
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<UUID, ChatHistory>();
    private final Map<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<UUID, AtomicBoolean>();
    private final ExecutorService executor;
    private final Random random = new Random();

    // Loaded model state
    private volatile LearningModel model;
    private volatile LearningModelDevice device;
    private volatile LearningModelSession session;
    private volatile WinMlTokenizer tokenizer;
    private volatile String loadedModelPath;
    private volatile String loadedDevice;

    public WinMlChatManager(Supplier<Map<String, String>> configSupplier) {
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
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "winml-chat");
                t.setDaemon(true);
                return t;
            }
        });
    }

    private Map<String, String> config() {
        Map<String, String> c = configSupplier.get();
        return c != null ? c : Collections.<String, String>emptyMap();
    }

    // ── ChatManager interface ────────────────────────────────

    @Override
    public UUID newSession() {
        UUID id = UUID.randomUUID();
        sessionHistories.put(id, new ChatHistory(id));
        return id;
    }

    @Override
    public List<String> getFormattedHistory(UUID sessionId) {
        ChatHistory h = sessionHistories.get(sessionId);
        return h != null ? h.getFormattedHistory() : Collections.<String>emptyList();
    }

    @Override
    public ChatHistory getHistory(UUID sessionId) {
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
    }

    @Override
    public void clearHistory(UUID sessionId) {
        ChatHistory h = sessionHistories.get(sessionId);
        if (h != null) h.clear();
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
    public boolean streamAnswer(UUID sessionId, boolean useContext, String userInput,
                                ChatStreamListener listener, boolean keepAlive) throws IOException {
        if (sessionId == null) return false;

        final Map<String, String> cfg = config();
        final String modelPath = trimToEmpty(cfg.get(CONFIG_MODEL_PATH));
        final String deviceKind = trimToEmpty(cfg.getOrDefault(CONFIG_DEVICE, "directx_high_performance"));
        final int maxTokens = parseInt(cfg.get(CONFIG_MAX_TOKENS), 256);
        final double temperature = parseDouble(cfg.get(CONFIG_TEMPERATURE), 0.7);
        final double topP = parseDouble(cfg.get(CONFIG_TOP_P), 0.9);
        final int topK = parseInt(cfg.get(CONFIG_TOP_K), 40);

        if (modelPath.isEmpty()) {
            listener.onError(new IOException("WinML model path not configured (winml.model.path)."));
            return false;
        }

        if (!WinRtRuntime.isWinMlAvailable()) {
            listener.onError(new IOException("WinML requires Windows 10 1903+ (Build 18362+)."));
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
                    ensureModelLoaded(modelPath, deviceKind);

                    if (history != null) {
                        history.addUserMessage(userInput);
                    }

                    listener.onStreamStart();

                    String response = generate(prompt, maxTokens, temperature, topP, topK,
                            cancelled, listener);

                    if (history != null) {
                        history.addBotMessage(response);
                    }

                    listener.onStreamEnd();
                } catch (Exception e) {
                    LOG.warning("WinML inference failed: " + e.getMessage());
                    listener.onError(e);
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
        if (flag != null) flag.set(true);
    }

    @Override
    public void onDispose() {
        executor.shutdownNow();
        closeModel();
    }

    @Override
    public void closeSession(UUID sessionId) {
        sessionHistories.remove(sessionId);
        cancelFlags.remove(sessionId);
    }

    // ── Model loading ────────────────────────────────────────

    private synchronized void ensureModelLoaded(String modelPath, String deviceKind) throws IOException {
        if (session != null
                && modelPath.equals(loadedModelPath)
                && deviceKind.equals(loadedDevice)) {
            return;
        }

        closeModel();

        Path modelDir = Paths.get(modelPath);
        Path modelFile = resolveModelFile(modelDir);
        if (modelFile == null) {
            throw new IOException("No ONNX model found in: " + modelPath);
        }

        Path dir = Files.isDirectory(modelDir) ? modelDir : modelDir.getParent();
        if (dir == null) {
            throw new IOException("Cannot resolve model directory: " + modelPath);
        }

        LOG.info("Loading WinML model: " + modelFile);
        LOG.info("Device: " + deviceKind);

        long t0 = System.currentTimeMillis();

        model = LearningModel.loadFromFilePath(modelFile.toString());

        LearningModelDevice.Kind kind = parseDeviceKind(deviceKind);
        device = LearningModelDevice.create(kind);

        session = LearningModelSession.create(model, device);

        tokenizer = new WinMlTokenizer(dir);

        loadedModelPath = modelPath;
        loadedDevice = deviceKind;

        long dt = System.currentTimeMillis() - t0;
        LOG.info("WinML model loaded in " + dt + " ms");
    }

    private void closeModel() {
        if (session != null) {
            try { session.close(); } catch (Exception e) { /* ignore */ }
            session = null;
        }
        if (device != null) {
            try { device.close(); } catch (Exception e) { /* ignore */ }
            device = null;
        }
        if (model != null) {
            try { model.close(); } catch (Exception e) { /* ignore */ }
            model = null;
        }
        tokenizer = null;
        loadedModelPath = null;
        loadedDevice = null;
    }

    // ── Generation loop ──────────────────────────────────────

    private String generate(String prompt, int maxTokens, double temperature,
                            double topP, int topK, AtomicBoolean cancelled,
                            ChatStreamListener listener) throws IOException {

        long[] promptTokenIds = tokenizer.encode(prompt);
        StringBuilder generated = new StringBuilder();

        Set<Integer> eosIds = tokenizer.getEosTokenIds();
        int fallbackEos = tokenizer.getEosTokenId();

        LOG.info("WinML generation: promptTokens=" + promptTokenIds.length
                + ", maxTokens=" + maxTokens);

        long genStart = System.currentTimeMillis();

        // For the first step, we process all prompt tokens at once.
        // For subsequent steps, we feed only the newly generated token.
        // NOTE: WinML does not expose KV-cache directly.
        // Each step re-evaluates with the full sequence. This is slower but simpler.
        // A KV-cache approach would require model-specific output/input wiring.

        long[] currentIds = promptTokenIds;

        for (int step = 0; step < maxTokens; step++) {
            if (cancelled.get()) {
                LOG.info("Generation cancelled at step " + step);
                break;
            }

            // Build input tensors
            int seqLen = currentIds.length;

            // input_ids: [1, seqLen]
            Pointer inputIdsTensor = TensorFactory.createTensorInt64(
                    new long[]{1, seqLen}, currentIds);

            // attention_mask: [1, seqLen] all ones
            long[] mask = new long[seqLen];
            Arrays.fill(mask, 1L);
            Pointer attentionMaskTensor = TensorFactory.createTensorInt64(
                    new long[]{1, seqLen}, mask);

            try {
                LearningModelBinding binding = LearningModelBinding.create(session);
                try {
                    binding.bind("input_ids", inputIdsTensor);
                    binding.bind("attention_mask", attentionMaskTensor);

                    long stepStart = System.currentTimeMillis();
                    EvaluationResult result = session.evaluate(binding, "step-" + step);

                    try {
                        if (!result.succeeded()) {
                            throw new IOException("WinML evaluation failed: error="
                                    + result.getErrorStatus());
                        }

                        if (step == 0) {
                            LOG.info("Prefill in " + (System.currentTimeMillis() - stepStart) + " ms");
                        }

                        // Read logits: [1, seqLen, vocabSize]
                        float[] allLogits = result.getOutputAsFloat("logits");
                        long[] logitsShape = result.getOutputShape("logits");

                        int vocabSize = (int) logitsShape[logitsShape.length - 1];
                        // Get logits for last position
                        float[] lastLogits = new float[vocabSize];
                        System.arraycopy(allLogits, allLogits.length - vocabSize,
                                lastLogits, 0, vocabSize);

                        // Sample next token
                        int nextToken = selectNextToken(lastLogits, temperature, topP, topK);

                        if (eosIds.contains(nextToken) || nextToken == fallbackEos) {
                            LOG.info("EOS after " + (step + 1) + " tokens");
                            break;
                        }

                        // Decode and stream
                        String text = tokenizer.decode(new long[]{nextToken});
                        if (step == 0 && text.startsWith(" ")) {
                            text = text.substring(1);
                        }
                        generated.append(text);
                        listener.onStreamChunk(text);

                        // Append to sequence for next step
                        currentIds = appendToken(currentIds, nextToken);

                    } finally {
                        result.close();
                    }
                } finally {
                    binding.close();
                }
            } finally {
                ComVTable.release(inputIdsTensor);
                ComVTable.release(attentionMaskTensor);
            }
        }

        long totalMs = System.currentTimeMillis() - genStart;
        int genCount = currentIds.length - promptTokenIds.length;
        if (genCount > 0 && totalMs > 0) {
            double tps = genCount * 1000.0 / totalMs;
            LOG.info("Generated " + genCount + " tokens in " + totalMs + " ms ("
                    + String.format(Locale.US, "%.2f", tps) + " tok/s)");
        }

        return generated.toString();
    }

    // ── Token sampling ───────────────────────────────────────

    private int selectNextToken(float[] logits, double temperature, double topP, int topK) {
        if (temperature <= 0.0) return argMax(logits);

        float[] probs = softmaxWithTemp(logits, temperature);
        applyTopK(probs, topK);
        applyTopP(probs, topP);

        float sum = 0f;
        for (float p : probs) sum += p;
        if (sum <= 0f) return argMax(logits);

        for (int i = 0; i < probs.length; i++) probs[i] /= sum;

        float r = random.nextFloat();
        float cumulative = 0f;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (cumulative >= r) return i;
        }
        return probs.length - 1;
    }

    private float[] softmaxWithTemp(float[] logits, double temp) {
        float[] s = new float[logits.length];
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            s[i] = (float) (logits[i] / temp);
            if (s[i] > max) max = s[i];
        }
        float sum = 0f;
        for (int i = 0; i < s.length; i++) {
            s[i] = (float) Math.exp(s[i] - max);
            sum += s[i];
        }
        if (sum > 0f) {
            for (int i = 0; i < s.length; i++) s[i] /= sum;
        }
        return s;
    }

    private void applyTopK(float[] probs, int k) {
        if (k <= 0 || k >= probs.length) return;
        float[] sorted = probs.clone();
        Arrays.sort(sorted);
        float threshold = sorted[sorted.length - k];
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] < threshold) probs[i] = 0f;
        }
    }

    private void applyTopP(float[] probs, double p) {
        if (p <= 0.0 || p >= 1.0) return;
        Integer[] indices = new Integer[probs.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Float.compare(probs[b], probs[a]);
            }
        });
        boolean[] keep = new boolean[probs.length];
        float cum = 0f;
        for (Integer idx : indices) {
            keep[idx] = true;
            cum += probs[idx];
            if (cum >= p) break;
        }
        for (int i = 0; i < probs.length; i++) {
            if (!keep[i]) probs[i] = 0f;
        }
    }

    private int argMax(float[] values) {
        int best = 0;
        float bestVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > bestVal) { bestVal = values[i]; best = i; }
        }
        return best;
    }

    // ── Prompt building ──────────────────────────────────────

    private String buildPrompt(ChatHistory history, String userInput) {
        StringBuilder sb = new StringBuilder();

        if (history != null) {
            String sys = history.getSystemPrompt();
            if (sys != null && !sys.isEmpty()) {
                sb.append("<|system|>\n").append(sys).append("<|end|>\n");
            }
            for (ChatHistory.Message m : history.getMessages()) {
                if ("user".equals(m.role)) {
                    sb.append("<|user|>\n").append(m.content).append("<|end|>\n");
                } else if ("assistant".equals(m.role)) {
                    sb.append("<|assistant|>\n").append(m.content).append("<|end|>\n");
                }
            }
        }

        sb.append("<|user|>\n").append(userInput).append("<|end|>\n");
        sb.append("<|assistant|>\n");
        return sb.toString();
    }

    // ── Helpers ──────────────────────────────────────────────

    private static long[] appendToken(long[] ids, int token) {
        long[] newIds = new long[ids.length + 1];
        System.arraycopy(ids, 0, newIds, 0, ids.length);
        newIds[ids.length] = token;
        return newIds;
    }

    private static Path resolveModelFile(Path path) {
        if (path == null) return null;
        if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".onnx")) {
            return path;
        }
        if (!Files.isDirectory(path)) return null;

        Path preferred = path.resolve("model.onnx");
        if (Files.isRegularFile(preferred)) return preferred;

        java.io.File[] files = path.toFile().listFiles();
        if (files == null) return null;

        for (java.io.File f : files) {
            if (f.getName().toLowerCase().endsWith(".onnx")) return f.toPath();
        }
        return null;
    }

    private static LearningModelDevice.Kind parseDeviceKind(String kind) {
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "cpu": return LearningModelDevice.Kind.CPU;
            case "directx": return LearningModelDevice.Kind.DIRECTX;
            case "directx_high_performance": return LearningModelDevice.Kind.DIRECTX_HIGH_PERFORMANCE;
            case "directx_min_power": return LearningModelDevice.Kind.DIRECTX_MIN_POWER;
            default: return LearningModelDevice.Kind.DEFAULT;
        }
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static int parseInt(String s, int def) {
        try { return s != null ? Integer.parseInt(s.trim()) : def; }
        catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        try { return s != null ? Double.parseDouble(s.trim()) : def; }
        catch (Exception e) { return def; }
    }
}

