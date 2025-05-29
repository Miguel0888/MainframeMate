package de.bund.zrb.ui.commands;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;

public class MenuBuilder {

    public static JMenu createMenu(String title, Predicate<Command> filter) {
        JMenu menu = new JMenu(title);

        Collection<Command> commands = CommandRegistry.getAll();
        commands.stream()
                .filter(filter)
                .sorted(Comparator.comparing(Command::getLabel)) // alphabetisch
                .forEach(cmd -> menu.add(CommandMenuFactory.createMenuItem(cmd)));

        return menu;
    }
}
