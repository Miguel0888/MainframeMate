package de.bund.zrb.ui.commands;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class CommandMenuFactory {

    public static JMenuItem createMenuItem(Command command) {
        JMenuItem item = new JMenuItem(command.getLabel());
        item.addActionListener((ActionEvent e) -> command.perform());
        return item;
    }
}
