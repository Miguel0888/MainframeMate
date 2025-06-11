package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.ui.util.RegexFoldParser;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;

public class ComparableFileTabImpl extends FileTabImpl {

    private final RSyntaxTextArea originalArea = new RSyntaxTextArea();
    private final JPanel comparePanel = new JPanel(new BorderLayout());
    private final JButton toggleCompare = new JButton("\uD83D\uDD00 Original");

    private boolean compareVisible = false;

    private boolean append = false;

    public ComparableFileTabImpl(TabbedPaneManager manager, FtpManager ftp, FtpFileBuffer buffer, String sentenceType) {
        super(manager, ftp, buffer, sentenceType);
        toggleCompare.setToolTipText(compareVisible ? "Vergleich mit dem Original beenden" : "Mit Serverinhalt vergleichen");

        Component left = statusBar.getComponent(0); // BorderLayout.WEST
        if (left instanceof JPanel) {
            JPanel leftPanel = (JPanel) left;

            // Optionaler Abstand zwischen Undo und "Original"
            leftPanel.add(Box.createHorizontalStrut(10)); // 10px Abstand
            leftPanel.add(toggleCompare); // jetzt kommt er *nach* Undo
        }

        initCompareUI(buffer);
        highlight(originalArea, sentenceType);
        if(SettingsHelper.load().compareByDefault)
        {
            showComparePanel(); // Beim Öffnen den Vergleich anzeigen
        }
    }

    private void initCompareUI(FtpFileBuffer buffer) {
        FileContentService service = new FileContentService(ftpManager);
        String originalText = service.decodeWith(buffer);
        String originalPath = buffer != null ? buffer.getLink() : "[Unbekannter Pfad]";

        initEditorSettings(originalArea, SettingsHelper.load());
        originalArea.setText(originalText);
        originalArea.setEditable(false);
        originalArea.setLineWrap(false);

        // Scrollbereich
        RTextScrollPane scrollPane = new RTextScrollPane(originalArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);

        // Statusleiste oben
        JPanel statusBarTop = new JPanel(new BorderLayout());
        JLabel infoLabel = new JLabel("Vergleich mit: " + originalPath);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton appendButton = new JButton("⬇ Übernehmen");
        appendButton.setToolTipText("Änderungen an Original anhängen");
        appendButton.addActionListener(e -> {
            this.append = true;
            onToggle(); // schließt den Vergleich
        });

        JButton overwriteButton = new JButton("⬆ Überschreiben");
        overwriteButton.setToolTipText("Original-Inhalt verwerfen (Standardverhalten)");
        overwriteButton.addActionListener(e -> {
            this.append = false;
            onToggle(); // schließt den Vergleich
        });

        rightButtons.add(appendButton);
        rightButtons.add(overwriteButton);

        statusBarTop.add(infoLabel, BorderLayout.WEST);
        statusBarTop.add(rightButtons, BorderLayout.EAST);

        // Panel zusammensetzen
        comparePanel.add(statusBarTop, BorderLayout.NORTH);
        comparePanel.add(scrollPane, BorderLayout.CENTER);
        comparePanel.setVisible(false);

        toggleCompare.addActionListener(e -> onToggle());
        mainPanel.add(comparePanel, BorderLayout.NORTH);
    }

    private void onToggle() {
        compareVisible = !compareVisible;
        comparePanel.setVisible(compareVisible);
        toggleCompare.setText(compareVisible ? "📂 Schließen" : "\uD83D\uDD00 Original");
        toggleCompare.setToolTipText(compareVisible ? "Vergleich mit dem Original beenden" : "Mit Serverinhalt vergleichen");
        getComponent().revalidate();
    }

    /**
     * Show the compare panel if it's currently hidden.
     */
    public void showComparePanel() {
        if (!compareVisible) {
            onToggle();
        }
    }

    @Override
    void onFilterApply(String input) {
        // 1. Folding wie gehabt
        RegexFoldParser parser = new RegexFoldParser(input, null);
        originalArea.getFoldManager().setFolds(parser.getFolds(originalArea));
        originalArea.repaint();

        // 2. Optional: Suchmarkierungen setzen
        originalArea.getHighlighter().removeAllHighlights();

        if (input == null || input.trim().isEmpty()) {
            highlight(originalArea, getCurrentSentenceType());
            return;
        }

        try {
            String text = originalArea.getText().toLowerCase();
            String pattern = input.toLowerCase();
            int pos = 0;
            while ((pos = text.indexOf(pattern, pos)) >= 0) {
                int end = pos + pattern.length();
                originalArea.getHighlighter().addHighlight(
                        pos,
                        end,
                        new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
                );
                pos = end;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Hervorheben im Vergleichsbereich: " + e.getMessage());
        }
    }

    public boolean isAppendEnabled() {
        return append;
    }

}
