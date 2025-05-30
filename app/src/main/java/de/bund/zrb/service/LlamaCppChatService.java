package de.bund.zrb.service;

import com.google.gson.*;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatService;
import de.zrb.bund.api.ChatStreamListener;
import okhttp3.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LlamaCppChatService implements ChatService {

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();
    private final Settings settings = SettingsManager.load();

    private Process llamaProcess;

    public LlamaCppChatService() {
        if (Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false"))) {
            startLlamaServer();
        }
    }

    private void startLlamaServer() {
        String binary = settings.aiConfig.getOrDefault("llama.binary", "./llama-server");
        String model = settings.aiConfig.getOrDefault("llama.model", "C:/models/mistral.gguf");
        String port = settings.aiConfig.getOrDefault("llama.port", "8080");
        String threads = settings.aiConfig.getOrDefault("llama.threads", "4");
        String context = settings.aiConfig.getOrDefault("llama.context", "2048");

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add("-m");
        command.add(model);
        command.add("--port");
        command.add(port);
        command.add("--ctx-size");
        command.add(context);
        command.add("--threads");
        command.add(threads);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true); // combine stdout and stderr

        try {
            llamaProcess = builder.start();
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(llamaProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[llama-server] " + line); // optional: logging
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            throw new RuntimeException("Fehler beim Starten von llama-server", e);
        }

        // Optional: auf Verfügbarkeit des Ports warten
        try {
            Thread.sleep(3000); // einfacher Start-Delay
        } catch (InterruptedException ignored) {}
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
        if (history != null) history.clear();
    }

    @Override
    public List<String> getHistory(UUID sessionId) {
        return sessionHistories.getOrDefault(sessionId, new ChatHistory(sessionId)).getFormattedHistory();
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
        if (activeCalls.containsKey(sessionId)) return false;

        String port = settings.aiConfig.getOrDefault("llama.port", "8080");
        String url = "http://localhost:" + port + "/completion";

        ChatHistory history = useContext ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new) : null;
        String prompt = history != null ? buildPrompt(history, userInput) : userInput;

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("prompt", "<s>[INST] " + prompt + " [/INST]");
        requestJson.addProperty("n_predict", 200);
        requestJson.addProperty("temperature", Float.parseFloat(settings.aiConfig.getOrDefault("llama.temp", "0.7")));
        requestJson.add("stop", new Gson().toJsonTree(Arrays.asList("</s>")));
        requestJson.addProperty("stream", true);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(gson.toJson(requestJson), MediaType.get("application/json")))
                .build();

        Call call = client.newCall(request);
        activeCalls.put(sessionId, call);

        listener.onStreamStart();

        call.enqueue(new Callback() {
            final StringBuilder botResponse = new StringBuilder();

            @Override
            public void onFailure(Call call, IOException e) {
                activeCalls.remove(sessionId);
                listener.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String chunk = extractContent(line);
                        if (chunk != null && !chunk.isEmpty()) {
                            botResponse.append(chunk);
                            listener.onStreamChunk(chunk);
                        }
                    }

                    if (history != null) {
                        history.addUserMessage(userInput);
                        history.addBotMessage(botResponse.toString());
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

    private String extractContent(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            if (obj.has("content")) {
                return obj.get("content").getAsString();
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Parsen des Chunks: " + jsonLine);
            e.printStackTrace();
        }
        return null;
    }

    private String buildPrompt(ChatHistory history, String userInput) {
        StringBuilder prompt = new StringBuilder();
        for (ChatHistory.Message msg : history.getMessages()) {
            prompt.append(msg.role.equals("user") ? "Du: " : "Bot: ").append(msg.content).append("\n");
        }
        prompt.append("Du: ").append(userInput);
        return prompt.toString();
    }

    @Override
    public void cancel(UUID sessionId) {
        Call call = activeCalls.remove(sessionId);
        if (call != null) call.cancel();
    }

    // Optional: Shutdown-Methode, z. B. beim Programmende
    public void shutdown() {
        if (llamaProcess != null) {
            llamaProcess.destroy();
        }
    }
}
