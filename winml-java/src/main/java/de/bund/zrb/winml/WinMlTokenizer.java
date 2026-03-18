package de.bund.zrb.winml;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Minimal BPE tokenizer for ONNX LLMs (Phi-3/Phi-4 etc.).
 * <p>
 * Reads {@code tokenizer.json} from the model directory (Hugging Face format)
 * and implements BPE-based encoding/decoding.
 * <p>
 * This is a standalone copy (no ONNX Runtime dependency) for the winml-java module.
 * <p>
 * Also reads {@code genai_config.json} for model architecture parameters
 * (num_layers, head_size, num_kv_heads).
 */
final class WinMlTokenizer {

    private static final Logger LOG = Logger.getLogger(WinMlTokenizer.class.getName());

    /** SentencePiece space marker (U+2581). */
    private static final String SPIECE_SPACE = "\u2581";

    private final Map<String, Integer> vocab = new LinkedHashMap<String, Integer>();
    private final Map<Integer, String> reverseVocab = new HashMap<Integer, String>();

    private int eosTokenId = 2;
    private int bosTokenId = 1;
    private final Set<Integer> eosTokenIds = new HashSet<Integer>();

    private final List<String[]> merges = new ArrayList<String[]>();
    private final Map<String, Integer> mergeRanks = new HashMap<String, Integer>();
    private final List<String> specialTokens = new ArrayList<String>();

    private String spacePrefix = SPIECE_SPACE;

    private int numLayers = 32;
    private int headSize = 96;
    private int numKvHeads = 32;

    private boolean loaded = false;

    WinMlTokenizer(Path modelDir) {
        Path tokenizerJson = modelDir.resolve("tokenizer.json");
        Path genaiConfig = modelDir.resolve("genai_config.json");

        if (Files.exists(tokenizerJson)) {
            try {
                loadTokenizerJson(tokenizerJson);
                loaded = true;
                LOG.info("Tokenizer loaded: " + vocab.size() + " tokens, "
                        + merges.size() + " merges, EOS=" + eosTokenIds);
            } catch (Exception e) {
                LOG.warning("Failed to load tokenizer: " + e.getMessage());
            }
        }

        if (Files.exists(genaiConfig)) {
            try {
                loadGenaiConfig(genaiConfig);
            } catch (Exception e) {
                LOG.warning("Failed to load genai_config: " + e.getMessage());
            }
        }

        if (!loaded) {
            LOG.warning("No tokenizer.json in " + modelDir + " — fallback tokenization.");
        }
    }

    int getEosTokenId() { return eosTokenId; }
    Set<Integer> getEosTokenIds() { return Collections.unmodifiableSet(eosTokenIds); }
    int getNumLayers() { return numLayers; }
    int getHeadSize() { return headSize; }
    int getNumKvHeads() { return numKvHeads; }

    // ══════════════════════════════════════════════════════════
    //  Encode
    // ══════════════════════════════════════════════════════════

    long[] encode(String text) {
        if (!loaded) return encodeFallback(text);

        List<Long> result = new ArrayList<Long>();
        int pos = 0;
        while (pos < text.length()) {
            String matchedSpecial = null;
            for (String special : specialTokens) {
                if (text.startsWith(special, pos)) {
                    matchedSpecial = special;
                    break;
                }
            }
            if (matchedSpecial != null) {
                Integer id = vocab.get(matchedSpecial);
                if (id != null) result.add((long) id);
                pos += matchedSpecial.length();
                continue;
            }

            int end = pos + 1;
            while (end < text.length()) {
                boolean isSpecialStart = false;
                for (String special : specialTokens) {
                    if (text.startsWith(special, end)) { isSpecialStart = true; break; }
                }
                if (isSpecialStart) break;
                end++;
            }

            String segment = text.substring(pos, end);
            String normalized = spacePrefix + segment.replace(" ", spacePrefix);

            List<String> tokens = bpeEncode(normalized);
            for (String tok : tokens) {
                Integer id = vocab.get(tok);
                if (id != null) result.add((long) id);
            }
            pos = end;
        }

        long[] ids = new long[result.size()];
        for (int i = 0; i < result.size(); i++) ids[i] = result.get(i);
        return ids;
    }

    // ══════════════════════════════════════════════════════════
    //  Decode
    // ══════════════════════════════════════════════════════════

