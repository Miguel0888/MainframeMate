package de.bund.zrb.service;

import de.bund.zrb.model.Settings;
import de.bund.zrb.util.SettingsManager;
import de.zrb.bund.api.ChatService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class OllamaChatService implements ChatService {

    public static final String DEBUG_MODEL = "custom-modell";
    public static final String DEBUG_URL = "http://localhost:11434/api/generate";
    private final String apiUrlDefault;
    private final String modelDefault;

    public OllamaChatService() {
        this(DEBUG_URL, DEBUG_MODEL);
    }

    public OllamaChatService(String apiUrlDefault) {
        this(apiUrlDefault, DEBUG_MODEL);
    }

    public OllamaChatService(String apiUrlDefault, String modelDefault) {
        this.apiUrlDefault = apiUrlDefault; // z.B. http://localhost:11434/api/generate
        this.modelDefault = modelDefault;
    }

    @Override
    public void streamAnswer(String prompt, Consumer<String> onChunk) throws IOException {
        System.out.println("#################################################");
        Settings settings = SettingsManager.load(); // hole aktuelle Settings

        String urlValue = settings.aiConfig.getOrDefault("ollama.url", apiUrlDefault);
        String model = settings.aiConfig.getOrDefault( "ollama.model", modelDefault);

        URL url = new URL(urlValue);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String payload = String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":true}",
                escapeJson(model), escapeJson(prompt)
        );

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes("UTF-8"));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String chunk = extractResponse(line);
                if (chunk != null && !chunk.isEmpty()) {
                    onChunk.accept(chunk);
                }
            }
        }
    }


    // Extrahiere das "response"-Feld aus jeder JSON-Zeile
    private String extractResponse(String jsonLine) {
        int start = jsonLine.indexOf("\"response\":\"");
        if (start == -1) {
            return null;
        }
        start += "\"response\":\"".length();
        int end = jsonLine.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        return jsonLine.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
