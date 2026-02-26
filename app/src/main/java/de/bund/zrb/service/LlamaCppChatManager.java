package de.bund.zrb.service;

import com.google.gson.*;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.RetryInterceptor;
import de.bund.zrb.helper.SettingsHelper;
import de.zrb.bund.api.ChatHistory;
import de.zrb.bund.api.ChatManager;
import de.zrb.bund.api.ChatStreamListener;
import okhttp3.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class LlamaCppChatManager implements ChatManager {

    private static final Logger LOG = de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.AI);

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final Map<UUID, Call> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, ChatHistory> sessionHistories = new ConcurrentHashMap<>();
    private final Settings settings = SettingsHelper.load();

    private Process llamaProcess;

    public LlamaCppChatManager() {
        this.client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(7, 1000))
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // streamt unendlich
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        startLlamaServer();
    }

    private void startLlamaServer() {
        if (Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.enabled", "false"))) {
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
            builder.redirectErrorStream(true);

            try {
                llamaProcess = builder.start();
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(llamaProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            LOG.fine("[llama-server] " + line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            } catch (IOException e) {
                throw new RuntimeException("Fehler beim Starten von llama-server", e);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
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
        if (history != null) history.clear();
    }

    @Override
    public ChatHistory getHistory(UUID sessionId) {
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new);
    }

    @Override
    public List<String> getFormattedHistory(UUID sessionId) {
        return sessionHistories.computeIfAbsent(sessionId, ChatHistory::new).getFormattedHistory();
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
    public boolean streamAnswer(UUID sessionId, boolean useContext, String userInput,
                                ChatStreamListener listener, boolean keepAlive) throws IOException {

        if (activeCalls.containsKey(sessionId)) return false;

        // Server ggf. starten, wenn er nicht läuft
        synchronized (this) {
            if (llamaProcess == null || !llamaProcess.isAlive()) {
                startLlamaServer();
            }
        }

        String port = settings.aiConfig.getOrDefault("llama.port", "8080");
        String url = "http://localhost:" + port + "/completion";

        boolean stream = Boolean.parseBoolean(settings.aiConfig.getOrDefault("llama.streaming", "true"));
        float temp = Float.parseFloat(settings.aiConfig.getOrDefault("llama.temp", "0.7"));

        ChatHistory history = useContext ? sessionHistories.computeIfAbsent(sessionId, ChatHistory::new) : null;
        String prompt = history != null ? buildPrompt(history, userInput) : userInput;

        LlamaRequest requestPayload = new LlamaRequest("<s>[INST] " + prompt + " [/INST]", temp, stream);
        String jsonBody = gson.toJson(requestPayload);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("LLAMA REQUEST to " + url);
            LOG.fine(jsonBody);
            LOG.fine("END REQUEST");
        }

        Call call = client.newCall(request);
        activeCalls.put(sessionId, call);
        listener.onStreamStart();

        call.enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                activeCalls.remove(sessionId);
                listener.onError(e);
                if (!keepAlive) shutdown(); // Server bei Fehler trotzdem stoppen
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
                    if (stream) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;

                            if (line.startsWith("data:")) {
                                line = line.substring("data:".length()).trim();
                            }

                            try {
                                LlamaStreamResponseChunk chunk = gson.fromJson(line, LlamaStreamResponseChunk.class);
                                if (chunk != null && chunk.content != null) {
                                    listener.onStreamChunk(chunk.content);
                                }
                            } catch (JsonSyntaxException e) {
                                System.err.println("Ungültige JSON-Zeile im Stream: " + line);
                            }
                        }

                    } else {
                        LlamaNonStreamResponse result = gson.fromJson(reader, LlamaNonStreamResponse.class);
                        if (result != null && result.content != null) {
                            listener.onStreamChunk(result.content);
                        }
                    }

                    listener.onStreamEnd();
                } catch (IOException e) {
                    listener.onError(e);
                } finally {
                    activeCalls.remove(sessionId);
                    if (!keepAlive) shutdown(); // Server bei Bedarf beenden
                }
            }
        });

        return true;
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

    public void shutdown() {
        if (llamaProcess != null) {
            llamaProcess.destroy();
            try {
                if (!llamaProcess.waitFor(5, TimeUnit.SECONDS)) {
                    System.err.println("llama-server reagiert nicht – wird forciert beendet.");
                    llamaProcess.destroyForcibly();
                    llamaProcess.waitFor(3, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                llamaProcess = null;
            }
        }
    }

    // Request DTO
    private static class LlamaRequest {
        String prompt;
        int n_predict = 200;
        float temperature;
        boolean stream;
        List<String> stop;

        public LlamaRequest(String prompt, float temperature, boolean stream) {
            this.prompt = prompt;
            this.temperature = temperature;
            this.stream = stream;
            this.stop = Collections.singletonList("</s>");
        }
    }

    // Response DTO für Streaming
    private static class LlamaStreamResponseChunk {
        String content;
    }

    // Response DTO für Non-Streaming
    private static class LlamaNonStreamResponse {
        String content;
    }

    @Override
    public void onDispose()
    {
        shutdown();
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


    // ToDo
    private boolean answerChunkContainsToolCall(String chunk) {
        // Implementiere Logik, um zu prüfen, ob die Antwort einen Tool-Call enthält
//        if (chunk.content.trim().startsWith("{") && chunk.content.contains("\"tool_name\"")) {
//            JsonObject toolCall = gson.fromJson(chunk.content, JsonObject.class);
//            mpcService.handleToolCall(sessionId, toolCall);
//        }

        return false; // Platzhalter
    }
}
