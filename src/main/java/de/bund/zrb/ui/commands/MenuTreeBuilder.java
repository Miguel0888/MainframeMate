package de.bund.zrb.ui.commands;

import javax.swing.*;
import java.util.*;

public class MenuTreeBuilder {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("menu", Locale.getDefault());

    public static JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        Node root = new Node();

        // Schritt 1: Baumstruktur aus Command-IDs aufbauen
        for (Command command : CommandRegistry.getAll()) {
            insert(root, command.getId().split("\\."), command);
        }

        // Schritt 2: Rekursiv Menüstruktur aus dem Baum erzeugen
        root.children.forEach((key, child) -> {
            JMenu menu = buildMenu(child, key);
            if (menu != null) {
                menuBar.add(menu);
            }
        });

        return menuBar;
    }

    private static void insert(Node current, String[] path, Command command) {
        for (String part : path) {
            current = current.children.computeIfAbsent(part, k -> new Node());
        }
        current.command = command;
    }

    private static JMenu buildMenu(Node node, String labelKey) {
        if (node.command != null) {
            JMenu menu = new JMenu(resolveLabel(labelKey));
            menu.add(CommandMenuFactory.createMenuItem(node.command));
            return menu;
        }

        JMenu menu = new JMenu(resolveLabel(labelKey));

        List<String> sortedKeys = new ArrayList<>(node.children.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            Node child = node.children.get(key);
            if (child.command != null && child.children.isEmpty()) {
                menu.add(CommandMenuFactory.createMenuItem(child.command));
            } else {
                menu.add(buildMenu(child, key));
            }
        }

        return menu;
    }

    private static String resolveLabel(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return capitalize(key);
        }
    }

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static class Node {
        Map<String, Node> children = new LinkedHashMap<>();
        Command command = null;
    }
}
