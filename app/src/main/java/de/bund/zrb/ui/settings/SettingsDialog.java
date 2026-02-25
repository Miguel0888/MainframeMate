package de.bund.zrb.ui.settings;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.categories.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Slim orchestrator that assembles all category panels into the Outlook-style settings dialog.
 * Each category is a self-contained panel that loads settings fresh and applies them independently.
 */
public class SettingsDialog {

    public static final int TAB_INDEX_MAILS = 10;
    public static final int TAB_INDEX_DEBUG = 11;

    public static void show(Component parent) {
        show(parent, 0);
    }

    public static void show(Component parent, int initialTabIndex) {
        RagSettingsPanel ragPanel = new RagSettingsPanel();

        List<SettingsCategory> categories = new ArrayList<>();
        categories.add(new GeneralSettingsPanel(parent));
        categories.add(new ColorSettingsPanel(parent));
        categories.add(new TransformSettingsPanel());
        categories.add(new FtpSettingsPanel());
        categories.add(new NdvSettingsPanel());
        categories.add(new AiSettingsPanel());
        categories.add(wrapPanel("rag", "RAG", ragPanel, ragPanel::saveToSettings));
        categories.add(new ProxySettingsPanel(parent));
        categories.add(wrapPanel("mcp", "MCP Registry", new McpRegistryPanel(), null));
        categories.add(new ToolConfigSettingsPanel());
        categories.add(new MailSettingsPanel());
        categories.add(new DebugSettingsPanel());

        List<JButton> leftButtons = new ArrayList<>();
        JButton folderBtn = new JButton("App-Ordner öffnen");
        folderBtn.addActionListener(e -> {
            try { Desktop.getDesktop().open(SettingsHelper.getSettingsFolder()); }
            catch (IOException ex) { JOptionPane.showMessageDialog(parent, "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage()); }
        });
        leftButtons.add(folderBtn);

        Window ownerWindow = (parent instanceof Window) ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        OutlookStyleSettingsDialog dlg = new OutlookStyleSettingsDialog(ownerWindow, "Einstellungen", categories, leftButtons);
        if (initialTabIndex >= 0 && initialTabIndex < categories.size()) dlg.selectCategory(initialTabIndex);
        dlg.setVisible(true);
    }

    /** Wraps a plain JPanel as a SettingsCategory with optional apply callback. */
    private static SettingsCategory wrapPanel(String id, String title, JComponent panel,
                                               java.util.function.Consumer<Settings> applier) {
        return new SettingsCategory() {
            @Override public String getId() { return id; }
            @Override public String getTitle() { return title; }
            @Override public JComponent getComponent() { return panel; }
            @Override public void apply() {
                if (applier != null) {
                    Settings s = SettingsHelper.load();
                    applier.accept(s);
                    SettingsHelper.save(s);
                }
            }
        };
    }
}
