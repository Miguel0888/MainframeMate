package de.bund.zrb.onnx;

import ai.onnxruntime.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrationstest: Phi-3 Mini ONNX-Modell laden und Inferenz ausführen.
 * <p>
 * Voraussetzung: Modell liegt unter {@code phi3-model-windows/model/}.
 * Das Verzeichnis wird über die System-Property {@code phi3.model.dir} gesetzt
 * (siehe build.gradle).
 * <p>
 * Start: {@code ./gradlew :phi3-model-windows:test -Xmx4g}
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phi3InferenceTest {

    private static final String MODEL_DIR_PROP = "phi3.model.dir";

    private static Path modelDir;
    private static OrtEnvironment env;
    private static OrtSession session;

    // Tokenizer-Daten (aus tokenizer.json geladen)
    private static Map<String, Integer> vocab;
    private static Map<Integer, String> reverseVocab;
    private static List<String[]> merges;
    private static Set<Integer> eosTokenIds;

    // Modell-Konfiguration
    private static int numLayers;
    private static int headSize;
    private static int numKvHeads;

    @BeforeAll
    static void setup() throws Exception {
        String dir = System.getProperty(MODEL_DIR_PROP);
        assertNotNull(dir, "System Property '" + MODEL_DIR_PROP + "' nicht gesetzt");
        modelDir = Paths.get(dir);
        assertTrue(Files.isDirectory(modelDir), "Modellverzeichnis existiert nicht: " + modelDir);

        Path modelFile = modelDir.resolve("model.onnx");
        assertTrue(Files.exists(modelFile), "model.onnx nicht gefunden in " + modelDir);

        // Konfiguration lesen
        loadConfig();

        // ONNX Environment + Session
        System.out.println("[Test] Lade ONNX-Modell: " + modelFile);
        long t0 = System.currentTimeMillis();
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        session = env.createSession(modelFile.toString(), opts);
        System.out.println("[Test] Modell geladen in " + (System.currentTimeMillis() - t0) + " ms");

        // Inputs/Outputs loggen
        System.out.println("[Test] Inputs:  " + session.getInputNames());
        System.out.println("[Test] Outputs: " + session.getOutputNames());

        // Tokenizer laden
        loadTokenizer();
    }

    @AfterAll
    static void teardown() {
        if (session != null) try { session.close(); } catch (Exception ignore) {}
    }

    // ==================== Tests ====================

    @Test
    @Order(1)
    void testModelLoadsAndHasExpectedIO() {
        assertNotNull(session);
        assertTrue(session.getInputNames().contains("input_ids"), "input_ids fehlt");
        System.out.println("[Test] ✓ Modell geladen, input_ids vorhanden");
    }

    @Test
    @Order(2)
    void testTokenizerRoundtrip() {
        String text = "Hello, world!";
        long[] ids = encode(text);
        assertTrue(ids.length > 0, "Tokenizer liefert leeres Array");
        String decoded = decode(ids);
        System.out.println("[Test] Tokenizer: '" + text + "' → " + Arrays.toString(ids) + " → '" + decoded + "'");
        // Roundtrip muss nicht perfekt sein (Whitespace), aber ähnlich
        assertTrue(decoded.contains("Hello"), "Decode enthält nicht 'Hello'");
    }

    @Test
    @Order(3)
    void testSingleForwardPass() throws Exception {
        String prompt = "<|user|>\nWhat is 2+2?<|end|>\n<|assistant|>\n";
        long[] inputIds = encode(prompt);
        System.out.println("[Test] Prompt tokenisiert: " + inputIds.length + " Tokens");

        // Erstinferenz (ohne KV-Cache)
        float[] logits = forwardPass(inputIds);
        assertNotNull(logits);
        assertTrue(logits.length > 0, "Logits leer");
        System.out.println("[Test] ✓ Forward Pass OK, Logits-Dim: " + logits.length);

        int nextToken = argmax(logits);
        String tokenText = decode(new long[]{nextToken});
        System.out.println("[Test] Nächster Token: ID=" + nextToken + " → '" + tokenText + "'");
    }

    @Test
    @Order(4)
    void testGeneration() throws Exception {
        String prompt = "<|user|>\nWhat is the capital of France?<|end|>\n<|assistant|>\n";
        long[] inputIds = encode(prompt);

        System.out.println("[Test] === Textgenerierung ===");
        System.out.print("[Test] Antwort: ");

        StringBuilder answer = new StringBuilder();
        List<Long> allIds = new ArrayList<>();
        for (long id : inputIds) allIds.add(id);

        int maxTokens = 50;
        long t0 = System.currentTimeMillis();

        for (int step = 0; step < maxTokens; step++) {
            long[] currentIds = new long[allIds.size()];
            for (int i = 0; i < allIds.size(); i++) currentIds[i] = allIds.get(i);

            float[] logits = forwardPass(currentIds);
            int nextToken = argmax(logits);

            if (eosTokenIds.contains(nextToken)) {
                System.out.println(" [EOS]");
                break;
            }

            allIds.add((long) nextToken);
            String text = decode(new long[]{nextToken});
            answer.append(text);
            System.out.print(text);
        }

        long dt = System.currentTimeMillis() - t0;
        System.out.println();
        System.out.println("[Test] Generiert in " + dt + " ms (" + (allIds.size() - inputIds.length)
                + " Tokens, " + (dt / Math.max(1, allIds.size() - inputIds.length)) + " ms/Token)");

        assertTrue(answer.length() > 0, "Keine Antwort generiert");
        System.out.println("[Test] ✓ Antwort: " + answer.toString().trim());
    }

    // ==================== Hilfsmethoden ====================

    /**
     * Führt einen Forward Pass ohne KV-Cache durch.
     * Gibt die Logits der letzten Position zurück.
     */
    private float[] forwardPass(long[] inputIds) throws OrtException {
        long[][] idsArr = {inputIds};
        long[][] maskArr = new long[1][inputIds.length];
        Arrays.fill(maskArr[0], 1L);

        // Position IDs: [0, 1, 2, ..., seq_len-1]
        long[][] posIds = new long[1][inputIds.length];
        for (int i = 0; i < inputIds.length; i++) posIds[0][i] = i;

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", OnnxTensor.createTensor(env, idsArr));
        inputs.put("attention_mask", OnnxTensor.createTensor(env, maskArr));
        inputs.put("position_ids", OnnxTensor.createTensor(env, posIds));

        // KV-Cache: leere FP16-Tensoren für den ersten Durchlauf
        // Shape: [batch=1, num_kv_heads, seq_len=0, head_size]
        long[] emptyKvShape = {1, numKvHeads, 0, headSize};
        ByteBuffer emptyFp16 = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
        for (int i = 0; i < numLayers; i++) {
            inputs.put("past_key_values." + i + ".key",
                    OnnxTensor.createTensor(env, emptyFp16, emptyKvShape, OnnxJavaType.FLOAT16));
            inputs.put("past_key_values." + i + ".value",
                    OnnxTensor.createTensor(env, emptyFp16, emptyKvShape, OnnxJavaType.FLOAT16));
        }

        OrtSession.Result result = session.run(inputs, Collections.singleton("logits"));

        // Logits: shape [1, seq_len, vocab_size] — als FP16!
        OnnxTensor logitsTensor = (OnnxTensor) result.get("logits").get();
        long[] logitsShape = logitsTensor.getInfo().getShape(); // [1, seq_len, vocab_size]
        int seqLen = (int) logitsShape[1];
        int vocabSize = (int) logitsShape[2];

        // FP16 ByteBuffer auslesen
        ByteBuffer fp16Buf = logitsTensor.getByteBuffer();
        fp16Buf.order(ByteOrder.LITTLE_ENDIAN);

        // Letzte Position: offset = (seqLen-1) * vocabSize * 2 Bytes
        int offset = (seqLen - 1) * vocabSize * 2;
        float[] lastLogits = new float[vocabSize];
        for (int i = 0; i < vocabSize; i++) {
            short fp16 = fp16Buf.getShort(offset + i * 2);
            lastLogits[i] = fp16ToFloat(fp16);
        }

        // Cleanup
        for (OnnxTensor t : inputs.values()) t.close();
        result.close();

        return lastLogits;
    }

    /** Greedy: Token mit höchstem Logit. */
    private static int argmax(float[] logits) {
        int best = 0;
        for (int i = 1; i < logits.length; i++) {
            if (logits[i] > logits[best]) best = i;
        }
        return best;
    }

    /**
     * Konvertiert IEEE 754 half-precision (FP16) zu float32.
     * Layout: 1 Vorzeichenbit | 5 Exponent (Bias 15) | 10 Mantisse
     */
    private static float fp16ToFloat(short fp16) {
        int h = fp16 & 0xFFFF;
        int sign = (h >>> 15) & 0x1;
        int exp  = (h >>> 10) & 0x1F;
        int mant = h & 0x03FF;

        if (exp == 0) {
            // Subnormal oder Null
            if (mant == 0) return sign == 1 ? -0.0f : 0.0f;
            // Subnormal → normalisieren
            float val = (float) (Math.pow(2, -14) * (mant / 1024.0));
            return sign == 1 ? -val : val;
        } else if (exp == 0x1F) {
            // Inf oder NaN
            if (mant == 0) return sign == 1 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            return Float.NaN;
        }

        // Normal: (-1)^sign * 2^(exp-15) * (1 + mant/1024)
        int fp32 = (sign << 31)
                | ((exp - 15 + 127) << 23)
                | (mant << 13);
        return Float.intBitsToFloat(fp32);
    }

    // ==================== Tokenizer ====================

    private static void loadTokenizer() throws Exception {
        Path tokenizerPath = modelDir.resolve("tokenizer.json");
        assertTrue(Files.exists(tokenizerPath), "tokenizer.json fehlt");

        String json = new String(Files.readAllBytes(tokenizerPath), Charset.forName("UTF-8"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        vocab = new LinkedHashMap<>();
        reverseVocab = new HashMap<>();
        merges = new ArrayList<>();

        JsonObject model = root.getAsJsonObject("model");
        if (model.has("vocab")) {
            for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("vocab").entrySet()) {
                int id = entry.getValue().getAsInt();
                vocab.put(entry.getKey(), id);
                reverseVocab.put(id, entry.getKey());
            }
        }
        if (model.has("merges")) {
            for (JsonElement elem : model.getAsJsonArray("merges")) {
                String[] parts = elem.getAsString().split(" ", 2);
                if (parts.length == 2) merges.add(parts);
            }
        }

        // Added tokens (Spezial-Tokens)
        if (root.has("added_tokens")) {
            for (JsonElement elem : root.getAsJsonArray("added_tokens")) {
                JsonObject t = elem.getAsJsonObject();
                String content = t.get("content").getAsString();
                int id = t.get("id").getAsInt();
                vocab.put(content, id);
                reverseVocab.put(id, content);
            }
        }

        System.out.println("[Test] Tokenizer geladen: " + vocab.size() + " Tokens, " + merges.size() + " Merges");
    }

    /** Einfaches BPE Encoding. */
    private static long[] encode(String text) {
        // Spezial-Tokens direkt matchen
        List<Long> result = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            // Versuche Spezial-Token-Match (längster zuerst)
            String bestSpecial = null;
            for (String token : vocab.keySet()) {
                if (token.startsWith("<") && token.endsWith(">") && text.startsWith(token, pos)) {
                    if (bestSpecial == null || token.length() > bestSpecial.length()) {
                        bestSpecial = token;
                    }
                }
            }
            if (bestSpecial != null) {
                result.add((long) vocab.get(bestSpecial).intValue());
                pos += bestSpecial.length();
                continue;
            }

            // Normales BPE: Zeichen bis zum nächsten Spezial-Token sammeln
            int end = pos + 1;
            while (end < text.length()) {
                boolean isSpecialStart = false;
                for (String token : vocab.keySet()) {
                    if (token.startsWith("<") && token.endsWith(">") && text.startsWith(token, end)) {
                        isSpecialStart = true;
                        break;
                    }
                }
                if (isSpecialStart) break;
                end++;
            }

            String segment = text.substring(pos, end);
            result.addAll(bpeEncode(segment));
            pos = end;
        }

        long[] ids = new long[result.size()];
        for (int i = 0; i < result.size(); i++) ids[i] = result.get(i);
        return ids;
    }

    /** BPE auf einen Text-Abschnitt (ohne Spezial-Tokens). */
    private static List<Long> bpeEncode(String text) {
        // SentencePiece/Llama-Style: '▁' (U+2581) steht für Leerzeichen am Wortanfang
        text = text.replace(' ', '\u2581');

        // In Zeichen aufteilen
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            symbols.add(String.valueOf(text.charAt(i)));
        }

        // BPE Merges anwenden
        boolean changed = true;
        while (changed && symbols.size() > 1) {
            changed = false;
            int bestRank = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < symbols.size() - 1; i++) {
                int rank = mergeRank(symbols.get(i), symbols.get(i + 1));
                if (rank >= 0 && rank < bestRank) {
                    bestRank = rank;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                symbols.set(bestIdx, symbols.get(bestIdx) + symbols.get(bestIdx + 1));
                symbols.remove(bestIdx + 1);
                changed = true;
            }
        }

        List<Long> ids = new ArrayList<>();
        for (String sym : symbols) {
            Integer id = vocab.get(sym);
            if (id != null) {
                ids.add((long) id.intValue());
            } else {
                // Fallback: Byte-level encoding
                for (char c : sym.toCharArray()) {
                    Integer byteId = vocab.get(String.valueOf(c));
                    ids.add(byteId != null ? (long) byteId.intValue() : 0L);
                }
            }
        }
        return ids;
    }

    private static int mergeRank(String left, String right) {
        for (int i = 0; i < merges.size(); i++) {
            if (merges.get(i)[0].equals(left) && merges.get(i)[1].equals(right)) return i;
        }
        return -1;
    }

    /** Einfaches Decoding: ID → Token → Text. */
    private static String decode(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            String token = reverseVocab.get((int) id);
            if (token != null) {
                // '▁' (U+2581) zurück zu Leerzeichen konvertieren
                sb.append(token.replace('\u2581', ' '));
            }
        }
        return sb.toString();
    }

    /** Konfiguration aus genai_config.json laden. */
    private static void loadConfig() throws Exception {
        Path configPath = modelDir.resolve("genai_config.json");
        assertTrue(Files.exists(configPath), "genai_config.json fehlt");

        String json = new String(Files.readAllBytes(configPath), Charset.forName("UTF-8"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject model = root.getAsJsonObject("model");
        JsonObject decoder = model.getAsJsonObject("decoder");

        numLayers = decoder.get("num_hidden_layers").getAsInt();
        headSize = decoder.get("head_size").getAsInt();
        numKvHeads = decoder.get("num_key_value_heads").getAsInt();

        eosTokenIds = new HashSet<>();
        JsonArray eosArr = model.getAsJsonArray("eos_token_id");
        for (JsonElement e : eosArr) eosTokenIds.add(e.getAsInt());

        System.out.println("[Test] Config: " + numLayers + " Layers, head_size=" + headSize
                + ", kv_heads=" + numKvHeads + ", EOS=" + eosTokenIds);
    }
}

