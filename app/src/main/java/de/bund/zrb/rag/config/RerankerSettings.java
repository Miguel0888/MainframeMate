package de.bund.zrb.rag.config;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;

import java.util.Map;

/**
 * Configuration for the optional cross-encoder reranker stage.
 *
 * <p>A reranker (e.g. BAAI/bge-reranker-v2-m3) scores each (query, passage) pair
 * with a cross-encoder model, producing much more accurate relevance scores than
 * the initial bi-encoder embedding similarity or BM25 lexical score.
 *
 * <p>The typical RAG pipeline becomes:
 * <ol>
 *   <li>Hybrid retrieval (BM25 + semantic) → large candidate set</li>
 *   <li><b>Reranker</b> → re-scores and re-orders the candidate set</li>
 *   <li>Top-K selection → final chunks for LLM context</li>
 * </ol>
 *
 * <p>Supported providers:
 * <ul>
 *   <li><b>Ollama</b> — local, via {@code POST /api/embed} (requires an Ollama version
 *       that exposes reranking scores, or a cross-encoder model served as embedding model)</li>
 *   <li><b>Custom API</b> — any HTTP endpoint that accepts a JSON body with
 *       {@code query} and {@code documents} and returns a list of {@code score} values.
 *       Compatible with: Jina Reranker API, Cohere Rerank, TEI /rerank, vLLM /score, etc.</li>
 * </ul>
 *
 * <p>Recommended models:
 * <ul>
 *   <li>BAAI/bge-reranker-v2-m3 — multilingual, fast, excellent quality</li>
 *   <li>BAAI/bge-reranker-v2-gemma — larger, highest quality</li>
 *   <li>cross-encoder/ms-marco-MiniLM-L-6-v2 — English-only, very fast</li>
 * </ul>
 */
public class RerankerSettings {

    /** Whether the reranker stage is enabled. */
    private boolean enabled = false;

    /**
     * Base URL of the reranker API endpoint.
     * <ul>
     *   <li>TEI (Text Embeddings Inference): {@code http://localhost:8082/rerank}</li>
     *   <li>Jina: {@code https://api.jina.ai/v1/rerank}</li>
     *   <li>Cohere: {@code https://api.cohere.ai/v1/rerank}</li>
     *   <li>Custom: any URL that follows the rerank protocol</li>
     * </ul>
     */
    private String apiUrl = "http://localhost:8082/rerank";

    /** Model name sent with the request (e.g. {@code BAAI/bge-reranker-v2-m3}). */
    private String model = "BAAI/bge-reranker-v2-m3";

    /** Optional API key for authenticated endpoints (Jina, Cohere, etc.). */
    private String apiKey = "";

    /**
     * Number of top candidates to keep after reranking.
     * The hybrid retriever fetches {@code candidatePoolSize} results,
     * the reranker re-scores them, and finally the top {@code topN} are returned.
     */
    private int topN = 5;

    /**
     * Size of the candidate pool sent to the reranker.
     * Should be larger than {@code topN} — typically 3–5× larger.
     * The reranker scores all candidates and keeps only the best {@code topN}.
     */
    private int candidatePoolSize = 50;

    /** HTTP request timeout in seconds. */
    private int timeoutSeconds = 30;

    /** Whether to route requests through the configured proxy. */
    private boolean useProxy = false;

    /**
     * Minimum relevance score (0.0–1.0) for a reranked result to be included.
     * Chunks scoring below this threshold are discarded even if they are
     * within the topN limit. Set to 0.0 to disable threshold filtering.
     */
    private float scoreThreshold = 0.0f;

    // ── Getters & Setters (fluent) ──────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public RerankerSettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public RerankerSettings setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
        return this;
    }

    public String getModel() {
        return model;
    }

    public RerankerSettings setModel(String model) {
        this.model = model;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public RerankerSettings setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public int getTopN() {
        return topN;
    }

    public RerankerSettings setTopN(int topN) {
        this.topN = topN;
        return this;
    }

    public int getCandidatePoolSize() {
        return candidatePoolSize;
    }

    public RerankerSettings setCandidatePoolSize(int candidatePoolSize) {
        this.candidatePoolSize = candidatePoolSize;
        return this;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public RerankerSettings setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public RerankerSettings setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        return this;
    }

    public float getScoreThreshold() {
        return scoreThreshold;
    }

    public RerankerSettings setScoreThreshold(float scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
        return this;
    }

    // ── Factory ─────────────────────────────────────────────────────

    public static RerankerSettings defaults() {
        return new RerankerSettings();
    }

    /**
     * Load reranker settings from the stored application configuration.
     */
    public static RerankerSettings fromStoredConfig() {
        Settings settings = SettingsHelper.load();
        Map<String, String> cfg = settings.rerankerConfig;

        RerankerSettings result = new RerankerSettings();
        if (cfg == null || cfg.isEmpty()) {
            return result;
        }

        result.setEnabled(Boolean.parseBoolean(cfg.getOrDefault("enabled", "false")));
        result.setApiUrl(cfg.getOrDefault("apiUrl", "http://localhost:8082/rerank"));
        result.setModel(cfg.getOrDefault("model", "BAAI/bge-reranker-v2-m3"));
        result.setApiKey(cfg.getOrDefault("apiKey", ""));

        try { result.setTopN(Integer.parseInt(cfg.getOrDefault("topN", "5"))); }
        catch (NumberFormatException e) { /* keep default */ }

        try { result.setCandidatePoolSize(Integer.parseInt(cfg.getOrDefault("candidatePoolSize", "50"))); }
        catch (NumberFormatException e) { /* keep default */ }

        try { result.setTimeoutSeconds(Integer.parseInt(cfg.getOrDefault("timeout", "30"))); }
        catch (NumberFormatException e) { /* keep default */ }

        result.setUseProxy(Boolean.parseBoolean(cfg.getOrDefault("useProxy", "false")));

        try { result.setScoreThreshold(Float.parseFloat(cfg.getOrDefault("scoreThreshold", "0.0"))); }
        catch (NumberFormatException e) { /* keep default */ }

        return result;
    }
}
