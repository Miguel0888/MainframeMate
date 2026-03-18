package de.bund.zrb.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Minimaler Tokenizer für ONNX-LLM-Modelle (Phi-3/Phi-4 etc.).
 * <p>
 * Liest {@code tokenizer.json} aus dem Modellverzeichnis (Hugging Face Format)
 * und implementiert BPE-basiertes Encoding/Decoding.
 * <p>
 * Decoder-Pipeline laut tokenizer.json:
 * <ol>
 *   <li>Replace: {@code ▁} → Leerzeichen</li>
 *   <li>ByteFallback: {@code <0xNN>} → Byte NN</li>
 *   <li>Fuse: Byte-Sequenzen als UTF-8 zusammenführen</li>
 *   <li>Strip: 1 führendes Leerzeichen entfernen</li>
 * </ol>
 * <p>
 * Normalizer-Pipeline (Encode):
 * <ol>
 *   <li>Prepend: {@code ▁} vor den gesamten Text stellen</li>
 *   <li>Replace: Leerzeichen → {@code ▁}</li>
 * </ol>
 */
final class OnnxTokenizer {

    private static final Logger LOG = Logger.getLogger(OnnxTokenizer.class.getName());

    /** SentencePiece space marker (U+2581 LOWER ONE EIGHTH BLOCK). */
    private static final String SPIECE_SPACE = "\u2581";

    // Token → ID
    private final Map<String, Integer> vocab = new LinkedHashMap<String, Integer>();
    // ID → Token
    private final Map<Integer, String> reverseVocab = new HashMap<Integer, String>();

    // Spezial-Tokens
    private int eosTokenId = 2;
    private int bosTokenId = 1;
    private int padTokenId = 0;
    private final Set<Integer> eosTokenIds = new HashSet<Integer>();

    // BPE Merge-Regeln (Paare in Prioritätsreihenfolge)
    private final List<String[]> merges = new ArrayList<String[]>();
    // Merge-Index für schnellen Lookup
    private final Map<String, Integer> mergeRanks = new HashMap<String, Integer>();

    // Spezial-Token-Liste (sortiert nach Länge absteigend für greedy matching)
    private final List<String> specialTokens = new ArrayList<String>();

    // Leerzeichen-Prefix-Zeichen: '▁' (U+2581, SentencePiece/LLaMA) oder 'Ġ' (U+0120, GPT2)
    private String spacePrefix = SPIECE_SPACE;

    // Modell-Konfiguration (aus genai_config.json)
    private int numLayers = 32;
    private int headSize = 96;
    private int numKvHeads = 32;

    private boolean loaded = false;

    OnnxTokenizer(Path modelDir) {
        Path tokenizerJson = modelDir.resolve("tokenizer.json");
        Path tokenizerConfig = modelDir.resolve("tokenizer_config.json");
        Path genaiConfig = modelDir.resolve("genai_config.json");

        if (Files.exists(tokenizerJson)) {
            try {
                loadTokenizerJson(tokenizerJson);
                loaded = true;
                LOG.info("Tokenizer geladen: " + vocab.size() + " Tokens, "
                        + merges.size() + " Merges, spacePrefix="
                        + (SPIECE_SPACE.equals(spacePrefix) ? "U+2581" : "U+0120")
                        + ", EOS=" + eosTokenIds);
            } catch (Exception e) {
                LOG.warning("Tokenizer konnte nicht geladen werden: " + e.getMessage());
            }
        }

        if (!loaded && Files.exists(tokenizerConfig)) {
            try {
                loadTokenizerConfig(tokenizerConfig);
            } catch (Exception e) {
                LOG.warning("tokenizer_config.json konnte nicht gelesen werden: " + e.getMessage());
            }
        }

        if (Files.exists(genaiConfig)) {
            try {
                loadGenaiConfig(genaiConfig);
            } catch (Exception e) {
                LOG.warning("genai_config.json konnte nicht gelesen werden: " + e.getMessage());
            }
        }

        if (!loaded) {
            LOG.warning("Kein tokenizer.json gefunden in " + modelDir
                    + " – Fallback auf einfache Whitespace-Tokenisierung.");
        }
    }

    int getEosTokenId() { return eosTokenId; }
    int getBosTokenId() { return bosTokenId; }
    Set<Integer> getEosTokenIds() { return Collections.unmodifiableSet(eosTokenIds); }
    int getNumLayers() { return numLayers; }
    int getHeadSize() { return headSize; }
    int getNumKvHeads() { return numKvHeads; }

    // ==================== Encode ====================

