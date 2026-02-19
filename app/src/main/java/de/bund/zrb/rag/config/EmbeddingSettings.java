package de.bund.zrb.rag.config;

import de.bund.zrb.model.AiProvider;

/**
 * Configuration for embedding generation.
 * Separate from AI settings to allow different providers.
 */
public class EmbeddingSettings {

    private AiProvider provider = AiProvider.OLLAMA;
    private String model = "nomic-embed-text";
    private String apiKey = "";
    private String baseUrl = "http://localhost:11434";
    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort = 0;
    private int timeoutSeconds = 30;
    private int batchSize = 10;
    private boolean enabled = true;

    // Getters and setters

    public AiProvider getProvider() {
        return provider;
    }

    public EmbeddingSettings setProvider(AiProvider provider) {
        this.provider = provider;
        return this;
    }

    public String getModel() {
        return model;
    }

    public EmbeddingSettings setModel(String model) {
        this.model = model;
        return this;
    }

    public String getApiKey() {
        return apiKey;
    }

    public EmbeddingSettings setApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public EmbeddingSettings setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public EmbeddingSettings setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
        return this;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public EmbeddingSettings setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public EmbeddingSettings setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public EmbeddingSettings setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        return this;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public EmbeddingSettings setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EmbeddingSettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Get default embedding model for provider.
     */
    public static String getDefaultModel(AiProvider provider) {
        if (provider == null) return "nomic-embed-text";
        switch (provider) {
            case OLLAMA:
                return "nomic-embed-text";
            case CLOUD:
                return "text-embedding-3-small";
            case LOCAL_AI:
            case LLAMA_CPP_SERVER:
                return "all-minilm";
            case DISABLED:
            default:
                return "nomic-embed-text";
        }
    }

    /**
     * Get default base URL for provider.
     */
    public static String getDefaultBaseUrl(AiProvider provider) {
        if (provider == null) return "http://localhost:11434";
        switch (provider) {
            case OLLAMA:
                return "http://localhost:11434";
            case CLOUD:
                return "https://api.openai.com/v1";
            case LOCAL_AI:
                return "http://localhost:8080/v1";
            case LLAMA_CPP_SERVER:
                return "http://localhost:8080/v1";
            case DISABLED:
            default:
                return "http://localhost:11434";
        }
    }

    public static EmbeddingSettings defaults() {
        return new EmbeddingSettings();
    }
}

