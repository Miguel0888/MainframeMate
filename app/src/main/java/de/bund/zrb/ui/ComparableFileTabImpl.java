package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.service.FileContentService;
import de.bund.zrb.ui.util.RegexFoldParser;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;

public class ComparableFileTabImpl extends FileTabImpl {

    private final RSyntaxTextArea originalArea = new RSyntaxTextArea();
    private final JPanel comparePanel = new JPanel(new BorderLayout());
    private final JButton toggleCompare = new JButton("\uD83D\uDD00");
    private final JPanel toggleButtonWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    private boolean compareVisible = false;
    private JCheckBox appendCheckBox;

    public ComparableFileTabImpl(TabbedPaneManager manager, FtpManager ftp, FtpFileBuffer buffer, String sentenceType) {
        super(manager, ftp, buffer, sentenceType);

        // Button initialisieren
        toggleCompare.setToolTipText("Mit Serverinhalt vergleichen");
        toggleCompare.setFont(toggleCompare.getFont().deriveFont(18f));
        toggleCompare.setMargin(new Insets(0, 0, 0, 0));
        toggleCompare.setFocusable(false);

        toggleCompare.addActionListener(e -> showComparePanel());

        // Nur wenn Vergleich noch nicht sichtbar ist
        toggleButtonWrapper.setOpaque(false);
        toggleButtonWrapper.add(Box.createHorizontalStrut(10));
        toggleButtonWrapper.add(toggleCompare);

        Component left = statusBar.getComponent(0);
        if (left instanceof JPanel) {
            JPanel leftPanel = (JPanel) left;
            leftPanel.add(toggleButtonWrapper);
        }

        initCompareUI(buffer);
        highlight(originalArea, sentenceType);

        if (SettingsHelper.load().compareByDefault) {
            showComparePanel();
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

        RTextScrollPane scrollPane = new RTextScrollPane(originalArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);

        // Vergleichs-Statusleiste oben
        JPanel statusBarTop = new JPanel(new BorderLayout());
        JLabel infoLabel = new JLabel("Vergleich mit: " + originalPath);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        appendCheckBox = new JCheckBox("Anhängen");
        appendCheckBox.setSelected(append);
        appendCheckBox.setToolTipText("Wenn aktiv, wird neuer Inhalt an den Vergleich angehängt.");
        appendCheckBox.addItemListener(e -> append = appendCheckBox.isSelected());

        JButton closeButton = new JButton("\u274C"); // ❌
        closeButton.setToolTipText("Vergleich schließen");
        closeButton.setForeground(Color.RED); // rote Schrift
        closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 18f));
        closeButton.setPreferredSize(new Dimension(28, 28));
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusPainted(true);
        closeButton.setBorderPainted(true);
        closeButton.setContentAreaFilled(true);
        closeButton.addActionListener(e -> hideComparePanel());

        rightButtons.add(appendCheckBox);
        rightButtons.add(closeButton);

        statusBarTop.add(infoLabel, BorderLayout.WEST);
        statusBarTop.add(rightButtons, BorderLayout.EAST);

        comparePanel.add(statusBarTop, BorderLayout.NORTH);
        comparePanel.add(scrollPane, BorderLayout.CENTER);
        comparePanel.setVisible(false);

        mainPanel.add(comparePanel, BorderLayout.NORTH);
    }

    private void showComparePanel() {
        compareVisible = true;
        comparePanel.setVisible(true);
        toggleButtonWrapper.setVisible(false); // Nur sichtbar wenn Panel zu
        getComponent().revalidate();
    }

    private void hideComparePanel() {
        compareVisible = false;
        comparePanel.setVisible(false);
        toggleButtonWrapper.setVisible(true); // Jetzt wieder anzeigen
        getComponent().revalidate();
    }

    @Override
    void onFilterApply(String input) {
        RegexFoldParser parser = new RegexFoldParser(input, null);
        originalArea.getFoldManager().setFolds(parser.getFolds(originalArea));
        originalArea.repaint();

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
                        new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
                );
                pos = end;
            }
        } catch (Exception e) {
            System.err.println("⚠️ Fehler beim Hervorheben im Vergleichsbereich: " + e.getMessage());
        }
    }

    @Override
    public void setAppend (boolean append) {
        super.setAppend(append);
        SwingUtilities.invokeLater(() -> appendCheckBox.setSelected(append));
    }
}
