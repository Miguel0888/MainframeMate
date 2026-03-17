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

    private static final Logger LOG = de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.AI);

    // Token → ID
    private final Map<String, Integer> vocab = new LinkedHashMap<>();
    // ID → Token
    private final Map<Integer, String> reverseVocab = new HashMap<>();

    // Spezial-Tokens
    private int eosTokenId = 2;    // default, wird aus config überschrieben
    private int bosTokenId = 1;
    private int padTokenId = 0;

    // BPE Merge-Regeln (Paare in Prioritätsreihenfolge)
    private final List<String[]> merges = new ArrayList<>();

    private boolean loaded = false;

    OnnxTokenizer(Path modelDir) {
        Path tokenizerJson = modelDir.resolve("tokenizer.json");
        Path tokenizerConfig = modelDir.resolve("tokenizer_config.json");

        if (Files.exists(tokenizerJson)) {
            try {
                loadTokenizerJson(tokenizerJson);
                loaded = true;
                LOG.info("Tokenizer geladen: " + vocab.size() + " Tokens, "
                        + merges.size() + " Merges, EOS=" + eosTokenId);
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

        if (!loaded) {
            LOG.warning("Kein tokenizer.json gefunden in " + modelDir
                    + " – Fallback auf einfache Whitespace-Tokenisierung.");
        }
    }

    int getEosTokenId() { return eosTokenId; }
    int getBosTokenId() { return bosTokenId; }

    /**
     * Encodiert einen String in Token-IDs.
     */
    long[] encode(String text) {
        if (!loaded) {
            return encodeFallback(text);
        }

        // Einfaches character-level pre-tokenization mit BPE
        List<String> tokens = bpeEncode(text);
        long[] ids = new long[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            Integer id = vocab.get(tokens.get(i));
            ids[i] = id != null ? id : 0; // UNK
        }
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
                // Hugging Face BPE verwendet 'Ġ' (U+0120) für Leerzeichen-Prefix
                sb.append(token.replace('\u0120', ' '));
            }
        }
        return sb.toString();
    }

    // ==================== BPE Encoding ====================

    private List<String> bpeEncode(String text) {
        // Pre-tokenize: split on whitespace/punctuation boundaries
        List<String> words = preTokenize(text);
        List<String> result = new ArrayList<>();

        for (String word : words) {
            // Split word into characters
            List<String> symbols = new ArrayList<>();
            for (int i = 0; i < word.length(); i++) {
                symbols.add(String.valueOf(word.charAt(i)));
            }

            // Apply BPE merges iteratively
            boolean changed = true;
            while (changed && symbols.size() > 1) {
                changed = false;
                int bestMergeRank = Integer.MAX_VALUE;
                int bestIdx = -1;

                for (int i = 0; i < symbols.size() - 1; i++) {
                    String pair = symbols.get(i) + " " + symbols.get(i + 1);
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

            result.addAll(symbols);
        }

        return result;
    }

    private int getMergeRank(String left, String right) {
        for (int i = 0; i < merges.size(); i++) {
            if (merges.get(i)[0].equals(left) && merges.get(i)[1].equals(right)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> preTokenize(String text) {
        // Hugging Face GPT2-style: split on spaces, keeping the space as prefix 'Ġ'
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' && current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
                current.append('\u0120'); // Ġ prefix for space
            } else if (c == ' ' && current.length() == 0) {
                current.append('\u0120');
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
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
                if (id >= 0) {
                    vocab.put(content, id);
                    reverseVocab.put(id, content);

                    if ("<|endoftext|>".equals(content) || "</s>".equals(content) || "<|end|>".equals(content)) {
                        eosTokenId = id;
                    }
                    if ("<|startoftext|>".equals(content) || "<s>".equals(content)) {
                        bosTokenId = id;
                    }
                }
            }
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

