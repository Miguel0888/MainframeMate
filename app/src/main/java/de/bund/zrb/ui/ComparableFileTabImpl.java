package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpFileBuffer;
import de.bund.zrb.ftp.FtpManager;
import de.bund.zrb.service.FileContentService;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;

public class ComparableFileTabImpl extends FileTabImpl {

    private final RSyntaxTextArea originalArea = new RSyntaxTextArea();
    private final JPanel comparePanel = new JPanel(new BorderLayout());
    private final JButton toggleCompare = new JButton("🪞 Vergleich einblenden");

    private boolean compareVisible = true;

    public ComparableFileTabImpl(TabbedPaneManager manager, FtpManager ftp, FtpFileBuffer buffer, String sentenceType) {
        super(manager, ftp, buffer, sentenceType);

        Component left = statusBar.getComponent(0); // BorderLayout.WEST
        if (left instanceof JPanel) {
            JPanel leftPanel = (JPanel) left;
            leftPanel.add(toggleCompare, 0); // Einfügen am Anfang, wenn FlowLayout es erlaubt
        }

        initCompareUI(buffer);
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
            compareVisible = !compareVisible;
            comparePanel.setVisible(compareVisible);
            toggleCompare.setText(compareVisible ? "📂 Vergleich ausblenden" : "🪞 Vergleich einblenden");
            getComponent().revalidate();
        });

        mainPanel.add(comparePanel, BorderLayout.NORTH);
    }

}
