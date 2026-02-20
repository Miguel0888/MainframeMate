package de.bund.zrb.mcpserver.transport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Reads JSON-RPC messages line-by-line from stdin, writes responses to a dedicated
 * protocol output stream. All diagnostic output goes to stderr.
 *
 * <p><b>Important:</b> Before starting the transport, the caller must capture the
 * original {@code System.out} and redirect {@code System.out} to {@code System.err}
 * so that WD4J internal prints never pollute the protocol channel.</p>
 */
public class StdioTransport {

    private final BufferedReader reader;
    private final PrintStream protocolOut;
    private final Gson gson;

    /**
     * @param input        stdin (or any InputStream for testing)
     * @param protocolOut  the <em>original</em> stdout captured before redirection
     */
    public StdioTransport(InputStream input, PrintStream protocolOut) {
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.protocolOut = protocolOut;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    /**
     * Blocking read of the next JSON-RPC message (one JSON object per line).
     *
     * @return parsed message or {@code null} on EOF
     */
    public JsonRpcMessage readMessage() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                return gson.fromJson(line, JsonRpcMessage.class);
            } catch (Exception e) {
                System.err.println("[MCP] Failed to parse incoming message: " + e.getMessage());
            }
        }
        return null; // EOF
    }

    /**
     * Writes a JSON-RPC response/notification as a single line to the protocol stream.
     */
    public synchronized void writeMessage(JsonRpcMessage message) {
        String json = gson.toJson(message);
        protocolOut.println(json);
        protocolOut.flush();
    }

    /**
     * Sends a JSON-RPC error response.
     */
    public void sendError(JsonElement id, int code, String msg) {
        writeMessage(JsonRpcMessage.errorResponse(id, code, msg));
    }

    /**
     * Sends a JSON-RPC success response.
     */
    public void sendResult(JsonElement id, JsonElement result) {
        writeMessage(JsonRpcMessage.successResponse(id, result));
    }
}