    /**
     * Encodiert einen String in Token-IDs.
     * Erkennt Spezial-Tokens (z.B. {@code <|user|>}) und encodiert sie direkt.
     * Wendet den Normalizer an (Prepend ▁ + Replace ' ' → ▁) auf jedes Text-Segment.
     */
    long[] encode(String text) {
        if (!loaded) {
            return encodeFallback(text);
        }

        List<Long> result = new ArrayList<Long>();
        int pos = 0;
        while (pos < text.length()) {
            // Spezial-Token greedy matchen (längster zuerst)
            String matchedSpecial = null;
            for (String special : specialTokens) {
                if (text.startsWith(special, pos)) {
                    matchedSpecial = special;
                    break;
                }
            }
            if (matchedSpecial != null) {
                Integer id = vocab.get(matchedSpecial);
                if (id != null) result.add((long) id.intValue());
                pos += matchedSpecial.length();
                continue;
            }

            // Normaler Text bis zum nächsten Spezial-Token oder Ende
            int end = pos + 1;
            while (end < text.length()) {
                boolean isSpecialStart = false;
                for (String special : specialTokens) {
                    if (text.startsWith(special, end)) {
                        isSpecialStart = true;
                        break;
                    }
                }
                if (isSpecialStart) break;
                end++;
            }

            String segment = text.substring(pos, end);

            // Normalizer: 1) Prepend ▁   2) Replace ' ' → ▁
            String normalized = spacePrefix + segment.replace(" ", spacePrefix);

            List<String> tokens = bpeEncode(normalized);
            for (String tok : tokens) {
                Integer id = vocab.get(tok);
                if (id != null) {
                    result.add((long) id.intValue());
                } else {
                    LOG.fine("Unknown BPE token (skipped): " + tok);
                }
            }
            pos = end;
        }

        long[] ids = new long[result.size()];
        for (int i = 0; i < result.size(); i++) ids[i] = result.get(i);
        return ids;
    }

    // ==================== Decode ====================

    /**
     * Decodiert Token-IDs zurück in Text.
     * <p>
     * Decoder-Pipeline:
     * <ol>
     *   <li>Token-Strings aus reverseVocab holen</li>
     *   <li>{@code ▁} durch Leerzeichen ersetzen</li>
     *   <li>{@code <0xNN>} Byte-Fallback-Tokens in Bytes umwandeln</li>
     *   <li>Byte-Sequenzen als UTF-8 zusammenführen</li>
     * </ol>
     */
    String decode(long[] ids) {
        if (!loaded) {
            return decodeFallback(ids);
        }

        // Phase 1: Token-Strings sammeln, ▁ → Space, Byte-Fallback erkennen
        List<Object> parts = new ArrayList<Object>(); // String oder byte[]
        for (long id : ids) {
            String token = reverseVocab.get((int) id);
            if (token == null) {
                continue;
            }

            // Byte-Fallback: <0xNN> → einzelnes Byte
            if (token.length() == 6 && token.startsWith("<0x") && token.endsWith(">")) {
                try {
                    int byteVal = Integer.parseInt(token.substring(3, 5), 16);
                    parts.add(new byte[] { (byte) byteVal });
                    continue;
                } catch (NumberFormatException ignore) {
                    // Kein gültiges Byte-Token → als normaler String behandeln
                }
            }

            // ▁ → Leerzeichen
            String decoded = token.replace(spacePrefix, " ");
            parts.add(decoded);
        }

        // Phase 2: Fuse – aufeinanderfolgende Bytes zusammenfügen und als UTF-8 decodieren
        StringBuilder sb = new StringBuilder();
        List<Byte> byteBuffer = new ArrayList<Byte>();

        for (Object part : parts) {
            if (part instanceof byte[]) {
                byte[] b = (byte[]) part;
                for (byte val : b) {
                    byteBuffer.add(val);
                }
            } else {
                // Byte-Buffer flush
                if (!byteBuffer.isEmpty()) {
                    sb.append(decodeUtf8Bytes(byteBuffer));
                    byteBuffer.clear();
                }
                sb.append((String) part);
            }
        }
        // Remaining bytes flush
        if (!byteBuffer.isEmpty()) {
            sb.append(decodeUtf8Bytes(byteBuffer));
        }

        return sb.toString();
    }

