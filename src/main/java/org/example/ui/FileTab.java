package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class FileTab implements FtpTab {

    private final FtpManager ftpManager;
    private final FtpFileBuffer buffer;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final JTextArea textArea = new JTextArea();
    private final TabbedPaneManager tabbedPaneManager;

    public FileTab(FtpManager ftpManager, TabbedPaneManager tabbedPaneManager, FtpFileBuffer buffer) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.buffer = buffer;

        textArea.setText(buffer.getOriginalContent());
        JScrollPane scroll = new JScrollPane(textArea);

        JPanel statusBar = createStatusBar();

        mainPanel.add(scroll, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    @Override
    public String getTitle() {
        return "📄 " + buffer.getMeta().getName();
    }

    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @Override
    public void saveIfApplicable() {
        try {
            boolean ok = ftpManager.storeFile(buffer, textArea.getText());
            if (!ok) {
                JOptionPane.showMessageDialog(mainPanel, "Datei wurde verändert!\nSpeichern abgebrochen.", "Konflikt", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainPanel, "Fehler beim Speichern:\n" + e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void onClose() {
        // evtl. Änderungen prüfen, Ressourcen freigeben
    }

    @Override
    public JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem saveItem = new JMenuItem("💾 Speichern");
        saveItem.addActionListener(e -> saveIfApplicable());

        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());

        menu.add(saveItem);
        menu.add(closeItem);
        return menu;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel encodingLabel = new JLabel("Encoding:");
        JComboBox<String> encodingBox = new JComboBox<>(new String[]{
                "UTF-8", "ISO-8859-1", "Cp1047", "Cp037", "IBM01140"
        });

        encodingBox.setSelectedItem("ISO-8859-1");
        encodingBox.addActionListener(e -> {
            String selectedEncoding = (String) encodingBox.getSelectedItem();
            ftpManager.getClient().setControlEncoding(selectedEncoding);
            // Reload would go here (falls du live umschalten willst)
        });

        statusBar.add(encodingLabel);
        statusBar.add(encodingBox);
        return statusBar;
    }
}
