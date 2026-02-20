package de.bund.zrb.mcp.registry;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client that communicates with an external MCP server process over stdio (JSON-RPC 2.0).
 * Starts the process, sends initialize, queries tools/list, and can call tools.
 */
public class McpServerClient {

    private final McpServerConfig config;
    private Process process;
    private BufferedReader reader;
    private PrintStream writer;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile boolean running = false;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public McpServerClient(McpServerConfig config) {
        this.config = config;
    }

    /**
     * Start the MCP server process and perform the initialize handshake.
     */
    public void start() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(config.getCommand());
        if (config.getArgs() != null) {
            command.addAll(config.getArgs());
        }

        System.err.println("[McpClient] Starting: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false); // keep stderr separate
        process = pb.start();

        // Read from process stdout (protocol)
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        // Write to process stdin (protocol)
        writer = new PrintStream(process.getOutputStream(), true, "UTF-8");

        // Drain stderr in background
        final InputStream stderr = process.getErrorStream();
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    System.err.println("[MCP:" + config.getName() + "] " + line);
                }
            } catch (IOException ignored) {}
        }, "mcp-stderr-" + config.getName());
        stderrThread.setDaemon(true);
        stderrThread.start();

        running = true;

        // Send initialize
        JsonObject initParams = new JsonObject();
        initParams.addProperty("protocolVersion", "2024-11-05");
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "MainframeMate");
        clientInfo.addProperty("version", "1.0.0");
        initParams.add("clientInfo", clientInfo);
        initParams.add("capabilities", new JsonObject());

        JsonObject initResult = sendRequest("initialize", initParams);
        if (initResult == null) {
            throw new IOException("MCP server did not respond to initialize");
        }
        System.err.println("[McpClient] Initialized: " + initResult);

        // Send notifications/initialized
        sendNotification("notifications/initialized", null);
    }

    /**
     * Query the server for its tool list.
     */
    public JsonArray listTools() throws IOException {
        JsonObject result = sendRequest("tools/list", new JsonObject());
        if (result != null && result.has("tools")) {
            return result.getAsJsonArray("tools");
        }
        return new JsonArray();
    }

    /**
     * Call a tool on the server.
     */
    public JsonObject callTool(String toolName, JsonObject arguments) throws IOException {
        JsonObject params = new JsonObject();
        params.addProperty("name", toolName);
        params.add("arguments", arguments != null ? arguments : new JsonObject());
        return sendRequest("tools/call", params);
    }

    /**
     * Stop the server process.
     */
    public void stop() {
        running = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
        } catch (IOException ignored) {}
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        System.err.println("[McpClient] Stopped: " + config.getName());
    }

    public boolean isRunning() {
        return running && process != null && process.isAlive();
    }

    public McpServerConfig getConfig() {
        return config;
    }

    // ── Internal ────────────────────────────────────────────────────

    private synchronized JsonObject sendRequest(String method, JsonObject params) throws IOException {
        int id = nextId.getAndIncrement();

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        if (params != null) {
            request.add("params", params);
        }

        String line = gson.toJson(request);
        writer.println(line);
        writer.flush();

        // Read response lines until we find one with matching id
        // (simple blocking read – suitable for synchronous tool calls)
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new IOException("MCP server closed connection");
            }
            responseLine = responseLine.trim();
            if (responseLine.isEmpty()) continue;

            try {
                JsonObject response = JsonParser.parseString(responseLine).getAsJsonObject();
                // Check if this is a response (has id)
                if (response.has("id") && !response.get("id").isJsonNull()) {
                    int respId = response.get("id").getAsInt();
                    if (respId == id) {
                        if (response.has("error") && !response.get("error").isJsonNull()) {
                            JsonObject error = response.getAsJsonObject("error");
                            throw new IOException("MCP error: " + error.get("message").getAsString());
                        }
                        return response.has("result") ? response.getAsJsonObject("result") : new JsonObject();
                    }
                }
                // Not our response – might be a notification, skip
            } catch (JsonSyntaxException e) {
                System.err.println("[McpClient] Bad JSON from server: " + responseLine);
            }
        }
        throw new IOException("Timeout waiting for response to " + method);
    }

    private synchronized void sendNotification(String method, JsonObject params) {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", method);
        if (params != null) {
            notification.add("params", params);
        }
        writer.println(gson.toJson(notification));
        writer.flush();
    }
}

