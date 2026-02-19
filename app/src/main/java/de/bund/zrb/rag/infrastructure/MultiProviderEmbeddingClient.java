package de.bund.zrb.rag.infrastructure;

import de.bund.zrb.model.AiProvider;
import de.bund.zrb.rag.config.EmbeddingSettings;
import de.bund.zrb.rag.port.EmbeddingClient;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Multi-provider embedding client supporting Ollama, OpenAI, and OpenAI-compatible APIs.
 */
public class MultiProviderEmbeddingClient implements EmbeddingClient {

    private static final Logger LOG = Logger.getLogger(MultiProviderEmbeddingClient.class.getName());
    private static final Gson GSON = new Gson();

    private final EmbeddingSettings settings;
    private final ConcurrentHashMap<String, float[]> cache = new ConcurrentHashMap<>();
    private int cachedDimension = 0;
    private boolean available = false;

    public MultiProviderEmbeddingClient(EmbeddingSettings settings) {
        this.settings = settings;
        checkAvailability();
    }

    private void checkAvailability() {
        if (!settings.isEnabled()) {
            this.available = false;
            return;
        }

        try {
            // Try a simple request to check availability
            float[] test = embedInternal("test");
            this.available = test != null && test.length > 0;
            if (available) {
                this.cachedDimension = test.length;
                LOG.info("Embedding client available: " + settings.getProvider() +
                        " model=" + settings.getModel() + " dim=" + cachedDimension);
            }
        } catch (Exception e) {
            LOG.warning("Embedding client not available: " + e.getMessage());
            this.available = false;
        }
    }

    @Override
    public float[] embed(String text) {
        if (!available || text == null || text.isEmpty()) {
            return new float[0];
        }

        // Check cache
        String cacheKey = Integer.toHexString(text.hashCode());
        float[] cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            float[] embedding = embedInternal(text);
            if (embedding != null && embedding.length > 0) {
                cache.put(cacheKey, embedding);
                return embedding;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Embedding failed", e);
        }

        return new float[0];
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!available || texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>();

        // Process in batches
        int batchSize = settings.getBatchSize();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);

            try {
                List<float[]> batchResults = embedBatchInternal(batch);
                results.addAll(batchResults);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Batch embedding failed", e);
                // Fall back to individual embedding
                for (String text : batch) {
                    results.add(embed(text));
                }
            }
        }

        return results;
    }

    @Override
    public int getDimension() {
        return cachedDimension;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getModelName() {
        return settings.getModel();
    }

    /**
     * Refresh availability status.
     */
    public void refresh() {
        checkAvailability();
    }

    /**
     * Clear the embedding cache.
     */
    public void clearCache() {
        cache.clear();
    }

    private float[] embedInternal(String text) throws IOException {
        switch (settings.getProvider()) {
            case OLLAMA:
                return embedOllama(text);
            case CLOUD:
            case LOCAL_AI:
            case LLAMA_CPP_SERVER:
                return embedOpenAI(text);
            case DISABLED:
            default:
                throw new UnsupportedOperationException("Unsupported provider: " + settings.getProvider());
        }
    }

    private List<float[]> embedBatchInternal(List<String> texts) throws IOException {
        switch (settings.getProvider()) {
            case OLLAMA:
                // Ollama doesn't support batch, embed individually
                List<float[]> results = new ArrayList<>();
                for (String text : texts) {
                    results.add(embedOllama(text));
                }
                return results;
            case CLOUD:
            case LOCAL_AI:
            case LLAMA_CPP_SERVER:
                return embedOpenAIBatch(texts);
            case DISABLED:
            default:
                throw new UnsupportedOperationException("Unsupported provider: " + settings.getProvider());
        }
    }

    private float[] embedOllama(String text) throws IOException {
        String url = settings.getBaseUrl() + "/api/embeddings";

        JsonObject request = new JsonObject();
        request.addProperty("model", settings.getModel());
        request.addProperty("prompt", text);

        String response = httpPost(url, request.toString(), null);
        JsonObject json = GSON.fromJson(response, JsonObject.class);

        JsonArray embedding = json.getAsJsonArray("embedding");
        return jsonArrayToFloatArray(embedding);
    }

    private float[] embedOpenAI(String text) throws IOException {
        List<float[]> results = embedOpenAIBatch(Collections.singletonList(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    private List<float[]> embedOpenAIBatch(List<String> texts) throws IOException {
        String url = settings.getBaseUrl() + "/embeddings";

        JsonObject request = new JsonObject();
        request.addProperty("model", settings.getModel());

        JsonArray input = new JsonArray();
        for (String text : texts) {
            input.add(text);
        }
        request.add("input", input);

        String response = httpPost(url, request.toString(), settings.getApiKey());
        JsonObject json = GSON.fromJson(response, JsonObject.class);

        List<float[]> results = new ArrayList<>();
        JsonArray data = json.getAsJsonArray("data");

        if (data != null) {
            for (JsonElement element : data) {
                JsonArray embedding = element.getAsJsonObject().getAsJsonArray("embedding");
                results.add(jsonArrayToFloatArray(embedding));
            }
        }

        return results;
    }

    private String httpPost(String urlString, String body, String apiKey) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn;

        if (settings.isUseProxy() && settings.getProxyHost() != null && !settings.getProxyHost().isEmpty()) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(settings.getProxyHost(), settings.getProxyPort()));
            conn = (HttpURLConnection) url.openConnection(proxy);
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setReadTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setDoOutput(true);

        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = readStream(conn.getErrorStream());
            throw new IOException("HTTP " + responseCode + ": " + error);
        }

        return readStream(conn.getInputStream());
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private float[] jsonArrayToFloatArray(JsonArray array) {
        if (array == null) return new float[0];

        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsFloat();
        }
        return result;
    }
}

