package de.bund.zrb.mcpserver;

import de.bund.zrb.mcpserver.browser.BrowserSession;
import de.bund.zrb.mcpserver.protocol.McpRouter;
import de.bund.zrb.mcpserver.tool.ToolRegistry;
import de.bund.zrb.mcpserver.tool.impl.*;
import de.bund.zrb.mcpserver.transport.JsonRpcMessage;
import de.bund.zrb.mcpserver.transport.StdioTransport;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Entry point for the WD4J MCP Server.
 *
 * <p>This server communicates over stdio using JSON-RPC 2.0 (one JSON object per line).
 * All logging goes to stderr; only protocol messages go to stdout.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 *   java -jar wd4j-mcp-server.jar [--debug]
 * </pre>
 */
public class McpServerMain {

    public static void main(String[] args) {
        // ── 1. Capture real stdout before redirecting ───────────────
        PrintStream protocolOut = System.out;

        // Redirect System.out to stderr so that WD4J's internal prints
        // (System.out.println in WDWebSocketImpl etc.) don't pollute the
        // JSON-RPC protocol channel.
        System.setOut(System.err);

        boolean debug = false;
        for (String arg : args) {
            if ("--debug".equals(arg)) {
                debug = true;
            }
        }

        if (debug) {
            System.setProperty("wd4j.log.browser", "true");
            System.setProperty("wd4j.log.websocket", "true");
        }

        System.err.println("[MCP] WD4J MCP Server starting...");

        // ── 2. Create shared browser session (initially unconnected) ─
        BrowserSession browserSession = new BrowserSession();

        // ── 3. Register all tools ───────────────────────────────────
        ToolRegistry registry = new ToolRegistry();

        // Research-mode tools
        registry.register(new ResearchNavigateTool());

        // Utility tools
        registry.register(new BrowseTypeTool());
        registry.register(new BrowseSelectTool());
        registry.register(new BrowseScrollTool());
        registry.register(new BrowserEvalTool());
        registry.register(new BrowserScreenshotTool());

        // ── 4. Set up transport + router ────────────────────────────
        StdioTransport transport = new StdioTransport(System.in, protocolOut);
        McpRouter router = new McpRouter(transport, registry, browserSession);

        // ── 5. Main read loop ───────────────────────────────────────
        System.err.println("[MCP] Ready. Waiting for JSON-RPC messages on stdin...");

        try {
            JsonRpcMessage message;
            while ((message = transport.readMessage()) != null) {
                router.handle(message);
            }
        } catch (IOException e) {
            System.err.println("[MCP] IO error: " + e.getMessage());
        }

        // ── 6. Cleanup ─────────────────────────────────────────────
        System.err.println("[MCP] Shutting down...");
        try {
            browserSession.close();
        } catch (Exception e) {
            System.err.println("[MCP] Error during shutdown: " + e.getMessage());
        }
        System.err.println("[MCP] Bye.");
    }
}
