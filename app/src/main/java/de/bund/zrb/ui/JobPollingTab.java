package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.newApi.ui.FtpTab;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Optional;

public class JobPollingTab implements FtpTab {

    private final JPanel panel = new JPanel(new BorderLayout());
    private final JLabel statusLabel = new JLabel("⏳ Warte auf Datei...");
    private final JButton reloadButton = new JButton("🔄 Neu laden");

    private final FtpManager ftpManager;
    private final String path;
    private final String sentenceType;
    private final TabbedPaneManager tabManager;

    public JobPollingTab(FtpManager ftpManager, TabbedPaneManager tabManager, String path, String sentenceType) {
        this.ftpManager = ftpManager;
        this.path = path;
        this.sentenceType = sentenceType;
        this.tabManager = tabManager;

        initUI();
        attemptLoad();
    }

    private void initUI() {
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(Box.createVerticalStrut(80));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        reloadButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(statusLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(reloadButton);

        panel.add(centerPanel, BorderLayout.CENTER);

        reloadButton.addActionListener(e -> attemptLoad());
    }

    private void attemptLoad() {
        try {
            FtpFileBuffer buffer = ftpManager.open(path);
            if (buffer != null) {
                FileTabImpl realTab = new FileTabImpl(tabManager, ftpManager, buffer, sentenceType);
                tabManager.replaceTab(this, realTab);
            } else {
                statusLabel.setText("❌ Datei noch nicht vorhanden");
            }
        } catch (IOException ex) {
            statusLabel.setText("⚠ Fehler beim Laden: " + ex.getMessage());
        }
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public String getContent() {
        return "";
    }

    @Override
    public void markAsChanged() {

    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public Type getType() {
        return null;
    }

    @Override
    public String getTitle() {
        return "[Warte auf Datei]";
    }

    @Override
    public String getTooltip() {
        return path;
    }

    @Override
    public void onClose() {
        // Kein Cleanup nötig
    }

    @Override
    public void saveIfApplicable() {
        // keine Datei zu speichern
    }
}
