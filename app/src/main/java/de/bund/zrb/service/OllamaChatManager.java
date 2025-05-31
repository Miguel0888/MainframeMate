package de.bund.zrb.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.RetryInterceptor;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OllamaChatManager implements ChatManager {

    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();

    public static final String DEBUG_MODEL = "custom-modell";
    public static final String DEBUG_URL = "http://localhost:11434/api/generate";

    private final String apiUrlDefault;
    private final String modelDefault;
    private final OkHttpClient client;
    private final Gson gson;

    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    public OllamaChatManager() {
        this(DEBUG_URL, DEBUG_MODEL);
    }

    public OllamaChatManager(String apiUrlDefault, String modelDefault) {
        this.apiUrlDefault = apiUrlDefault;
        this.modelDefault = modelDefault;
        this.client = new OkHttpClient.Builder()
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
    public List<String> getHistory(UUID sessionId) {
        ChatHistory history = sessionHistories.get(sessionId);
        return history != null ? history.getFormattedHistory() : Collections.emptyList();
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
        if(sessionId == null) {
            return false;
        }
        if (activeCalls.containsKey(sessionId)) {
            // Eine Anfrage läuft bereits mit dieser SessionId → keine neue starten
            return false;
        }
        Settings settings = SettingsManager.load();
        String url = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault("ollama.model", modelDefault);

        ChatHistory history;
        if(useContext) {
            history = sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
        } else {
            history = null;
        }

        String prompt = (history != null)
                ? buildPrompt(history, userInput)
                : userInput;

        String jsonPayload = gson.toJson(new OllamaRequest(model, prompt, keepAlive));
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json")))
                .build();

        Call call = client.newCall(request);
        activeCalls.put(sessionId, call); // Optional: auch bei null erlaubt, oder nicht eintragen

        listener.onStreamStart();

        call.enqueue(new Callback() {
            final StringBuilder currentBotResponse = new StringBuilder();

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
                        String chunk = extractResponse(line);
                        if (chunk != null && !chunk.isEmpty()) {
                            currentBotResponse.append(chunk);
                            listener.onStreamChunk(chunk);
                        }
                    }

                    if (history != null) {
                        history.addUserMessage(userInput);
                        history.addBotMessage(currentBotResponse.toString());
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

    private String buildPrompt(ChatHistory history, String userInput) {
        StringBuilder prompt = new StringBuilder();
        for (ChatHistory.Message msg : history.getMessages()) {
            prompt.append(msg.role.equals("user") ? "Du: " : "Bot: ");
            prompt.append(msg.content).append("\n");
        }
        prompt.append("Du: ").append(userInput);
        return prompt.toString();
    }

    private String extractResponse(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            if (obj.has("response") && !obj.get("response").isJsonNull()) {
                return obj.get("response").getAsString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static class OllamaRequest {
        String model;
        String prompt;
        boolean stream = true;
        String keep_alive;

        OllamaRequest(String model, String prompt, boolean keepAlive) {
            this.model = model;
            this.prompt = prompt;
            String keepAliveValue = SettingsManager.load().aiConfig.getOrDefault("ollama.keepalive", "10m");
            this.keep_alive = keepAlive ? keepAliveValue : "0m";
        }
    }
}
