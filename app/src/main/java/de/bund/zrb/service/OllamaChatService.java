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

    // Gson-basiertes Parsen des JSON-Zeile
    private String extractResponse(String jsonLine) {
    try {
        JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();
        if (obj.has("response") && !obj.get("response").isJsonNull()) {
            return obj.get("response").getAsString();
        }
    } catch (Exception ignored) {}
    return null;
}


    @Override
    public void streamAnswer(String prompt, ChatStreamListener listener, boolean keepAlive) throws IOException {
        Settings settings = SettingsManager.load();
        String url = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault("ollama.model", modelDefault);

        Gson gson = new Gson();
        String jsonPayload = gson.toJson(new OllamaRequest(model, prompt, keepAlive));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonPayload, MediaType.get("application/json")))
                .build();

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
                            listener.onStreamChunk(chunk);
                        }
                    }

                    listener.onStreamEnd();

                } catch (IOException e) {
                    listener.onError(e);
                }
            }
        });
    }


    // Request-DTO für das JSON
    private static class OllamaRequest {
        String model;
        String prompt;
        boolean stream = true;
        Object keep_alive; // String oder boolean zulässig

        OllamaRequest(String model, String prompt, boolean keepAlive) {
            this.model = model;
            this.prompt = prompt;
            this.keep_alive = keepAlive ? "30m" : false;
        }
    }

}
