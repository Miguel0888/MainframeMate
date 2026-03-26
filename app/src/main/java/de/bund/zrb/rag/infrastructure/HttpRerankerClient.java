package de.bund.zrb.rag.infrastructure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.rag.config.RerankerSettings;
import de.bund.zrb.rag.port.RerankerClient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based reranker client compatible with common reranker APIs:
 * <ul>
 *   <li><b>TEI</b> (HuggingFace Text Embeddings Inference) — {@code /rerank}</li>
 *   <li><b>Jina Reranker API</b> — {@code /v1/rerank}</li>
 *   <li><b>Cohere Rerank</b> — {@code /v1/rerank}</li>
 *   <li>Any OpenAI-compatible rerank endpoint</li>
 * </ul>
 *
 * <h3>Request format (JSON):</h3>
 * <pre>{@code
 * {
 *   "model": "BAAI/bge-reranker-v2-m3",
 *   "query": "What is deep learning?",
 *   "documents": ["passage 1", "passage 2", ...],
 *   "top_n": 5
 * }
 * }</pre>
 *
 * <h3>Expected response format (JSON):</h3>
 * <pre>{@code
 * {
 *   "results": [
 *     {"index": 0, "relevance_score": 0.95},
 *     {"index": 2, "relevance_score": 0.82},
 *     ...
 *   ]
 * }
 * }</pre>
 */
public class HttpRerankerClient implements RerankerClient {

    private static final Logger LOG = Logger.getLogger(HttpRerankerClient.class.getName());
    private static final Gson GSON = new Gson();

    private final RerankerSettings settings;
    private boolean available;

    public HttpRerankerClient(RerankerSettings settings) {
        this.settings = settings;
        this.available = settings.isEnabled()
                && settings.getApiUrl() != null
                && !settings.getApiUrl().trim().isEmpty();
    }

    @Override
    public float[] rerank(String query, List<String> passages) throws RerankerException {
        if (passages == null || passages.isEmpty()) {
            return new float[0];
        }
        if (query == null || query.trim().isEmpty()) {
            // No query → return neutral scores
            float[] zeros = new float[passages.size()];
            java.util.Arrays.fill(zeros, 0f);
            return zeros;
        }

        // Build request body
        JsonObject requestBody = new JsonObject();
        if (settings.getModel() != null && !settings.getModel().trim().isEmpty()) {
            requestBody.addProperty("model", settings.getModel());
        }
        requestBody.addProperty("query", query);

        JsonArray docsArray = new JsonArray();
        for (String passage : passages) {
            docsArray.add(passage);
        }
        requestBody.add("documents", docsArray);
        requestBody.addProperty("top_n", passages.size()); // we re-order ourselves

        String jsonBody = GSON.toJson(requestBody);
        LOG.fine("[Reranker] POST " + settings.getApiUrl() + " with " + passages.size() + " passages");

        try {
            String response = doPost(settings.getApiUrl(), jsonBody);
            return parseResponse(response, passages.size());
        } catch (IOException e) {
            throw new RerankerException("Reranker HTTP request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse the reranker response. Supports two common formats:
     * <ol>
     *   <li>{@code {"results": [{"index": 0, "relevance_score": 0.95}, ...]}}</li>
     *   <li>{@code [{"index": 0, "score": 0.95}, ...]}</li>
     * </ol>
     */
    private float[] parseResponse(String responseJson, int passageCount) throws RerankerException {
        float[] scores = new float[passageCount];

        try {
            JsonElement root = JsonParser.parseString(responseJson);
            JsonArray results;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("results")) {
                    results = obj.getAsJsonArray("results");
                } else if (obj.has("data")) {
                    // Some APIs use "data" instead of "results"
                    results = obj.getAsJsonArray("data");
                } else {
                    throw new RerankerException("Unexpected response format: no 'results' or 'data' field");
                }
            } else if (root.isJsonArray()) {
                results = root.getAsJsonArray();
            } else {
                throw new RerankerException("Unexpected response format: " + responseJson);
            }

            for (JsonElement elem : results) {
                JsonObject entry = elem.getAsJsonObject();
                int index = entry.has("index") ? entry.get("index").getAsInt() : -1;

                float score = 0f;
                if (entry.has("relevance_score")) {
                    score = entry.get("relevance_score").getAsFloat();
                } else if (entry.has("score")) {
                    score = entry.get("score").getAsFloat();
                }

                if (index >= 0 && index < passageCount) {
                    scores[index] = score;
                }
            }

            return scores;
        } catch (RerankerException e) {
            throw e;
        } catch (Exception e) {
            throw new RerankerException("Failed to parse reranker response: " + e.getMessage(), e);
        }
    }

    private String doPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);

        HttpURLConnection conn;
        if (settings.isUseProxy()) {
            // Proxy settings are resolved globally via ProxySelector in the app
            conn = (HttpURLConnection) url.openConnection();
        } else {
            conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        }

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setReadTimeout(settings.getTimeoutSeconds() * 1000);
        conn.setDoOutput(true);

        String apiKey = settings.getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        }

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String error = readStream(conn.getErrorStream());
            throw new IOException("Reranker HTTP " + responseCode + ": " + error);
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

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getDescription() {
        return (settings.getModel() != null ? settings.getModel() : "unknown")
                + " @ " + settings.getApiUrl();
    }
}
