package de.bund.zrb.rag.config;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.AiProvider;
import de.bund.zrb.model.Settings;

import java.util.Map;

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

    /**
     * Lädt die EmbeddingSettings aus den gespeicherten Anwendungseinstellungen.
     * Verwendet die embeddingConfig aus Settings, falls vorhanden.
     * Proxy-Einstellungen werden aus dem zentralen Proxy-Tab übernommen.
     */
    public static EmbeddingSettings fromStoredConfig() {
        Settings settings = SettingsHelper.load();
        Map<String, String> embConfig = settings.embeddingConfig;

        EmbeddingSettings result = new EmbeddingSettings();

        if (embConfig != null && !embConfig.isEmpty()) {
            try {
                String providerStr = embConfig.getOrDefault("provider", "OLLAMA");
                result.setProvider(AiProvider.valueOf(providerStr));
            } catch (IllegalArgumentException e) {
                result.setProvider(AiProvider.OLLAMA);
            }

            result.setModel(embConfig.getOrDefault("model", "nomic-embed-text"));
            result.setApiKey(embConfig.getOrDefault("apiKey", ""));
            result.setBaseUrl(embConfig.getOrDefault("baseUrl", "http://localhost:11434"));

            try {
                result.setTimeoutSeconds(Integer.parseInt(embConfig.getOrDefault("timeout", "30")));
            } catch (NumberFormatException e) {
                result.setTimeoutSeconds(30);
            }

            try {
                result.setBatchSize(Integer.parseInt(embConfig.getOrDefault("batchSize", "10")));
            } catch (NumberFormatException e) {
                result.setBatchSize(10);
            }

            result.setEnabled(Boolean.parseBoolean(embConfig.getOrDefault("enabled", "true")));
        }

        // Proxy-Einstellungen aus dem zentralen Proxy-Tab übernehmen
        if (settings.proxyEnabled) {
            result.setUseProxy(true);
            if ("MANUAL".equals(settings.proxyMode)) {
                result.setProxyHost(settings.proxyHost);
                result.setProxyPort(settings.proxyPort);
            }
            // Bei PAC/WPAD wird der Proxy dynamisch ermittelt
        } else {
            result.setUseProxy(false);
        }

        return result;
    }
}

