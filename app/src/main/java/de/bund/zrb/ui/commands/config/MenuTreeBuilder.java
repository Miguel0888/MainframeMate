package de.bund.zrb.ui.commands.config;

import de.bund.zrb.ui.commands.SeparatorMenuCommand;
import de.zrb.bund.api.MenuCommand;

import javax.swing.*;
import java.util.*;

public class MenuTreeBuilder {

    private static final ResourceBundle bundle = ResourceBundle.getBundle("menu", Locale.getDefault());

    // Definiert die Reihenfolge der Hauptmenüs
    private static final List<String> MENU_ORDER = Arrays.asList(
            "file", "edit", "view", "extras", "plugin", "settings", "help"
    );

    // Definiert die Reihenfolge innerhalb von Untermenüs
    private static final Map<String, List<String>> SUBMENU_ORDER = new HashMap<>();

    static {
        // Datei-Menü Reihenfolge
        SUBMENU_ORDER.put("file", Arrays.asList(
                "save", "saveAndClose", "---1", "connect", "local", "---2", "exit"
        ));
        // Bearbeiten-Menü Reihenfolge
        SUBMENU_ORDER.put("edit", Arrays.asList(
                "search", "compare", "---1", "bookmark"
        ));
        // Einstellungen-Menü Reihenfolge
        SUBMENU_ORDER.put("settings", Arrays.asList(
                "general", "server", "---1", "sentences", "expressions", "tools", "---2", "shortcuts", "plugins"
        ));
        // Hilfe-Menü Reihenfolge
        SUBMENU_ORDER.put("help", Arrays.asList(
                "features", "---1", "about"
        ));
    }

    public static JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        Node root = new Node();

        // Schritt 1: Baumstruktur aus Command-IDs aufbauen
        for (MenuCommand menuCommand : CommandRegistryImpl.getAll()) {
            insert(root, menuCommand.getId().split("\\."), menuCommand);
        }

        // Schritt 2: Rekursiv Menüstruktur aus dem Baum erzeugen (mit Reihenfolge)
        for (String menuKey : MENU_ORDER) {
            Node child = root.children.get(menuKey);
            if (child != null) {
                JMenu menu = buildMenu(child, menuKey, menuKey);
                if (menu != null && menu.getItemCount() > 0) {
                    menuBar.add(menu);
                }
            }
        }

        // Unbekannte Menüs hinzufügen (falls vorhanden)
        for (String key : root.children.keySet()) {
            if (!MENU_ORDER.contains(key)) {
                JMenu menu = buildMenu(root.children.get(key), key, key);
                if (menu != null && menu.getItemCount() > 0) {
                    menuBar.add(menu);
                }
            }
        }

        return menuBar;
    }

    private static void insert(Node current, String[] path, MenuCommand menuCommand) {
        for (String part : path) {
            current = current.children.computeIfAbsent(part, k -> new Node());
        }
        current.menuCommand = menuCommand;
    }

    private static JMenu buildMenu(Node node, String labelKey, String menuPath) {
        if (node.menuCommand != null && node.children.isEmpty()) {
            // Einzelnes Leaf-Item als Menü (selten)
            JMenu menu = new JMenu(resolveLabel(labelKey));
            if (!SeparatorMenuCommand.isSeparator(node.menuCommand)) {
                menu.add(CommandMenuFactory.createMenuItem(node.menuCommand));
            }
            return menu;
        }

        JMenu menu = new JMenu(resolveLabel(labelKey));

        // Reihenfolge für dieses Menü ermitteln
        List<String> order = SUBMENU_ORDER.getOrDefault(menuPath, null);
        List<String> sortedKeys;

        if (order != null) {
            sortedKeys = new ArrayList<>();
            // Erst geordnete Keys
            for (String key : order) {
                if (key.startsWith("---")) {
                    // Separator-Platzhalter
                    sortedKeys.add(key);
                } else if (node.children.containsKey(key)) {
                    sortedKeys.add(key);
                }
            }
            // Dann unbekannte Keys
            for (String key : node.children.keySet()) {
                if (!sortedKeys.contains(key)) {
                    sortedKeys.add(key);
                }
            }
        } else {
            sortedKeys = new ArrayList<>(node.children.keySet());
            Collections.sort(sortedKeys);
        }

        for (String key : sortedKeys) {
            if (key.startsWith("---")) {
                // Separator hinzufügen
                if (menu.getItemCount() > 0) {
                    menu.addSeparator();
                }
                continue;
            }

            Node child = node.children.get(key);
            if (child == null) continue;

            if (child.menuCommand != null && child.children.isEmpty()) {
                // Leaf: MenuItem
                if (!SeparatorMenuCommand.isSeparator(child.menuCommand)) {
                    menu.add(CommandMenuFactory.createMenuItem(child.menuCommand));
                }
            } else {
                // Branch: Untermenü
                String childPath = menuPath + "." + key;
                JMenu subMenu = buildMenu(child, key, childPath);
                if (subMenu != null && subMenu.getItemCount() > 0) {
                    menu.add(subMenu);
                }
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
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private static class Node {
        Map<String, Node> children = new LinkedHashMap<>();
        MenuCommand menuCommand = null;
    }
}
