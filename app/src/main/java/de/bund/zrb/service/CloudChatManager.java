package de.bund.zrb.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.net.ProxyResolver;
import de.bund.zrb.util.RetryInterceptor;
import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CloudChatManager implements ChatManager {

    private static final String VENDOR_OPENAI = "OPENAI";
    private static final String VENDOR_CLAUDE = "CLAUDE";

    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    private final OkHttpClient baseClient;
    private final Gson gson;

    public CloudChatManager() {
        this.baseClient = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(7, 1000))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
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

    @Override
    public boolean streamAnswer(UUID sessionId, boolean useContext, String userInput, ChatStreamListener listener, boolean keepAlive) throws IOException {
        if (sessionId == null || activeCalls.containsKey(sessionId)) {
            return false;
        }

        Settings settings = SettingsHelper.load();
        String vendor = settings.aiConfig.getOrDefault("cloud.vendor", VENDOR_OPENAI).toUpperCase();
        String url = settings.aiConfig.getOrDefault("cloud.url", "https://api.openai.com/v1/chat/completions");
        String model = settings.aiConfig.getOrDefault("cloud.model", "gpt-4o-mini");
        String apiKey = settings.aiConfig.getOrDefault("cloud.apikey", "").trim();

        if (apiKey.isEmpty()) {
            listener.onError(new IOException("Cloud API Key fehlt. Bitte in den Einstellungen hinterlegen."));
            return false;
        }

        OkHttpClient client = buildClient(url, settings);
        Request request = buildRequestForVendor(sessionId, useContext, userInput, settings, vendor, url, model, apiKey);

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
                        String chunk = VENDOR_CLAUDE.equals(vendor)
                                ? extractClaudeChunk(line)
                                : extractOpenAiCompatibleChunk(line);
                        if (chunk != null && !chunk.isEmpty()) {
                            listener.onStreamChunk(chunk);
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

    private Request buildRequestForVendor(UUID sessionId,
                                          boolean useContext,
                                          String userInput,
                                          Settings settings,
                                          String vendor,
                                          String url,
                                          String model,
                                          String apiKey) {

        Request.Builder builder = new Request.Builder().url(url);
        JsonObject payload = new JsonObject();

        if (VENDOR_CLAUDE.equals(vendor)) {
            String anthropicVersion = settings.aiConfig.getOrDefault("cloud.anthropicVersion", "2023-06-01").trim();
            if (anthropicVersion.isEmpty()) {
                anthropicVersion = "2023-06-01";
            }

            payload.addProperty("model", model);
            payload.addProperty("stream", true);
            payload.addProperty("max_tokens", 1024);
            payload.add("messages", buildMessages(sessionId, useContext, userInput));

            builder.header("x-api-key", apiKey);
            builder.header("anthropic-version", anthropicVersion);
            builder.header("content-type", "application/json");
        } else {
            String authHeader = settings.aiConfig.getOrDefault("cloud.authHeader", "Authorization").trim();
            String authPrefix = settings.aiConfig.getOrDefault("cloud.authPrefix", "Bearer").trim();
            String authValue = authPrefix.isEmpty() ? apiKey : authPrefix + " " + apiKey;

            payload.addProperty("model", model);
            payload.addProperty("stream", true);
            payload.add("messages", buildMessages(sessionId, useContext, userInput));

            builder.header(authHeader.isEmpty() ? "Authorization" : authHeader, authValue.trim());

            String organization = settings.aiConfig.getOrDefault("cloud.organization", "").trim();
            if (!organization.isEmpty()) {
                builder.header("OpenAI-Organization", organization);
            }
            String project = settings.aiConfig.getOrDefault("cloud.project", "").trim();
            if (!project.isEmpty()) {
                builder.header("OpenAI-Project", project);
            }
        }

        RequestBody body = RequestBody.create(gson.toJson(payload), MediaType.get("application/json"));
        return builder.post(body).build();
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
        // no-op
    }

    @Override
    public void closeSession(UUID sessionId) {
        Call call = activeCalls.remove(sessionId);
        if (call != null) {
            call.cancel();
        }
        sessionHistories.remove(sessionId);
    }

    private JsonArray buildMessages(UUID sessionId, boolean useContext, String userInput) {
        JsonArray messages = new JsonArray();
        if (useContext) {
            ChatHistory history = sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
            for (ChatHistory.Message message : history.getMessages()) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", message.role);
                msg.addProperty("content", message.content);
                messages.add(msg);
            }
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userInput);
        messages.add(userMessage);
        return messages;
    }

    private String extractOpenAiCompatibleChunk(String line) {
        String trimmed = normalizeSseLine(line);
        if (trimmed == null || "[DONE]".equals(trimmed)) {
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
            if (!root.has("choices") || !root.get("choices").isJsonArray()) {
                return null;
            }
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices.isEmpty()) {
                return null;
            }
            JsonObject first = choices.get(0).getAsJsonObject();

            if (first.has("delta") && first.get("delta").isJsonObject()) {
                JsonObject delta = first.getAsJsonObject("delta");
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    return delta.get("content").getAsString();
                }
            }

            if (first.has("message") && first.get("message").isJsonObject()) {
                JsonObject message = first.getAsJsonObject("message");
                if (message.has("content") && !message.get("content").isJsonNull()) {
                    JsonElement content = message.get("content");
                    if (content.isJsonPrimitive()) {
                        return content.getAsString();
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private String extractClaudeChunk(String line) {
        String trimmed = normalizeSseLine(line);
        if (trimmed == null || "[DONE]".equals(trimmed)) {
            return null;
        }

        try {
            JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            if ("content_block_delta".equals(type) && root.has("delta") && root.get("delta").isJsonObject()) {
                JsonObject delta = root.getAsJsonObject("delta");
                if (delta.has("text") && !delta.get("text").isJsonNull()) {
                    return delta.get("text").getAsString();
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private String normalizeSseLine(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("event:")) {
            return null;
        }
        if (trimmed.startsWith("data:")) {
            trimmed = trimmed.substring("data:".length()).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OkHttpClient buildClient(String url, Settings settings) {
        ProxyResolver.ProxyResolution resolution = ProxyResolver.resolveForUrl(url, settings);
        return baseClient.newBuilder()
                .proxy(resolution.isDirect() ? Proxy.NO_PROXY : resolution.getProxy())
                .build();
    }
}
