package de.bund.zrb.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.RetryInterceptor;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import de.bund.zrb.net.ProxyResolver;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Custom ChatManager für selbstgehostete LLM-Server.
 * Basiert auf dem Ollama-Protokoll, bietet aber erweiterte Konfigurationsmöglichkeiten:
 * - Benutzerdefinierte API-Endpunkte
 * - Authentifizierung (API-Key, Bearer Token)
 * - Benutzerdefinierte Header
 * - Anpassbare Timeouts
 * - SSL/TLS-Optionen
 * - Temperatur und andere Generierungsparameter
 */
public class CustomChatManager implements ChatManager {

    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    private final OkHttpClient baseClient;
    private final Gson gson;

    public CustomChatManager() {
        Settings settings = SettingsHelper.load();
        int connectTimeout = parseIntSafe(settings.aiConfig.getOrDefault("custom.connectTimeout", "10"), 10);
        int readTimeout = parseIntSafe(settings.aiConfig.getOrDefault("custom.readTimeout", "0"), 0);
        int writeTimeout = parseIntSafe(settings.aiConfig.getOrDefault("custom.writeTimeout", "0"), 0);
        int maxRetries = parseIntSafe(settings.aiConfig.getOrDefault("custom.maxRetries", "3"), 3);
        int retryDelay = parseIntSafe(settings.aiConfig.getOrDefault("custom.retryDelay", "1000"), 1000);

        this.baseClient = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(maxRetries, retryDelay))
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                .build();
        this.gson = new Gson();
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDoubleSafe(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public UUID newSession() {
        UUID sessionId = UUID.randomUUID();
        sessionHistories.put(sessionId, new ChatHistory(sessionId));
        return sessionId;
    }

    @Override
    public void clearHistory(UUID sessionId) {
        ChatHistory history = sessionHistories.get(sessionId);
        if (history != null) {
            history.clear();
        }
    }

    @Override
    public List<String> getFormattedHistory(UUID sessionId) {
        ChatHistory history = sessionHistories.get(sessionId);
        return history != null ? history.getFormattedHistory() : Collections.emptyList();
    }

    @Override
    public ChatHistory getHistory(UUID sessionId) {
        return sessionHistories.getOrDefault(sessionId, new ChatHistory(sessionId));
    }

    @Override
    public void addUserMessage(UUID sessionId, String message) {
        sessionHistories.computeIfAbsent(sessionId, ChatHistory::new).addUserMessage(message);
    }

    @Override
    public void addBotMessage(UUID sessionId, String message) {
        sessionHistories.computeIfAbsent(sessionId, ChatHistory::new).addBotMessage(message);
    }

    private static final class ResponseChunk {
        final String text;
        final boolean done;
        final String error;

        private ResponseChunk(String text, boolean done, String error) {
            this.text = text;
            this.done = done;
            this.error = error;
        }

        static ResponseChunk empty() {
            return new ResponseChunk(null, false, null);
        }
    }

    @Override
    public boolean streamAnswer(UUID sessionId, boolean useContext, String userInput, ChatStreamListener listener, boolean keepAlive) throws IOException {
        if (sessionId == null) {
            return false;
        }
        if (activeCalls.containsKey(sessionId)) {
            return false;
        }

        Settings settings = SettingsHelper.load();

        // Custom-Einstellungen laden
        String url = settings.aiConfig.getOrDefault("custom.url", "http://localhost:11434/api/generate");
        String model = settings.aiConfig.getOrDefault("custom.model", "llama3.2");
        String apiKey = settings.aiConfig.getOrDefault("custom.apiKey", "");
        String authHeader = settings.aiConfig.getOrDefault("custom.authHeader", "Authorization");
        String authPrefix = settings.aiConfig.getOrDefault("custom.authPrefix", "Bearer");
        String keepAliveValue = settings.aiConfig.getOrDefault("custom.keepAlive", "10m");
        double temperature = parseDoubleSafe(settings.aiConfig.getOrDefault("custom.temperature", "0.7"), 0.7);
        int maxTokens = parseIntSafe(settings.aiConfig.getOrDefault("custom.maxTokens", "0"), 0); // 0 = unbegrenzt
        String systemPrompt = settings.aiConfig.getOrDefault("custom.systemPrompt", "");
        String responseFormat = settings.aiConfig.getOrDefault("custom.responseFormat", "ollama"); // ollama, openai
        Map<String, String> customHeaders = parseCustomHeaders(settings.aiConfig.getOrDefault("custom.headers", ""));

        OkHttpClient client = buildClient(url, settings);

        ChatHistory history = useContext
                ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new)
                : null;

        String prompt = (history != null)
                ? buildPrompt(history, userInput, systemPrompt)
                : (systemPrompt.isEmpty() ? userInput : systemPrompt + "\n\n" + userInput);

        // Request-Body bauen
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("prompt", prompt);
        requestBody.addProperty("stream", true);

        if (temperature > 0) {
            requestBody.addProperty("temperature", temperature);
        }
        if (maxTokens > 0) {
            requestBody.addProperty("max_tokens", maxTokens);
            requestBody.addProperty("num_predict", maxTokens); // Ollama-Style
        }
        if (!keepAliveValue.isEmpty()) {
            requestBody.addProperty("keep_alive", keepAlive ? keepAliveValue : "0m");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")));

        // Authentifizierung hinzufügen
        if (!apiKey.isEmpty()) {
            String authValue = authPrefix.isEmpty() ? apiKey : authPrefix + " " + apiKey;
            requestBuilder.addHeader(authHeader, authValue);
        }

        // Benutzerdefinierte Header hinzufügen
        for (Map.Entry<String, String> header : customHeaders.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        Request request = requestBuilder.build();
        Call call = client.newCall(request);
        activeCalls.put(sessionId, call);
        listener.onStreamStart();

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                activeCalls.remove(sessionId);
                listener.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        String errorMsg = "HTTP " + response.code();
                        if (body != null) {
                            try {
                                String bodyStr = body.string();
                                if (!bodyStr.isEmpty()) {
                                    errorMsg += ": " + bodyStr;
                                }
                            } catch (IOException ignored) {}
                        }
                        listener.onError(new IOException(errorMsg));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(body.charStream());
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ResponseChunk chunk = extractResponse(line, responseFormat);

                        if (chunk.error != null && !chunk.error.trim().isEmpty()) {
                            listener.onError(new IOException("Server error: " + chunk.error));
                            return;
                        }

                        if (chunk.text != null && !chunk.text.isEmpty()) {
                            listener.onStreamChunk(chunk.text);
                        }

                        if (chunk.done) {
                            break;
                        }
                    }

                    listener.onStreamEnd();
                } catch (IOException e) {
                    listener.onError(e);
                } finally {
                    activeCalls.remove(sessionId);
                }
            }
        });
        return true;
    }

    @Override
    public void cancel(UUID sessionId) {
        Call call = activeCalls.remove(sessionId);
        if (call != null) {
            call.cancel();
        }
    }

    @Override
    public void onDispose() {
        // not required
    }

    @Override
    public void closeSession(UUID sessionId) {
        Call call = activeCalls.remove(sessionId);
        if (call != null) {
            call.cancel();
        }
        sessionHistories.remove(sessionId);
    }

    private String buildPrompt(ChatHistory history, String userInput, String systemPrompt) {
        if (systemPrompt.isEmpty()) {
            return history.toPrompt(userInput);
        }
        // System-Prompt voranstellen, falls nicht bereits in der History
        return systemPrompt + "\n\n" + history.toPrompt(userInput);
    }

    private Map<String, String> parseCustomHeaders(String headerString) {
        Map<String, String> headers = new HashMap<>();
        if (headerString == null || headerString.trim().isEmpty()) {
            return headers;
        }
        // Format: "Header1: Value1; Header2: Value2"
        String[] parts = headerString.split(";");
        for (String part : parts) {
            int colonIndex = part.indexOf(':');
            if (colonIndex > 0) {
                String key = part.substring(0, colonIndex).trim();
                String value = part.substring(colonIndex + 1).trim();
                if (!key.isEmpty()) {
                    headers.put(key, value);
                }
            }
        }
        return headers;
    }

    private ResponseChunk extractResponse(String jsonLine, String responseFormat) {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            return ResponseChunk.empty();
        }

        // SSE-Prefix entfernen (falls vorhanden)
        if (jsonLine.startsWith("data: ")) {
            jsonLine = jsonLine.substring(6);
        }
        if (jsonLine.equals("[DONE]")) {
            return new ResponseChunk(null, true, null);
        }

        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();

            String error = null;
            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                JsonElement errorEl = obj.get("error");
                if (errorEl.isJsonPrimitive()) {
                    error = errorEl.getAsString();
                } else if (errorEl.isJsonObject() && errorEl.getAsJsonObject().has("message")) {
                    error = errorEl.getAsJsonObject().get("message").getAsString();
                }
            }

            boolean done = obj.has("done") && !obj.get("done").isJsonNull() && obj.get("done").getAsBoolean();

            // OpenAI-Style Response (choices[0].delta.content)
            if ("openai".equals(responseFormat) || obj.has("choices")) {
                if (obj.has("choices") && obj.get("choices").isJsonArray()) {
                    JsonArray choices = obj.getAsJsonArray("choices");
                    if (choices.size() > 0) {
                        JsonObject choice = choices.get(0).getAsJsonObject();

                        // Streaming (delta)
                        if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                            JsonObject delta = choice.getAsJsonObject("delta");
                            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                return new ResponseChunk(delta.get("content").getAsString(), false, error);
                            }
                        }

                        // Non-streaming (message)
                        if (choice.has("message") && choice.get("message").isJsonObject()) {
                            JsonObject message = choice.getAsJsonObject("message");
                            if (message.has("content") && !message.get("content").isJsonNull()) {
                                return new ResponseChunk(message.get("content").getAsString(), true, error);
                            }
                        }

                        // finish_reason
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                            String finishReason = choice.get("finish_reason").getAsString();
                            if ("stop".equals(finishReason) || "length".equals(finishReason)) {
                                done = true;
                            }
                        }
                    }
                }
            }

            // Ollama-Style Response
            if (obj.has("response") && !obj.get("response").isJsonNull()) {
                String response = obj.get("response").getAsString();
                if (!response.isEmpty()) {
                    return new ResponseChunk(response, done, error);
                }
            }

            // Ollama /api/chat Style
            if (obj.has("message") && obj.get("message").isJsonObject()) {
                JsonObject message = obj.getAsJsonObject("message");
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    JsonElement content = message.get("content");
                    if (content.isJsonPrimitive()) {
                        String text = content.getAsString();
                        if (!text.isEmpty()) {
                            return new ResponseChunk(text, done, error);
                        }
                    }
                }

                // Tool Calls
                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    String toolCalls = formatToolCallsAsJsonLines(message.getAsJsonArray("tool_calls"));
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        return new ResponseChunk(toolCalls, done, error);
                    }
                }
            }

            if (obj.has("tool_calls") && obj.get("tool_calls").isJsonArray()) {
                String toolCalls = formatToolCallsAsJsonLines(obj.getAsJsonArray("tool_calls"));
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    return new ResponseChunk(toolCalls, done, error);
                }
            }

            if (error != null && !error.trim().isEmpty()) {
                return new ResponseChunk(null, done, error);
            }

            if (done) {
                return new ResponseChunk(null, true, null);
            }
        } catch (Exception ignored) {
            // ignore malformed chunks
        }
        return ResponseChunk.empty();
    }

    private String formatToolCallsAsJsonLines(JsonArray toolCalls) {
        if (toolCalls == null || toolCalls.size() == 0) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < toolCalls.size(); i++) {
            if (!toolCalls.get(i).isJsonObject()) {
                continue;
            }
            JsonObject tc = toolCalls.get(i).getAsJsonObject();

            JsonObject call = new JsonObject();
            String name = null;
            String args = null;

            if (tc.has("function") && tc.get("function").isJsonObject()) {
                JsonObject function = tc.getAsJsonObject("function");
                if (function.has("name") && !function.get("name").isJsonNull()) {
                    name = function.get("name").getAsString();
                }
                if (function.has("arguments") && !function.get("arguments").isJsonNull()) {
                    JsonElement argElement = function.get("arguments");
                    args = argElement.isJsonPrimitive() ? argElement.getAsString() : argElement.toString();
                }
            }

            if (name == null && tc.has("name") && !tc.get("name").isJsonNull()) {
                name = tc.get("name").getAsString();
            }
            if (args == null && tc.has("arguments") && !tc.get("arguments").isJsonNull()) {
                JsonElement argElement = tc.get("arguments");
                args = argElement.isJsonPrimitive() ? argElement.getAsString() : argElement.toString();
            }

            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            call.addProperty("name", name);
            call.addProperty("arguments", args == null ? "" : args);

            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(call.toString());
        }

        return out.length() == 0 ? null : out.toString();
    }

    private OkHttpClient buildClient(String url, Settings settings) {
        ProxyResolver.ProxyResolution resolution = ProxyResolver.resolveForUrl(url, settings);

        OkHttpClient.Builder builder = baseClient.newBuilder()
                .proxy(resolution.isDirect() ? Proxy.NO_PROXY : resolution.getProxy());

        // SSL-Verifikation deaktivieren falls konfiguriert (für selbstsignierte Zertifikate)
        boolean skipSslVerify = Boolean.parseBoolean(settings.aiConfig.getOrDefault("custom.skipSslVerify", "false"));
        if (skipSslVerify) {
            try {
                javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                        new javax.net.ssl.X509TrustManager() {
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                        }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                // SSL-Override fehlgeschlagen, weiter mit Standard-SSL
            }
        }

        return builder.build();
    }
}

