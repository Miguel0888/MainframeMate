package de.bund.zrb.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatService;
import de.zrb.bund.api.ChatStreamListener;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OllamaChatService implements ChatService {

    public static final String DEBUG_MODEL = "custom-modell";
    public static final String DEBUG_URL = "http://localhost:11434/api/generate";

    private final String apiUrlDefault;
    private final String modelDefault;
    private final OkHttpClient client;
    private final Gson gson;

    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();

    public OllamaChatService() {
        this(DEBUG_URL, DEBUG_MODEL);
    }

    public OllamaChatService(String apiUrlDefault, String modelDefault) {
        this.apiUrlDefault = apiUrlDefault;
        this.modelDefault = modelDefault;
        this.client = new OkHttpClient();
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
    public void streamAnswer(UUID sessionId, String userInput, ChatStreamListener listener, boolean keepAlive) throws IOException {
        Settings settings = SettingsManager.load();
        String url = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault("ollama.model", modelDefault);

        boolean useContext = settings.aiConfig.getOrDefault("ollama.useContext", "true").equalsIgnoreCase("true");

        ChatHistory history = sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
        history.addUserMessage(userInput);

        String prompt = useContext ? buildPrompt(history, userInput) : userInput;

        String jsonPayload = gson.toJson(new OllamaRequest(model, prompt, keepAlive));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json")))
                .build();

        final StringBuilder currentBotResponse = new StringBuilder();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                listener.onStreamStart();

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

                    history.addBotMessage(currentBotResponse.toString());
                    listener.onStreamEnd();

                } catch (IOException e) {
                    listener.onError(e);
                }
            }
        });
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
        Object keep_alive;

        OllamaRequest(String model, String prompt, boolean keepAlive) {
            this.model = model;
            this.prompt = prompt;
            this.keep_alive = keepAlive ? "30m" : false;
        }
    }
}
