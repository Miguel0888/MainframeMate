package de.bund.zrb.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatService;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.Consumer;

public class OllamaChatService implements ChatService {

    public static final String DEBUG_MODEL = "custom-modell";
    public static final String DEBUG_URL = "http://localhost:11434/api/generate";

    private final String apiUrlDefault;
    private final String modelDefault;
    private final OkHttpClient client;
    private final Gson gson;

    public OllamaChatService() {
        this(DEBUG_URL, DEBUG_MODEL);
    }

    public OllamaChatService(String apiUrlDefault) {
        this(apiUrlDefault, DEBUG_MODEL);
    }

    public OllamaChatService(String apiUrlDefault, String modelDefault) {
        this.apiUrlDefault = apiUrlDefault;
        this.modelDefault = modelDefault;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    @Override
    public void streamAnswer(String prompt, Consumer<String> onChunk) throws IOException {
        Settings settings = SettingsManager.load();
        String url = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault("ollama.model", modelDefault);

        // JSON-Request-Body mit Gson
        String jsonPayload = gson.toJson(new OllamaRequest(model, prompt));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace(); // Oder Callback für Fehler, falls du ChatStreamListener nutzt
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("Unsuccessful response: " + response.code());
                    }

                    BufferedReader reader = new BufferedReader(body.charStream());
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String chunk = extractResponse(line);
                        if (chunk != null && !chunk.isEmpty()) {
                            onChunk.accept(chunk);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Gson-basiertes Parsen des JSON-Zeile
    private String extractResponse(String jsonLine) {
        try {
            JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
            if (obj.has("response") && !obj.get("response").isJsonNull()) {
                return obj.get("response").getAsString();
            }
        } catch (Exception e) {
            // Optional: Logging bei Parsing-Fehlern
        }
        return null;
    }

    // Request-DTO für das JSON
    private static class OllamaRequest {
        final String model;
        final String prompt;
        final boolean stream = true;

        OllamaRequest(String model, String prompt) {
            this.model = model;
            this.prompt = prompt;
        }
    }
}
