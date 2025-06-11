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
        // Original laden (nur lesend)
        FileContentService service = new FileContentService(ftpManager);
        String originalText = service.decodeWith(buffer);

        initEditorSettings(originalArea, SettingsHelper.load()); // Anzeigeverhalten vom Hauptfenster übernehmen
        originalArea.setText(originalText);

        originalArea.setEditable(false);
        originalArea.setLineWrap(false);

        RTextScrollPane scrollPane = new RTextScrollPane(originalArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);

        comparePanel.add(scrollPane, BorderLayout.CENTER);
        comparePanel.setVisible(false); // Anfangs ausgeblendet

        toggleCompare.addActionListener(e -> {
            onToggle();
        });

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
        RegexFoldParser parser = new RegexFoldParser(input, null);
        originalArea.getFoldManager().setFolds(parser.getFolds(originalArea));
        originalArea.repaint();
    }


//    @Override
//    void onFilterApply(String input) {
//        // Regex-Folding anwenden, um FoldIndicator-Spacing identisch zu halten
////        RegexFoldParser parser = new RegexFoldParser(input, hasMatch -> {
////            // Kein UI-Feedback für originalArea nötig
////        });
//        RegexFoldParser parser = new RegexFoldParser(".*", null); // Alle Zeilen sichtbar
//        originalArea.getFoldManager().setFolds(parser.getFolds(originalArea));
//        originalArea.repaint();
//    }

}
