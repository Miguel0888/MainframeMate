package de.bund.zrb.ui.commands.config;

import de.zrb.bund.api.MenuCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class CommandMenuFactory {

    public static JMenuItem createMenuItem(MenuCommand menuCommand) {
        JMenuItem item = new JMenuItem(menuCommand.getLabel());
        item.addActionListener((ActionEvent e) -> menuCommand.perform());
        return item;
    }
}
