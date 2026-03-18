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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * ChatManager-Implementierung für lokale LLM-Inferenz via ONNX Runtime.
 * <p>
 * Unterstützt Microsoft Phi-3/Phi-4 ONNX-Modelle. Die Inferenz läuft komplett lokal,
 * ohne Netzwerkzugriff. Der Generate-Loop (autoregressive Decodierung) ist in reinem
 * Java implementiert – kein ONNX Runtime GenAI nötig.
 * <p>
 * Einstellungen werden aus der übergebenen {@code aiConfig}-Map gelesen:
 * <ul>
 *   <li>{@code onnx.model.path} – Pfad zum ONNX-Modellverzeichnis</li>
 *   <li>{@code onnx.execution.provider} – "cpu" oder "directml"</li>
 *   <li>{@code onnx.max.tokens} – Maximale Ausgabelänge (default 256)</li>
 *   <li>{@code onnx.temperature} – Sampling-Temperatur (default 0.7)</li>
 *   <li>{@code onnx.top.p} – Top-P Nucleus Sampling (default 0.9)</li>
 *   <li>{@code onnx.top.k} – Top-K Sampling (default 40)</li>
 * </ul>
 */
public class OnnxChatManager implements ChatManager {

    private static final Logger LOG = Logger.getLogger(OnnxChatManager.class.getName());

    private final Map<String, String> aiConfig;
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Lazy-loaded ONNX session
    private volatile OrtEnvironment environment;
    private volatile OrtSession session;
    private volatile String loadedModelPath;

    // Simple BPE-like token mapping (will be populated from tokenizer.json if available)
    private volatile OnnxTokenizer tokenizer;

    /**
     * @param aiConfig die KI-Konfiguration (normalerweise {@code settings.aiConfig})
     */
    public OnnxChatManager(Map<String, String> aiConfig) {
        this.aiConfig = aiConfig != null ? aiConfig : Collections.<String, String>emptyMap();
    }

    // ==================== ChatManager Interface ====================

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
        if (history != null) history.clear();
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

        String modelPath = aiConfig.getOrDefault("onnx.model.path", "");
        int maxTokens = parseInt(aiConfig.getOrDefault("onnx.max.tokens", "256"), 256);
        double temperature = parseDouble(aiConfig.getOrDefault("onnx.temperature", "0.7"), 0.7);
        double topP = parseDouble(aiConfig.getOrDefault("onnx.top.p", "0.9"), 0.9);
        int topK = parseInt(aiConfig.getOrDefault("onnx.top.k", "40"), 40);

        if (modelPath.isEmpty()) {
            listener.onError(new IOException("ONNX Modellpfad nicht konfiguriert. Bitte in den Einstellungen angeben."));
            return false;
        }

