package de.bund.zrb.ui.filetab;

import de.zrb.bund.newApi.sentence.SentenceDefinition;

import javax.swing.*;
import java.awt.*;

public class LegendController {
    private final LegendRenderer renderer = new LegendRenderer();
    private final JPanel wrapper;
    private SentenceDefinition definition;
    private int schemaLines = 1;
    private int currentRow = 0;

    public LegendController(JPanel wrapper) {
        this.wrapper = wrapper;
    }

    public void setDefinition(SentenceDefinition def) {
        this.definition = def;
        this.schemaLines = def.getRowCount() != null ? def.getRowCount() : 1;
        updateLegend(0);
    }

    public void updateLegendForCaret(int lineIndex) {
        int row = lineIndex % schemaLines;
        if (row != currentRow) {
            currentRow = row;
            updateLegend(row);
        }
    }

    private void updateLegend(int row) {
        wrapper.removeAll();
        wrapper.add(renderer.renderLegend(definition, row), BorderLayout.CENTER);
        wrapper.revalidate();
        wrapper.repaint();
    }
}
