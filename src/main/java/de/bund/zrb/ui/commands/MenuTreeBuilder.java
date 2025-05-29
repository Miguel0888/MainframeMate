package de.bund.zrb.ui.commands;

import javax.swing.*;
import java.util.*;

public class MenuTreeBuilder {

    private static final Map<String, String> labelMap = new HashMap<>();

    static {
        labelMap.put("file", "Datei");
        labelMap.put("settings", "Einstellungen");
        labelMap.put("help", "Hilfe");
        labelMap.put("plugin", "Plugins");
        labelMap.put("connect", "Verbindung");
        labelMap.put("save", "Speichern");
        labelMap.put("about", "Über");
        // Optional: Rest nach Bedarf ergänzen
    }

    private static String resolveLabel(String part) {
        return labelMap.getOrDefault(part, capitalize(part));
    }

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
        for (int i = 0; i < path.length; i++) {
            String part = path[i];
            current = current.children.computeIfAbsent(part, k -> new Node());
        }
        current.command = command;
    }

    private static JMenu buildMenu(Node node, String label) {
        if (node.command != null) {
            // Sonderfall: Command liegt auf Menüebene, selten, aber möglich
            JMenu menu = new JMenu(resolveLabel(label));
            menu.add(CommandMenuFactory.createMenuItem(node.command));
            return menu;
        }

        JMenu menu = new JMenu(resolveLabel(label));

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

    private static String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static class Node {
        Map<String, Node> children = new LinkedHashMap<>();
        Command command = null;
    }
}
