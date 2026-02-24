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
    private final Map<UUID, ToolCallAccumulator> toolCallAccumulators = new ConcurrentHashMap<>();

    private final OkHttpClient baseClient;
    private final Gson gson;

    /** Accumulate streamed tool call fragments until complete. */
    private static final class ToolCallAccumulator {

        private static final class Part {
            String name;
            final StringBuilder arguments = new StringBuilder();
        }

        private final Map<Integer, Part> partsByIndex = new java.util.HashMap<>();

        void add(int index, String namePart, String argumentsPart) {
            Part part = partsByIndex.get(index);
            if (part == null) {
                part = new Part();
                partsByIndex.put(index, part);
            }
            if (namePart != null && !namePart.trim().isEmpty() && (part.name == null || part.name.trim().isEmpty())) {
                part.name = namePart;
            }
            if (argumentsPart != null && !argumentsPart.isEmpty()) {
                part.arguments.append(argumentsPart);
            }
        }

        boolean isEmpty() {
            return partsByIndex.isEmpty();
        }

        String flushAsJsonLines() {
            if (partsByIndex.isEmpty()) {
                return null;
            }

            java.util.List<Integer> indices = new java.util.ArrayList<>(partsByIndex.keySet());
            java.util.Collections.sort(indices);

            StringBuilder out = new StringBuilder();
            for (Integer index : indices) {
                Part part = partsByIndex.get(index);
                if (part == null || part.name == null || part.name.trim().isEmpty()) {
                    continue;
                }

                JsonObject call = new JsonObject();
                call.addProperty("name", part.name);
                call.addProperty("arguments", part.arguments.toString());

                if (out.length() > 0) {
                    out.append("\n");
                }
                out.append(call.toString());
            }

            partsByIndex.clear();
            return out.length() == 0 ? null : out.toString();
        }
    }

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
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
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
                toolCallAccumulators.remove(sessionId);
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
                                : extractOpenAiCompatibleChunk(sessionId, line);
                        if (chunk != null && !chunk.isEmpty()) {
                            listener.onStreamChunk(chunk);
                        }
                    }

                    listener.onStreamEnd();
                } catch (IOException e) {
                    listener.onError(e);
                } finally {
                    activeCalls.remove(sessionId);
                    toolCallAccumulators.remove(sessionId);
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

            // Insert system prompt as first message (system role) so the model
            // always knows about available tools and mode instructions.
            String sysPrompt = history.getSystemPrompt();
            if (sysPrompt != null && !sysPrompt.trim().isEmpty()) {
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", sysPrompt.trim());
                messages.add(sysMsg);
            }

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

    private String extractOpenAiCompatibleChunk(UUID sessionId, String line) {
        String trimmed = normalizeSseLine(line);
        if (trimmed == null || "[DONE]".equals(trimmed)) {
            ToolCallAccumulator acc = toolCallAccumulators.remove(sessionId);
            if (acc != null && !acc.isEmpty()) {
                return acc.flushAsJsonLines();
            }
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

                if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                    JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
                    ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(sessionId, id -> new ToolCallAccumulator());

                    for (int i = 0; i < toolCalls.size(); i++) {
                        JsonObject tc = toolCalls.get(i).getAsJsonObject();
                        int index = tc.has("index") && !tc.get("index").isJsonNull() ? tc.get("index").getAsInt() : 0;

                        String namePart = null;
                        String argsPart = null;

                        if (tc.has("function") && tc.get("function").isJsonObject()) {
                            JsonObject fn = tc.getAsJsonObject("function");
                            if (fn.has("name") && !fn.get("name").isJsonNull()) {
                                namePart = fn.get("name").getAsString();
                            }
                            if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                                argsPart = fn.get("arguments").getAsString();
                            }
                        }

                        acc.add(index, namePart, argsPart);
                    }
                    return null;
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

                if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
                    JsonArray toolCalls = message.getAsJsonArray("tool_calls");
                    ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(sessionId, id -> new ToolCallAccumulator());

                    for (int i = 0; i < toolCalls.size(); i++) {
                        JsonObject tc = toolCalls.get(i).getAsJsonObject();

                        String name = null;
                        String args = "";
                        if (tc.has("function") && tc.get("function").isJsonObject()) {
                            JsonObject fn = tc.getAsJsonObject("function");
                            if (fn.has("name") && !fn.get("name").isJsonNull()) {
                                name = fn.get("name").getAsString();
                            }
                            if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                                args = fn.get("arguments").getAsString();
                            }
                        }

                        acc.add(i, name, args);
                    }

                    String flush = acc.flushAsJsonLines();
                    toolCallAccumulators.remove(sessionId);
                    return flush;
                }
            }

            if (first.has("finish_reason") && !first.get("finish_reason").isJsonNull()) {
                String finishReason = first.get("finish_reason").getAsString();
                if ("tool_calls".equalsIgnoreCase(finishReason)) {
                    ToolCallAccumulator acc = toolCallAccumulators.remove(sessionId);
                    if (acc != null && !acc.isEmpty()) {
                        return acc.flushAsJsonLines();
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
