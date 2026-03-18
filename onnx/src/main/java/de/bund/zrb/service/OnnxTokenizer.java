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
 * Falls kein {@code tokenizer.json} vorhanden ist, fällt er auf eine einfache
 * Whitespace-Tokenisierung zurück (nur für Tests geeignet).
 */
final class OnnxTokenizer {

    private static final Logger LOG = Logger.getLogger(OnnxTokenizer.class.getName());

    // Token → ID
    private final Map<String, Integer> vocab = new LinkedHashMap<>();
    // ID → Token
    private final Map<Integer, String> reverseVocab = new HashMap<>();

    // Spezial-Tokens
    private int eosTokenId = 2;    // default, wird aus config überschrieben
    private int bosTokenId = 1;
    private int padTokenId = 0;
    private final Set<Integer> eosTokenIds = new HashSet<>();

    // BPE Merge-Regeln (Paare in Prioritätsreihenfolge)
    private final List<String[]> merges = new ArrayList<>();

    // Spezial-Token-Liste (sortiert nach Länge absteigend für greedy matching)
    private final List<String> specialTokens = new ArrayList<>();

    // Leerzeichen-Prefix-Zeichen: '▁' (U+2581, SentencePiece/LLaMA) oder 'Ġ' (U+0120, GPT2)
    private char spaceChar = '\u2581';

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
                        + merges.size() + " Merges, EOS=" + eosTokenIds);
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

    /**
     * Encodiert einen String in Token-IDs.
     * Erkennt Spezial-Tokens (z.B. {@code <|user|>}) und encodiert sie direkt.
     */
    long[] encode(String text) {
        if (!loaded) {
            return encodeFallback(text);
        }

        List<Long> result = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            // Spezial-Token greedy matchen (längster zuerst)
            String matchedSpecial = null;
            for (String special : specialTokens) {
                if (text.startsWith(special, pos)) {
                    matchedSpecial = special;
                    break; // Liste ist nach Länge sortiert
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
            List<String> tokens = bpeEncode(segment);
            for (String tok : tokens) {
                Integer id = vocab.get(tok);
                result.add(id != null ? (long) id.intValue() : 0L);
            }
            pos = end;
        }

        long[] ids = new long[result.size()];
        for (int i = 0; i < result.size(); i++) ids[i] = result.get(i);
        return ids;
    }

    /**
     * Decoded Token-IDs zurück in Text.
     */
    String decode(long[] ids) {
        if (!loaded) {
            return decodeFallback(ids);
        }

        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            String token = reverseVocab.get((int) id);
            if (token != null) {
                // Leerzeichen-Prefix zurückwandeln
                sb.append(token.replace(spaceChar, ' '));
            }
        }
        return sb.toString();
    }

    // ==================== BPE Encoding ====================

    private List<String> bpeEncode(String text) {
        // Pre-tokenize: Leerzeichen durch Space-Prefix-Char ersetzen
        text = text.replace(' ', spaceChar);

        // In Zeichen aufteilen
        List<String> symbols = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            symbols.add(String.valueOf(text.charAt(i)));
        }

        // BPE Merges anwenden
        boolean changed = true;
        while (changed && symbols.size() > 1) {
            changed = false;
            int bestMergeRank = Integer.MAX_VALUE;
            int bestIdx = -1;

            for (int i = 0; i < symbols.size() - 1; i++) {
                int rank = getMergeRank(symbols.get(i), symbols.get(i + 1));
                if (rank >= 0 && rank < bestMergeRank) {
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

    private int getMergeRank(String left, String right) {
        for (int i = 0; i < merges.size(); i++) {
            if (merges.get(i)[0].equals(left) && merges.get(i)[1].equals(right)) {
                return i;
            }
        }
        return -1;
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
                for (JsonElement elem : mergesArr) {
                    String merge = elem.getAsString();
                    String[] parts = merge.split(" ", 2);
                    if (parts.length == 2) {
                        merges.add(parts);
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
            spaceChar = '\u0120';
        } else {
            spaceChar = '\u2581';
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

    /**
     * Fallback-Encoding: Jedes Zeichen wird als eigener Token behandelt.
     * NUR für Tests / Debugging wenn kein tokenizer.json vorhanden.
     */
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
