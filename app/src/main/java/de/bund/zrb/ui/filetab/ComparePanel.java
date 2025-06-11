package de.bund.zrb.ui.filetab;

import de.bund.zrb.ui.filetab.event.AppendChangedEvent;
import de.bund.zrb.ui.filetab.event.CloseComparePanelEvent;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;

public class ComparePanel extends JPanel {

    private final OriginalBarPanel barPanel = new OriginalBarPanel();
    private final RSyntaxTextArea originalArea = new RSyntaxTextArea();

    public ComparePanel(String originalPath, String originalContent) {
        super(new BorderLayout());

        // Konfiguration
        originalArea.setText(originalContent);
        originalArea.setEditable(false);
        originalArea.setSyntaxEditingStyle("text/plain");
        originalArea.setCodeFoldingEnabled(true);
        originalArea.setLineWrap(false);
        originalArea.setAntiAliasingEnabled(true);
        originalArea.setHighlightCurrentLine(true);
        originalArea.setPaintTabLines(true);

        barPanel.setPathText(originalPath);

        RTextScrollPane scrollPane = new RTextScrollPane(originalArea);
        scrollPane.setFoldIndicatorEnabled(true);
        scrollPane.setLineNumbersEnabled(true);

        add(barPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setContent(String originalPath, String originalContent) {
        originalArea.setText(originalContent);
        barPanel.setPathText(originalPath);
    }

    public void setAppendSelected(boolean selected) {
        barPanel.setAppendSelected(selected);
    }

    public boolean isAppendSelected() {
        return barPanel.isAppendSelected();
    }

    public void setCloseAction(Runnable onClose) {
        barPanel.setCloseAction(onClose);
    }

    public RSyntaxTextArea getOriginalTextArea() {
        return originalArea;
    }

    public OriginalBarPanel getBarPanel() {
        return barPanel;
    }

    public void bindEvents(FileTabEventDispatcher dispatcher) {
        barPanel.setCloseAction(() -> dispatcher.publish(new CloseComparePanelEvent()));
        barPanel.onAppendChanged(append -> dispatcher.publish(new AppendChangedEvent(append)));
    }

}