    String decode(long[] ids) {
        if (!loaded) return decodeFallback(ids);

        List<Object> parts = new ArrayList<Object>();
        for (long id : ids) {
            String token = reverseVocab.get((int) id);
            if (token == null) continue;

            if (token.length() == 6 && token.startsWith("<0x") && token.endsWith(">")) {
                try {
                    int b = Integer.parseInt(token.substring(3, 5), 16);
                    parts.add(new byte[]{(byte) b});
                    continue;
                } catch (NumberFormatException ignore) {}
            }

            parts.add(token.replace(spacePrefix, " "));
        }

        StringBuilder sb = new StringBuilder();
        List<Byte> byteBuffer = new ArrayList<Byte>();
        for (Object part : parts) {
            if (part instanceof byte[]) {
                for (byte b : (byte[]) part) byteBuffer.add(b);
            } else {
                if (!byteBuffer.isEmpty()) {
                    sb.append(decodeUtf8(byteBuffer));
                    byteBuffer.clear();
                }
                sb.append((String) part);
            }
        }
        if (!byteBuffer.isEmpty()) sb.append(decodeUtf8(byteBuffer));
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  BPE
    // ══════════════════════════════════════════════════════════

    private List<String> bpeEncode(String text) {
        List<String> symbols = new ArrayList<String>();
        for (int i = 0; i < text.length(); i++) {
            symbols.add(String.valueOf(text.charAt(i)));
        }

        boolean changed = true;
        while (changed && symbols.size() > 1) {
            changed = false;
            int bestRank = Integer.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < symbols.size() - 1; i++) {
                String key = symbols.get(i) + " " + symbols.get(i + 1);
                Integer rank = mergeRanks.get(key);
                if (rank != null && rank < bestRank) {
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
        return symbols;
    }

    // ══════════════════════════════════════════════════════════
    //  Loading
    // ══════════════════════════════════════════════════════════

    private void loadTokenizerJson(Path path) throws IOException {
        String json = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (root.has("model")) {
            JsonObject model = root.getAsJsonObject("model");
            if (model.has("vocab")) {
                for (Map.Entry<String, JsonElement> e : model.getAsJsonObject("vocab").entrySet()) {
                    int id = e.getValue().getAsInt();
                    vocab.put(e.getKey(), id);
                    reverseVocab.put(id, e.getKey());
                }
            }
            if (model.has("merges")) {
                JsonArray arr = model.getAsJsonArray("merges");
                for (int i = 0; i < arr.size(); i++) {
                    String[] parts = arr.get(i).getAsString().split(" ", 2);
                    if (parts.length == 2) {
                        merges.add(parts);
                        mergeRanks.put(parts[0] + " " + parts[1], i);
                    }
                }
            }
        }

        if (root.has("added_tokens")) {
            for (JsonElement el : root.getAsJsonArray("added_tokens")) {
                JsonObject t = el.getAsJsonObject();
                String content = t.has("content") ? t.get("content").getAsString() : "";
                int id = t.has("id") ? t.get("id").getAsInt() : -1;
                boolean special = t.has("special") && t.get("special").getAsBoolean();
                if (id >= 0) {
                    vocab.put(content, id);
                    reverseVocab.put(id, content);
                    if ("<|endoftext|>".equals(content) || "</s>".equals(content)
                            || "<|end|>".equals(content)) {
                        eosTokenId = id;
                        eosTokenIds.add(id);
                    }
                    if ("<|startoftext|>".equals(content) || "<s>".equals(content)) {
                        bosTokenId = id;
                    }
                    if (special) specialTokens.add(content);
                }
            }
        }

        Collections.sort(specialTokens, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Integer.compare(b.length(), a.length());
            }
        });

        spacePrefix = vocab.containsKey("\u0120") ? "\u0120" : SPIECE_SPACE;
    }

    private void loadGenaiConfig(Path path) throws IOException {
        String json = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (root.has("model")) {
            JsonObject model = root.getAsJsonObject("model");
            if (model.has("eos_token_id")) {
                JsonElement eos = model.get("eos_token_id");
                if (eos.isJsonArray()) {
                    for (JsonElement e : eos.getAsJsonArray()) eosTokenIds.add(e.getAsInt());
                } else if (eos.isJsonPrimitive()) {
                    eosTokenIds.add(eos.getAsInt());
                }
            }
            if (model.has("decoder")) {
                JsonObject d = model.getAsJsonObject("decoder");
                if (d.has("num_hidden_layers")) numLayers = d.get("num_hidden_layers").getAsInt();
                if (d.has("head_size")) headSize = d.get("head_size").getAsInt();
                if (d.has("num_key_value_heads")) numKvHeads = d.get("num_key_value_heads").getAsInt();
            }
        }

        LOG.info("Model config: " + numLayers + " layers, head=" + headSize
                + ", kvHeads=" + numKvHeads + ", EOS=" + eosTokenIds);
    }

    // ══════════════════════════════════════════════════════════
    //  Fallback
    // ══════════════════════════════════════════════════════════

    private long[] encodeFallback(String text) {
        long[] ids = new long[text.length()];
        for (int i = 0; i < text.length(); i++) ids[i] = text.charAt(i);
        return ids;
    }

    private String decodeFallback(long[] ids) {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (id >= 0 && id < 0x110000) sb.append((char) id);
        }
        return sb.toString();
    }

    private static String decodeUtf8(List<Byte> bytes) {
        byte[] arr = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) arr[i] = bytes.get(i);
        return new String(arr, Charset.forName("UTF-8"));
    }
}