    /** Converts a list of bytes to a UTF-8 string. */
    private static String decodeUtf8Bytes(List<Byte> bytes) {
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            arr[i] = bytes.get(i);
        }
        return new String(arr, Charset.forName("UTF-8"));
    }

    // ==================== BPE Encoding ====================

    private List<String> bpeEncode(String text) {
        // In Zeichen aufteilen
        List<String> symbols = new ArrayList<String>();
        for (int i = 0; i < text.length(); i++) {
            symbols.add(String.valueOf(text.charAt(i)));
        }

        // BPE Merges anwenden (mit schnellem Rank-Lookup)
        boolean changed = true;
        while (changed && symbols.size() > 1) {
            changed = false;
            int bestMergeRank = Integer.MAX_VALUE;
            int bestIdx = -1;

            for (int i = 0; i < symbols.size() - 1; i++) {
                String key = symbols.get(i) + " " + symbols.get(i + 1);
                Integer rank = mergeRanks.get(key);
                if (rank != null && rank < bestMergeRank) {
                    bestMergeRank = rank;
                    bestIdx = i;
                }
            }

            if (bestIdx >= 0) {
                String merged = symbols.get(bestIdx) + symbols.get(bestIdx + 1);
                symbols.set(bestIdx, merged);
                symbols.remove(bestIdx + 1);
                changed = true;
            }
        }

        return symbols;
    }

    // ==================== Tokenizer Loading ====================

    private void loadTokenizerJson(Path path) throws IOException {
        String json = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

        // Vocabulary laden
        if (root.has("model")) {
            JsonObject model = root.getAsJsonObject("model");

            // vocab
            if (model.has("vocab")) {
                JsonObject vocabObj = model.getAsJsonObject("vocab");
                for (Map.Entry<String, JsonElement> entry : vocabObj.entrySet()) {
                    int id = entry.getValue().getAsInt();
                    vocab.put(entry.getKey(), id);
                    reverseVocab.put(id, entry.getKey());
                }
            }

            // BPE merges
            if (model.has("merges")) {
                JsonArray mergesArr = model.getAsJsonArray("merges");
                for (int i = 0; i < mergesArr.size(); i++) {
                    String merge = mergesArr.get(i).getAsString();
                    String[] parts = merge.split(" ", 2);
                    if (parts.length == 2) {
                        merges.add(parts);
                        mergeRanks.put(merge, i);
                    }
                }
            }
        }

        // added_tokens für Spezial-Tokens
        if (root.has("added_tokens")) {
            JsonArray addedTokens = root.getAsJsonArray("added_tokens");
            for (JsonElement elem : addedTokens) {
                JsonObject tokenObj = elem.getAsJsonObject();
                String content = tokenObj.has("content") ? tokenObj.get("content").getAsString() : "";
                int id = tokenObj.has("id") ? tokenObj.get("id").getAsInt() : -1;
                boolean special = tokenObj.has("special") && tokenObj.get("special").getAsBoolean();
                if (id >= 0) {
                    vocab.put(content, id);
                    reverseVocab.put(id, content);

                    if ("<|endoftext|>".equals(content) || "</s>".equals(content) || "<|end|>".equals(content)) {
                        eosTokenId = id;
                        eosTokenIds.add(id);
                    }
                    if ("<|startoftext|>".equals(content) || "<s>".equals(content)) {
                        bosTokenId = id;
                    }
                    if (special) {
                        specialTokens.add(content);
                    }
                }
            }
        }

        // Spezial-Tokens nach Länge absteigend sortieren (greedy matching)
        Collections.sort(specialTokens, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(b.length(), a.length());
            }
        });

        // Auto-Detect: '▁' (SentencePiece) vs 'Ġ' (GPT2)
        if (vocab.containsKey("\u0120")) {
            spacePrefix = "\u0120";
        } else {
            spacePrefix = SPIECE_SPACE;
        }
    }

    private void loadTokenizerConfig(Path path) throws IOException {
        String json = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

        if (root.has("eos_token")) {
            JsonElement eos = root.get("eos_token");
            if (eos.isJsonPrimitive()) {
                String eosStr = eos.getAsString();
                Integer id = vocab.get(eosStr);
                if (id != null) eosTokenId = id;
            }
        }
    }

    private void loadGenaiConfig(Path path) throws IOException {
        String json = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

        if (root.has("model")) {
            JsonObject model = root.getAsJsonObject("model");

            // EOS Token IDs aus Config
            if (model.has("eos_token_id")) {
                JsonElement eos = model.get("eos_token_id");
                if (eos.isJsonArray()) {
                    for (JsonElement e : eos.getAsJsonArray()) {
                        eosTokenIds.add(e.getAsInt());
                    }
                } else if (eos.isJsonPrimitive()) {
                    eosTokenIds.add(eos.getAsInt());
                }
            }

            // Decoder-Konfiguration
            if (model.has("decoder")) {
                JsonObject decoder = model.getAsJsonObject("decoder");
                if (decoder.has("num_hidden_layers")) numLayers = decoder.get("num_hidden_layers").getAsInt();
                if (decoder.has("head_size")) headSize = decoder.get("head_size").getAsInt();
                if (decoder.has("num_key_value_heads")) numKvHeads = decoder.get("num_key_value_heads").getAsInt();
            }
        }

        LOG.info("Modell-Config: " + numLayers + " Layers, head_size=" + headSize
                + ", kv_heads=" + numKvHeads + ", EOS=" + eosTokenIds);
    }

    // ==================== Fallback (kein Tokenizer geladen) ====================

    private long[] encodeFallback(String text) {
        long[] ids = new long[text.length()];
        for (int i = 0; i < text.length(); i++) {
            ids[i] = text.charAt(i);
        }
        return ids;
    }

    private String decodeFallback(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (id >= 0 && id < 0x110000) {
                sb.append((char) id);
            }
        }
        return sb.toString();
    }
}
