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
import java.util.function.Supplier;
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

    private final Supplier<Map<String, String>> configSupplier;
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
     * @param configSupplier liefert bei jedem Aufruf die aktuelle KI-Konfiguration
     *                       (normalerweise {@code () -> SettingsHelper.load().aiConfig})
     */
    public OnnxChatManager(Supplier<Map<String, String>> configSupplier) {
        this.configSupplier = configSupplier != null
                ? configSupplier
                : new Supplier<Map<String, String>>() {
                      @Override public Map<String, String> get() {
                          return Collections.emptyMap();
                      }
                  };
    }

    /** Returns the current config snapshot (re-read from settings on every call). */
    private Map<String, String> config() {
        Map<String, String> c = configSupplier.get();
        return c != null ? c : Collections.<String, String>emptyMap();
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

        Map<String, String> cfg = config();
        String modelPath = cfg.getOrDefault("onnx.model.path", "");
        int maxTokens = parseInt(cfg.getOrDefault("onnx.max.tokens", "256"), 256);
        double temperature = parseDouble(cfg.getOrDefault("onnx.temperature", "0.7"), 0.7);
        double topP = parseDouble(cfg.getOrDefault("onnx.top.p", "0.9"), 0.9);
        int topK = parseInt(cfg.getOrDefault("onnx.top.k", "40"), 40);

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
     * Versucht zuerst DirectML (GPU), fällt automatisch auf CPU zurück.
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

        // Immer DirectML zuerst versuchen, CPU als stiller Fallback
        boolean usingDirectMl = false;
        try {
            options.addDirectML(0);
            usingDirectMl = true;
            LOG.info("ONNX Execution Provider: DirectML (GPU)");
        } catch (Throwable t) {
            LOG.info("DirectML nicht verfügbar, verwende CPU: " + t.getMessage());
        }

        session = environment.createSession(modelFile.toString(), options);
        loadedModelPath = modelPath;

        long dt = System.currentTimeMillis() - t0;
        LOG.info("ONNX-Modell geladen in " + dt + " ms (" + (usingDirectMl ? "DirectML" : "CPU")
                + ") – Inputs: " + session.getInputNames()
                + ", Outputs: " + session.getOutputNames());

        // Tokenizer laden
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
     * Autoregressive Textgenerierung mit inkrementellem KV-Cache.
     * <p>
     * Phase 1 (Prefill): Gesamter Prompt wird verarbeitet, KV-Cache wird aufgebaut.
     * Phase 2 (Decode):  Pro Schritt wird nur 1 Token verarbeitet, KV-Cache wird weitergeführt.
     * <p>
     * Dies ist ~100× schneller als ohne KV-Cache, da der Prompt nicht bei jedem Token
     * neu verarbeitet werden muss.
     */
    private String generate(String prompt, int maxTokens, double temperature,
                            double topP, int topK,
                            AtomicBoolean cancelled, ChatStreamListener listener)
            throws OrtException {

        long[] promptIds = tokenizer.encode(prompt);

        StringBuilder output = new StringBuilder();
        Set<Integer> eosTokenIds = tokenizer.getEosTokenIds();
        int eosTokenId = tokenizer.getEosTokenId();
        int numLayers = tokenizer.getNumLayers();
        int headSize = tokenizer.getHeadSize();
        int numKvHeads = tokenizer.getNumKvHeads();

        boolean needsPositionIds = session.getInputNames().contains("position_ids");
        boolean hasKvCache = session.getInputNames().contains("past_key_values.0.key");

        LOG.info("Generate: prompt=" + promptIds.length + " tokens, maxTokens=" + maxTokens
                + ", kvCache=" + hasKvCache + ", positionIds=" + needsPositionIds);

        // Welche Outputs sollen angefordert werden?
        Set<String> requestedOutputs = new LinkedHashSet<>();
        requestedOutputs.add("logits");
        if (hasKvCache) {
            for (int i = 0; i < numLayers; i++) {
                requestedOutputs.add("present." + i + ".key");
                requestedOutputs.add("present." + i + ".value");
            }
        }

        // KV-Cache Buffers (null = noch nicht initialisiert)
        ByteBuffer[] kvKeys = null;
        ByteBuffer[] kvValues = null;
        int pastSeqLen = 0;
        int totalSeqLen = promptIds.length;
        long lastTokenId = 0;

        long t0 = System.currentTimeMillis();

        for (int step = 0; step < maxTokens; step++) {
            if (cancelled.get()) break;

            Map<String, OnnxTensor> inputs = new HashMap<>();

            // ── input_ids ──
            if (step == 0) {
                inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][]{promptIds}));
            } else {
                inputs.put("input_ids", OnnxTensor.createTensor(environment, new long[][]{{lastTokenId}}));
            }

            // ── attention_mask (gesamte Sequenzlänge) ──
            long[] mask = new long[totalSeqLen];
            Arrays.fill(mask, 1L);
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, new long[][]{mask}));

            // ── position_ids ──
            if (needsPositionIds) {
                if (step == 0) {
                    long[] posIds = new long[promptIds.length];
                    for (int i = 0; i < promptIds.length; i++) posIds[i] = i;
                    inputs.put("position_ids", OnnxTensor.createTensor(environment, new long[][]{posIds}));
                } else {
                    inputs.put("position_ids", OnnxTensor.createTensor(environment, new long[][]{{totalSeqLen - 1}}));
                }
            }

            // ── KV-Cache ──
            if (hasKvCache) {
                if (kvKeys == null) {
                    // Erster Schritt: leerer Past-Cache
                    long[] emptyShape = {1, numKvHeads, 0, headSize};
                    ByteBuffer emptyBuf = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
                    for (int i = 0; i < numLayers; i++) {
                        inputs.put("past_key_values." + i + ".key",
                                OnnxTensor.createTensor(environment, emptyBuf, emptyShape, OnnxJavaType.FLOAT16));
                        inputs.put("past_key_values." + i + ".value",
                                OnnxTensor.createTensor(environment, emptyBuf, emptyShape, OnnxJavaType.FLOAT16));
                    }
                } else {
                    // Folgeschritte: KV-Cache vom vorherigen Schritt
                    long[] pastShape = {1, numKvHeads, pastSeqLen, headSize};
                    for (int i = 0; i < numLayers; i++) {
                        kvKeys[i].rewind();
                        kvValues[i].rewind();
                        inputs.put("past_key_values." + i + ".key",
                                OnnxTensor.createTensor(environment, kvKeys[i], pastShape, OnnxJavaType.FLOAT16));
                        inputs.put("past_key_values." + i + ".value",
                                OnnxTensor.createTensor(environment, kvValues[i], pastShape, OnnxJavaType.FLOAT16));
                    }
                }
            }

            // ── Forward Pass ──
            long tStep = System.currentTimeMillis();
            OrtSession.Result result;
            try {
                result = session.run(inputs, requestedOutputs);
            } finally {
                for (OnnxTensor t : inputs.values()) t.close();
            }

            try {
                if (step == 0) {
                    LOG.info("Prefill: " + (System.currentTimeMillis() - tStep) + " ms ("
                            + promptIds.length + " tokens)");
                }

                // ── Logits extrahieren (letztes Token) ──
                float[] lastLogits = extractLastLogits(result);

                // ── KV-Cache für nächsten Schritt kopieren ──
                if (hasKvCache) {
                    if (kvKeys == null) {
                        kvKeys = new ByteBuffer[numLayers];
                        kvValues = new ByteBuffer[numLayers];
                    }
                    for (int i = 0; i < numLayers; i++) {
                        OnnxTensor keyOut = (OnnxTensor) result.get("present." + i + ".key").get();
                        OnnxTensor valOut = (OnnxTensor) result.get("present." + i + ".value").get();
                        kvKeys[i] = copyDirectBuffer(keyOut.getByteBuffer());
                        kvValues[i] = copyDirectBuffer(valOut.getByteBuffer());
                        if (i == 0) {
                            pastSeqLen = (int) keyOut.getInfo().getShape()[2];
                        }
                    }
                }

                // ── Token samplen ──
                int nextTokenId = sampleToken(lastLogits, temperature, topP, topK);

                if (eosTokenIds.contains(nextTokenId) || nextTokenId == eosTokenId) {
                    LOG.info("EOS nach " + (step + 1) + " Tokens, "
                            + (System.currentTimeMillis() - t0) + " ms");
                    break;
                }

                lastTokenId = nextTokenId;
                totalSeqLen++;

                String tokenText = tokenizer.decode(new long[]{nextTokenId});
                output.append(tokenText);
                listener.onStreamChunk(tokenText);

            } finally {
                result.close();
            }
        }

        long totalMs = System.currentTimeMillis() - t0;
        int generatedTokens = totalSeqLen - promptIds.length;
        if (generatedTokens > 0) {
            LOG.info("Generiert: " + generatedTokens + " Tokens in " + totalMs + " ms ("
                    + String.format("%.1f", generatedTokens * 1000.0 / totalMs) + " tok/s)");
        }

        return output.toString();
    }

    /**
     * Extrahiert die Logits des letzten Tokens aus dem Ergebnis.
     * Unterstützt sowohl FP32- als auch FP16-Modelle.
     */
    private float[] extractLastLogits(OrtSession.Result result) throws OrtException {
        OnnxTensor logitsTensor = (OnnxTensor) result.get("logits").get();
        long[] shape = logitsTensor.getInfo().getShape();
        int seqLen = (int) shape[1];
        int vocabSize = (int) shape[2];

        try {
            float[][][] logits = (float[][][]) logitsTensor.getValue();
            return logits[0][logits[0].length - 1];
        } catch (OrtException e) {
            // FP16-Modell: ByteBuffer auslesen und konvertieren
            ByteBuffer fp16Buf = logitsTensor.getByteBuffer();
            fp16Buf.order(ByteOrder.LITTLE_ENDIAN);
            int offset = (seqLen - 1) * vocabSize * 2;
            float[] lastLogits = new float[vocabSize];
            for (int i = 0; i < vocabSize; i++) {
                lastLogits[i] = fp16ToFloat(fp16Buf.getShort(offset + i * 2));
            }
            return lastLogits;
        }
    }

    /** Kopiert einen DirectByteBuffer (nötig da ONNX Runtime die Originaldaten beim Result.close() freigibt). */
    private static ByteBuffer copyDirectBuffer(ByteBuffer src) {
        src.rewind();
        ByteBuffer copy = ByteBuffer.allocateDirect(src.remaining()).order(src.order());
        copy.put(src);
        copy.flip();
        return copy;
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
            float[] sorted = probs.clone();
            Arrays.sort(sorted);
            float threshold = sorted[sorted.length - topK];
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] < threshold) probs[i] = 0;
            }
        }

        // Top-P (Nucleus): behalte Tokens bis kumulative Wahrscheinlichkeit >= topP
        if (topP > 0 && topP < 1.0) {
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
        if (total <= 0) return 0;
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

