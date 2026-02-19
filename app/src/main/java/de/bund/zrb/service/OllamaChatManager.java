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

public class OllamaChatManager implements ChatManager {

    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();

    public static final String DEBUG_MODEL = "custom-modell";
    public static final String DEBUG_URL = "http://localhost:11434/api/generate";

    private final String apiUrlDefault;
    private final String modelDefault;
    private final OkHttpClient baseClient;
    private final Gson gson;

    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    public OllamaChatManager() {
        this(DEBUG_URL, DEBUG_MODEL);
    }

    public OllamaChatManager(String apiUrlDefault, String modelDefault) {
        this.apiUrlDefault = apiUrlDefault;
        this.modelDefault = modelDefault;
        this.baseClient = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(7, 1000))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // streamt unendlich
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        this.gson = new Gson();
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

    /** Parse a single Ollama stream line. */
    private static final class OllamaChunk {
        final String text;
        final boolean done;
        final String error;

        private OllamaChunk(String text, boolean done, String error) {
            this.text = text;
            this.done = done;
            this.error = error;
        }

        static OllamaChunk empty() {
            return new OllamaChunk(null, false, null);
        }
    }

    @Override
    public boolean streamAnswer(UUID sessionId, boolean useContext, String userInput, ChatStreamListener listener, boolean keepAlive) throws IOException {
        if (sessionId == null) {
            return false;
        }
        if (activeCalls.containsKey(sessionId)) {
            // Reject concurrent requests for same session
            return false;
        }

        Settings settings = SettingsHelper.load();
        String url = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault("ollama.model", modelDefault);

        OkHttpClient client = buildClient(url, settings);

        ChatHistory history = useContext
                ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new)
                : null;

        String prompt = (history != null)
                ? buildPrompt(history, userInput)
                : userInput;

        String jsonPayload = gson.toJson(new OllamaRequest(model, prompt, keepAlive));
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json")))
                .build();

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
                        listener.onError(new IOException("Unsuccessful response: " + response.code()));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(body.charStream());
                    String line;
                    while ((line = reader.readLine()) != null) {
                        OllamaChunk chunk = extractResponse(line);

                        if (chunk.error != null && !chunk.error.trim().isEmpty()) {
                            listener.onError(new IOException("Ollama error: " + chunk.error));
                            return;
                        }

                        if (chunk.text != null && !chunk.text.isEmpty()) {
                            listener.onStreamChunk(chunk.text);
                        }

                        if (chunk.done) {
                            // Stop reading once model signals generation completed
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
        // Cancel any active call associated with the session
        Call call = activeCalls.remove(sessionId);
        if (call != null) {
            call.cancel();
        }

        // Remove associated chat history
        sessionHistories.remove(sessionId);
    }

    private String buildPrompt(ChatHistory history, String userInput) {
        return history.toPrompt(userInput);
    }

    private OllamaChunk extractResponse(String jsonLine) {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            return OllamaChunk.empty();
        }

        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();

            String error = null;
            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                error = obj.get("error").getAsString();
            }

            boolean done = obj.has("done") && !obj.get("done").isJsonNull() && obj.get("done").getAsBoolean();

            // Ollama /api/generate streaming token
            if (obj.has("response") && !obj.get("response").isJsonNull()) {
                String response = obj.get("response").getAsString();
                if (!response.isEmpty()) {
                    return new OllamaChunk(response, done, error);
                }
            }

            // Ollama /api/chat style fallback
            if (obj.has("message") && obj.get("message").isJsonObject()) {
                JsonObject message = obj.getAsJsonObject("message");
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    JsonElement content = message.get("content");
                    if (content.isJsonPrimitive()) {
                        String text = content.getAsString();
                        if (!text.isEmpty()) {
                            return new OllamaChunk(text, done, error);
                        }
                    }
                }

                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    String toolCalls = formatToolCallsAsJsonLines(message.getAsJsonArray("tool_calls"));
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        return new OllamaChunk(toolCalls, done, error);
                    }
                }
            }

            if (obj.has("tool_calls") && obj.get("tool_calls").isJsonArray()) {
                String toolCalls = formatToolCallsAsJsonLines(obj.getAsJsonArray("tool_calls"));
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    return new OllamaChunk(toolCalls, done, error);
                }
            }

            if (error != null && !error.trim().isEmpty()) {
                return new OllamaChunk(null, done, error);
            }

            if (done) {
                return new OllamaChunk(null, true, null);
            }
        } catch (Exception ignored) {
            // ignore malformed chunks
        }
        return OllamaChunk.empty();
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
        return baseClient.newBuilder()
                .proxy(resolution.isDirect() ? Proxy.NO_PROXY : resolution.getProxy())
                .build();
    }

    private static class OllamaRequest {
        String model;
        String prompt;
        boolean stream = true;
        String keep_alive;

        OllamaRequest(String model, String prompt, boolean keepAlive) {
            this.model = model;
            this.prompt = prompt;
            String keepAliveValue = SettingsHelper.load().aiConfig.getOrDefault("ollama.keepalive", "10m");
            this.keep_alive = keepAlive ? keepAliveValue : "0m";
        }
    }
}
