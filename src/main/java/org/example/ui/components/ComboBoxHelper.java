package org.example.ui.components;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class ComboBoxHelper {

    public static <T extends Enum<T>> JComboBox<T> createComboBoxWithNullOption(Class<T> enumClass, T selectedValue, String nullLabel) {
        DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();

        // Erzeuge neues ComboBoxModel mit null-Option vorne
        JComboBox<T> comboBox = new JComboBox<>();
        comboBox.addItem(null); // null = "Automatisch" oder "Standard"
        Arrays.stream(enumClass.getEnumConstants()).forEach(comboBox::addItem);

        // Renderer mit null-Beschriftung
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                if (value == null) value = nullLabel;
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });

        comboBox.setSelectedItem(selectedValue);
        return comboBox;
    }

    public static <T extends Enum<T>> T getSelectedEnumValue(JComboBox<T> comboBox, Class<T> enumClass) {
        Object selected = comboBox.getSelectedItem();
        return enumClass.isInstance(selected) ? enumClass.cast(selected) : null;
    }
}
