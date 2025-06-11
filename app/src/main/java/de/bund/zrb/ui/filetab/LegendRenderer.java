package de.bund.zrb.ui.filetab;

import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.bund.zrb.helper.SettingsHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class LegendRenderer {

    public JPanel renderLegend(SentenceDefinition def, int rowIndex) {
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        for (Map.Entry<FieldCoordinate, SentenceField> entry : def.getFields().entrySet()) {
            FieldCoordinate coord = entry.getKey();
            SentenceField field = entry.getValue();

            if (coord.getRow() - 1 != rowIndex) continue;

            JLabel label = new JLabel(field.getName());
            label.setOpaque(true);
            label.setBackground(getColorFor(field));
            label.setForeground(Color.BLACK);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.DARK_GRAY),
                    BorderFactory.createEmptyBorder(2, 4, 2, 4)
            ));

            legendPanel.add(label);
        }

        return legendPanel;
    }

    private Color getColorFor(SentenceField field) {
        String name = field.getName();
        String override = SettingsHelper.load().fieldColorOverrides.get(name.toUpperCase());
        try {
            if (override != null) return Color.decode(override);
            if (field.getColor() != null && !field.getColor().isEmpty()) return Color.decode(field.getColor());
        } catch (NumberFormatException ignored) {}

        int hash = Math.abs(name.hashCode());
        float hue = (hash % 360) / 360f;
        return Color.getHSBColor(hue, 0.5f, 0.85f);
    }
}
