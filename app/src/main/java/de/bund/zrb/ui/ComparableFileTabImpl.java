package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.service.FileContentService;
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
        if(SettingsHelper.load().compareByDefault)
        {
            showComparePanel(); // Beim Öffnen den Vergleich anzeigen
        }
    }

    private void initCompareUI(FtpFileBuffer buffer) {
        // Original laden (nur lesend)
        FileContentService service = new FileContentService(ftpManager);
        String originalText = service.decodeWith(buffer);

        originalArea.setText(originalText);
        originalArea.setEditable(false);
        originalArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        originalArea.setLineWrap(false);

        RTextScrollPane scrollPane = new RTextScrollPane(originalArea);
        scrollPane.setFoldIndicatorEnabled(false);
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

}
