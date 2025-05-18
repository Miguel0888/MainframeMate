package org.example.ui;

import org.example.ftp.FtpFileBuffer;
import org.example.ftp.FtpManager;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.swing.undo.UndoManager;
import javax.swing.undo.CannotUndoException;

public class FileTab implements FtpTab {

    private final FtpManager ftpManager;
    private final FtpFileBuffer buffer;
    private final JPanel mainPanel = new JPanel(new BorderLayout());
    private final JTextArea textArea = new JTextArea();
    private final TabbedPaneManager tabbedPaneManager;

    private final UndoManager undoManager = new UndoManager();
    private final JButton undoButton = new JButton("↶");
    private final JButton redoButton = new JButton("↷");

    public FileTab(FtpManager ftpManager, TabbedPaneManager tabbedPaneManager, FtpFileBuffer buffer) {
        this.tabbedPaneManager = tabbedPaneManager;
        this.ftpManager = ftpManager;
        this.buffer = buffer;

        textArea.setText(buffer.getOriginalContent());
        textArea.getDocument().addUndoableEditListener(undoManager);
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
        JPanel statusBar = new JPanel(new BorderLayout());

        // Links: Undo/Redo Buttons
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        undoButton.setToolTipText("Änderung rückgängig machen");
        undoButton.setEnabled(false);
        undoButton.addActionListener(e -> {
            try {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            } catch (CannotUndoException ex) {
                // ignorieren
            }
            updateUndoRedoState();
        });

        redoButton.setToolTipText("Wiederherstellen");
        redoButton.setEnabled(false);
        redoButton.addActionListener(e -> {
            try {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            } catch (CannotUndoException ex) {
                // ignorieren
            }
            updateUndoRedoState();
        });

        leftPanel.add(undoButton);
        leftPanel.add(redoButton);

        // Rechts: Encoding-Auswahl
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel encodingLabel = new JLabel("Encoding:");
        JComboBox<String> encodingBox = new JComboBox<>(
                SettingsManager.SUPPORTED_ENCODINGS.toArray(new String[0])
        );
        encodingBox.setSelectedItem(SettingsManager.load().encoding);
        encodingBox.addActionListener(e -> {
            String selected = (String) encodingBox.getSelectedItem();
            ftpManager.getClient().setControlEncoding(selected);
            Charset charset = Charset.forName(selected);
            textArea.setText(buffer.decodeWith(charset));
        });
        rightPanel.add(encodingLabel);
        rightPanel.add(encodingBox);

        // Listener registrieren, um Buttons aktuell zu halten
        textArea.getDocument().addUndoableEditListener(e -> updateUndoRedoState());

        statusBar.add(leftPanel, BorderLayout.WEST);
        statusBar.add(rightPanel, BorderLayout.EAST);
        return statusBar;
    }

    private void updateUndoRedoState() {
        undoButton.setEnabled(undoManager.canUndo());
        redoButton.setEnabled(undoManager.canRedo());
    }




    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Plugin-Management
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void setContent(String newText) {
        textArea.selectAll();
        textArea.replaceSelection(""); // erlaubt Undo des Löschens
        textArea.append(newText);      // erlaubt Undo des Einfügens
        updateUndoRedoState();
    }

    public void resetUndoHistory() {
        undoManager.discardAllEdits();
        updateUndoRedoState();
    }

    //ToDo: Mit Hashing kombinieren
    private boolean changed = false; // wird aber sowieso beim speichern geprüft mittels hashWert

    public void markAsChanged() {
        this.changed = true;
        // Optional: Tab-Titel mit Stern markieren
        updateTabTitle();
    }

    private void updateTabTitle() {
        int index = ((JTabbedPane) mainPanel.getParent()).indexOfComponent(mainPanel);
        if (index >= 0) {
            String title = getTitle();
            if (changed && !title.endsWith("*")) {
                title += " *";
            }
            ((JTabbedPane) mainPanel.getParent()).setTitleAt(index, title);
        }
    }

    public String getContent() {
        return textArea.getText();
    }
}