        ChatHistory history = useContext
                ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new)
                : null;

        // Build prompt from history
        String fullPrompt = buildPrompt(history, userInput);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(sessionId, cancelled);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureModelLoaded(modelPath);
                    listener.onStreamStart();

                    if (history != null) history.addUserMessage(userInput);

                    String response = generate(fullPrompt, maxTokens, temperature, topP, topK, cancelled, listener);

                    if (history != null) history.addBotMessage(response);
                    listener.onStreamEnd();
                } catch (Exception e) {
                    LOG.warning("ONNX Inferenz-Fehler: " + e.getMessage());
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
        closeSession();
    }

    @Override
    public void closeSession(UUID sessionId) {
        sessionHistories.remove(sessionId);
        cancelFlags.remove(sessionId);
    }

    // ==================== ONNX Model Management ====================

    /**
     * Lädt das ONNX-Modell (lazy, nur wenn sich der Pfad geändert hat).
     */
    private synchronized void ensureModelLoaded(String modelPath) throws OrtException, IOException {
        if (session != null && modelPath.equals(loadedModelPath)) {
            return; // bereits geladen
        }

        closeSession();

        Path modelDir = Paths.get(modelPath);
        Path modelFile = resolveModelFile(modelDir);
        if (modelFile == null) {
            throw new IOException("Kein ONNX-Modell gefunden in: " + modelPath
                    + "\nErwartet: *.onnx Datei im Verzeichnis oder direkter Pfad zur .onnx Datei.");
        }

        LOG.info("Lade ONNX-Modell: " + modelFile);
        long t0 = System.currentTimeMillis();

        environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();

        // Execution Provider konfigurieren
        String ep = aiConfig.getOrDefault("onnx.execution.provider", "cpu").toLowerCase(Locale.ROOT);
        if ("directml".equals(ep)) {
            try {
                options.addDirectML(0);
                LOG.info("ONNX Execution Provider: DirectML (GPU)");
            } catch (Throwable t) {
                LOG.warning("DirectML nicht verfügbar, Fallback auf CPU: " + t.getMessage());
            }
        } else {
            LOG.info("ONNX Execution Provider: CPU");
        }

        session = environment.createSession(modelFile.toString(), options);
        loadedModelPath = modelPath;

        long dt = System.currentTimeMillis() - t0;
        LOG.info("ONNX-Modell geladen in " + dt + " ms – Inputs: " + session.getInputNames()
                + ", Outputs: " + session.getOutputNames());

        // Tokenizer laden (falls vorhanden)
        Path tokenizerPath = modelDir.resolve("tokenizer.json");
        if (!Files.exists(tokenizerPath)) {
            tokenizerPath = modelDir.resolve("tokenizer_config.json");
        }
        tokenizer = new OnnxTokenizer(modelDir);
    }

    /**
     * Findet die .onnx Datei im Modellverzeichnis.
     */
    private Path resolveModelFile(Path modelDir) {
        if (Files.isRegularFile(modelDir) && modelDir.toString().endsWith(".onnx")) {
            return modelDir;
        }
        if (Files.isDirectory(modelDir)) {
            // Priorisierung: model.onnx > *decoder*.onnx > erster *.onnx
            Path candidate = modelDir.resolve("model.onnx");
            if (Files.exists(candidate)) return candidate;

            try {
                // Suche nach decoder-Modell (typisch für Phi-3/4)
                java.io.File[] files = modelDir.toFile().listFiles();
                if (files != null) {
                    Path firstOnnx = null;
                    for (java.io.File f : files) {
                        if (f.getName().endsWith(".onnx")) {
                            if (f.getName().contains("decoder")) return f.toPath();
                            if (firstOnnx == null) firstOnnx = f.toPath();
                        }
                    }
                    return firstOnnx;
                }
            } catch (Exception e) {
                LOG.warning("Fehler beim Scannen des Modellverzeichnisses: " + e.getMessage());
            }
        }
        return null;
    }

    private void closeSession() {
        if (session != null) {
            try { session.close(); } catch (Throwable ignore) {}
            session = null;
        }
        // OrtEnvironment is a singleton, don't close it
        loadedModelPath = null;
    }

    // ==================== Autoregressive Generate Loop ====================

    /**
     * Autoregressive Textgenerierung: Tokenisiert den Prompt, führt die Forward-Passes
     * sequentiell durch und decoded die generierten Tokens Token für Token.
     * <p>
     * Unterstützt FP16-Modelle (DirectML INT4) und FP32-Modelle.
     * KV-Cache wird pro Durchlauf leer übergeben (kein inkrementeller Cache).
     */
    private String generate(String prompt, int maxTokens, double temperature,
                            double topP, int topK,
                            AtomicBoolean cancelled, ChatStreamListener listener)
            throws OrtException {

        long[] inputIds = tokenizer.encode(prompt);
        List<Long> generated = new ArrayList<>();
        for (long id : inputIds) generated.add(id);

        StringBuilder output = new StringBuilder();
        Set<Integer> eosTokenIds = tokenizer.getEosTokenIds();
        int eosTokenId = tokenizer.getEosTokenId();

        // Modell-Konfiguration für KV-Cache
        int numLayers = tokenizer.getNumLayers();
        int headSize = tokenizer.getHeadSize();
        int numKvHeads = tokenizer.getNumKvHeads();

        // Prüfe ob das Modell position_ids erwartet
        boolean needsPositionIds = session.getInputNames().contains("position_ids");
        // Prüfe ob das Modell KV-Cache Inputs hat
        boolean hasKvCache = session.getInputNames().contains("past_key_values.0.key");
        // Prüfe ob Logits FP16 sind (DirectML-Modelle)
        boolean fp16Logits = false;

        for (int step = 0; step < maxTokens; step++) {
            if (cancelled.get()) break;

            // Prepare input tensors
            long[] ids = new long[generated.size()];
            for (int i = 0; i < generated.size(); i++) ids[i] = generated.get(i);

            long[] attentionMask = new long[ids.length];
            Arrays.fill(attentionMask, 1L);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            long[][] idShape = {ids};
            long[][] maskShape = {attentionMask};
            inputs.put("input_ids", OnnxTensor.createTensor(environment, idShape));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, maskShape));

            // Position IDs
            if (needsPositionIds) {
                long[][] posIds = new long[1][ids.length];
                for (int i = 0; i < ids.length; i++) posIds[0][i] = i;
                inputs.put("position_ids", OnnxTensor.createTensor(environment, posIds));
            }

            // KV-Cache: leere FP16-Tensoren (kein inkrementeller Cache)
            if (hasKvCache) {
                long[] emptyKvShape = {1, numKvHeads, 0, headSize};
                ByteBuffer emptyFp16 = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
                for (int i = 0; i < numLayers; i++) {
                    inputs.put("past_key_values." + i + ".key",
                            OnnxTensor.createTensor(environment, emptyFp16, emptyKvShape, OnnxJavaType.FLOAT16));
                    inputs.put("past_key_values." + i + ".value",
                            OnnxTensor.createTensor(environment, emptyFp16, emptyKvShape, OnnxJavaType.FLOAT16));
                }
            }

            // Forward pass — nur Logits anfordern
            OrtSession.Result result = session.run(inputs, Collections.singleton("logits"));

            // Logits auslesen (FP16 oder FP32)
            float[] lastLogits;
            OnnxTensor logitsTensor = (OnnxTensor) result.get("logits").get();
            long[] logitsShape = logitsTensor.getInfo().getShape();
            int seqLen = (int) logitsShape[1];
            int vocabSize = (int) logitsShape[2];

            try {
                // Versuche FP32 zuerst
                float[][][] logits = (float[][][]) logitsTensor.getValue();
                lastLogits = logits[0][logits[0].length - 1];
            } catch (OrtException e) {
                // FP16-Modell: ByteBuffer auslesen und konvertieren
                fp16Logits = true;
                ByteBuffer fp16Buf = logitsTensor.getByteBuffer();
                fp16Buf.order(ByteOrder.LITTLE_ENDIAN);
                int offset = (seqLen - 1) * vocabSize * 2;
                lastLogits = new float[vocabSize];
                for (int i = 0; i < vocabSize; i++) {
                    short fp16 = fp16Buf.getShort(offset + i * 2);
                    lastLogits[i] = fp16ToFloat(fp16);
                }
            }

            // Close tensors
            for (OnnxTensor t : inputs.values()) t.close();
            result.close();

            // Sample next token
            int nextTokenId = sampleToken(lastLogits, temperature, topP, topK);

            // EOS Check (multiple EOS tokens für Phi-3)
            if (eosTokenIds.contains(nextTokenId) || nextTokenId == eosTokenId) break;

            generated.add((long) nextTokenId);

            // Decode and stream
            String tokenText = tokenizer.decode(new long[]{nextTokenId});
            output.append(tokenText);
            listener.onStreamChunk(tokenText);
        }

        return output.toString();
    }

    /**
     * Konvertiert IEEE 754 half-precision (FP16) zu float32.
     */
    private static float fp16ToFloat(short fp16) {
        int h = fp16 & 0xFFFF;
        int sign = (h >>> 15) & 0x1;
        int exp  = (h >>> 10) & 0x1F;
        int mant = h & 0x03FF;

        if (exp == 0) {
            if (mant == 0) return sign == 1 ? -0.0f : 0.0f;
            float val = (float) (Math.pow(2, -14) * (mant / 1024.0));
            return sign == 1 ? -val : val;
        } else if (exp == 0x1F) {
            if (mant == 0) return sign == 1 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            return Float.NaN;
        }

        int fp32 = (sign << 31) | ((exp - 15 + 127) << 23) | (mant << 13);
        return Float.intBitsToFloat(fp32);
    }

    /**
     * Token-Sampling mit Temperature, Top-K und Top-P (Nucleus Sampling).
     */
    private int sampleToken(float[] logits, double temperature, double topP, int topK) {
        // Apply temperature
        if (temperature > 0 && temperature != 1.0) {
            for (int i = 0; i < logits.length; i++) {
                logits[i] /= (float) temperature;
            }
        }

        // Softmax
        float maxLogit = Float.NEGATIVE_INFINITY;
        for (float l : logits) if (l > maxLogit) maxLogit = l;
        float sumExp = 0;
        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (float) Math.exp(logits[i] - maxLogit);
            sumExp += probs[i];
        }
        for (int i = 0; i < probs.length; i++) probs[i] /= sumExp;

        // Top-K: behalte nur die K wahrscheinlichsten Tokens
        if (topK > 0 && topK < probs.length) {
            // Find the top-K threshold
            float[] sorted = probs.clone();
            Arrays.sort(sorted);
            float threshold = sorted[sorted.length - topK];
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] < threshold) probs[i] = 0;
            }
        }

        // Top-P (Nucleus): behalte Tokens bis kumulative Wahrscheinlichkeit >= topP
        if (topP > 0 && topP < 1.0) {
            // Sort indices by probability descending
            Integer[] indices = new Integer[probs.length];
            for (int i = 0; i < indices.length; i++) indices[i] = i;
            Arrays.sort(indices, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Float.compare(probs[b], probs[a]);
                }
            });

            float cumulative = 0;
            boolean[] keep = new boolean[probs.length];
            for (Integer idx : indices) {
                keep[idx] = true;
                cumulative += probs[idx];
                if (cumulative >= topP) break;
            }
            for (int i = 0; i < probs.length; i++) {
                if (!keep[i]) probs[i] = 0;
            }
        }

        // Renormalize
        float total = 0;
        for (float p : probs) total += p;
        if (total <= 0) return 0; // Fallback
        for (int i = 0; i < probs.length; i++) probs[i] /= total;

        // Sample
        float r = (float) Math.random();
        float acc = 0;
        for (int i = 0; i < probs.length; i++) {
            acc += probs[i];
            if (acc >= r) return i;
        }
        return probs.length - 1;
    }

    // ==================== Helpers ====================

    private String buildPrompt(ChatHistory history, String userInput) {
        StringBuilder sb = new StringBuilder();

        // Phi-3 Prompt-Format: <|system|>\n...<|end|>\n<|user|>\n...<|end|>\n<|assistant|>\n
        if (history != null) {
            String systemPrompt = history.getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                sb.append("<|system|>\n").append(systemPrompt).append("<|end|>\n");
            }
            for (ChatHistory.Message msg : history.getMessages()) {
                if ("user".equals(msg.role)) {
                    sb.append("<|user|>\n").append(msg.content).append("<|end|>\n");
                } else if ("assistant".equals(msg.role)) {
                    sb.append("<|assistant|>\n").append(msg.content).append("<|end|>\n");
                }
            }
        }

        sb.append("<|user|>\n").append(userInput).append("<|end|>\n");
        sb.append("<|assistant|>\n");
        return sb.toString();
    }

    private static int parseInt(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return defaultVal; }
    }

    private static double parseDouble(String s, double defaultVal) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return defaultVal; }
    }
}

