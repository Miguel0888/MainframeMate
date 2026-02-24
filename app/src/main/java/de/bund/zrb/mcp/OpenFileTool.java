package de.bund.zrb.mcp;

import com.google.gson.JsonObject;
import de.bund.zrb.files.path.VirtualResourceRef;
import de.bund.zrb.ui.VirtualResource;
import de.bund.zrb.ui.VirtualResourceResolver;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.McpToolResponse;
import de.zrb.bund.newApi.mcp.ToolSpec;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class OpenFileTool implements McpTool {

    private final MainframeContext context;

    public OpenFileTool(MainframeContext context) {
        this.context = context;
    }

    @Override
    public ToolSpec getSpec() {
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        properties.put("file", new ToolSpec.Property("string",
                "Pfad zur Ressource (Datei, Verzeichnis, Mail, NDV-Quelle). "
                + "Akzeptiert: lokale Pfade (C:\\...), FTP-Pfade (ftp:/...), "
                + "oder URIs mit Prefix (local://..., ftp://..., mail://..., ndv://...) "
                + "wie sie von search_index zur\u00fcckgegeben werden."));
        properties.put("satzart", new ToolSpec.Property("string", "Optionaler Satzartenschl\u00fcssel"));
        properties.put("search", new ToolSpec.Property("string", "Optionaler Suchausdruck"));
        properties.put("toCompare", new ToolSpec.Property("boolean", "Ob im Vergleichsmodus ge\u00f6ffnet werden soll"));

        ToolSpec.InputSchema inputSchema = new ToolSpec.InputSchema(properties, Collections.singletonList("file"));

        Map<String, Object> example = new LinkedHashMap<>();
        example.put("file", "local://C:\\TEST\\datei.txt");
        example.put("satzart", "100");
        example.put("search", "BEGIN");
        example.put("toCompare", true);

        return new ToolSpec(
                "open_resource",
                "\u00d6ffnet eine Ressource (Datei, Verzeichnis, Mail, NDV-Quelle) als neuen Tab. " +
                "Akzeptiert Pfade mit Prefix (local://, ftp:, mail://, ndv://) " +
                "wie sie von search_index zur\u00fcckgegeben werden, sowie direkte lokale/FTP-Pfade.",
                inputSchema,
                example
        );
    }


    @Override
    public McpToolResponse execute(JsonObject input, String resultVar) {
        JsonObject response = new JsonObject();

        try {
            if (input == null || !input.has("file") || input.get("file").isJsonNull()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Pflichtfeld fehlt: file");
                return new McpToolResponse(response, resultVar, null);
            }

            String file = input.get("file").getAsString();
            String satzart = input.has("satzart") && !input.get("satzart").isJsonNull()
                    ? input.get("satzart").getAsString() : null;
            String search = input.has("search") && !input.get("search").isJsonNull()
                    ? input.get("search").getAsString() : null;
            Boolean toCompare = input.has("toCompare") && !input.get("toCompare").isJsonNull()
                    ? input.get("toCompare").getAsBoolean() : null;

            // First resolve to check what kind of resource this is (for response metadata)
            // Skip resolve for mail:// and ndv:// – these are handled directly by MainFrame
            VirtualResourceRef ref = VirtualResourceRef.of(file);
            VirtualResource resource = null;
            if (!ref.isMailPath() && !ref.isNdvPath()) {
                VirtualResourceResolver resolver = new VirtualResourceResolver();
                resource = resolver.resolve(file);
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.TOOL).fine("[open_resource] resolved: " + resource.getResolvedPath() + " kind=" + resource.getKind());
            } else {
                de.bund.zrb.util.AppLogger.get(de.bund.zrb.util.AppLogger.TOOL).fine("[open_resource] routing " + (ref.isMailPath() ? "MAIL" : "NDV") + " path directly");
            }

            // Open the tab via MainframeContext (handles EDT internally or delegates correctly)
            AtomicReference<FtpTab> openedTab = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();

            Runnable openAction = () -> {
                try {
                    FtpTab tab = context.openFileOrDirectory(file, satzart, search, toCompare);
                    openedTab.set(tab);
                } catch (Exception e) {
                    error.set(e);
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                openAction.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(openAction);
                } catch (Exception e) {
                    error.set(e);
                }
            }

            if (error.get() != null) {
                Exception e = error.get();
                response.addProperty("status", "error");
                response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
                return new McpToolResponse(response, resultVar, null);
            }

            if (openedTab.get() == null) {
                response.addProperty("status", "error");
                response.addProperty("message", "Tab konnte nicht ge\u00f6ffnet werden. " +
                        (resource != null && resource.isLocal() ? "Pr\u00fcfe, ob der Pfad existiert." :
                         "Pr\u00fcfe Pfad und Verbindungseinstellungen."));
                return new McpToolResponse(response, resultVar, null);
            }

            response.addProperty("status", "success");
            response.addProperty("openedFile", file);
            if (resource != null) {
                response.addProperty("resolvedPath", resource.getResolvedPath());
                response.addProperty("kind", resource.getKind().name());
                response.addProperty("local", resource.isLocal());
            } else {
                response.addProperty("resolvedPath", file);
                response.addProperty("kind", ref.isMailPath() ? "MAIL" : "NDV");
                response.addProperty("local", false);
            }
            response.addProperty("tabTitle", openedTab.get().getTitle());

            return new McpToolResponse(response, resultVar, null);
        } catch (Exception e) {
            response.addProperty("status", "error");
            response.addProperty("message", e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            return new McpToolResponse(response, resultVar, null);
        }
    }

}
