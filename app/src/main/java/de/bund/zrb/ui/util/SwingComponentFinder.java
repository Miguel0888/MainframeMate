package de.bund.zrb.ui.util;

import java.awt.*;

public class SwingComponentFinder {

    /**
     * Search recursively for a component by name within the container hierarchy.
     *
     * @param root  the container to search in
     * @param name  the name to search for
     * @return the found component or null if not found
     */
    public static Component findByName(Container root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }

        for (Component child : root.getComponents()) {
            if (child instanceof Container) {
                Component found = findByName((Container) child, name);
                if (found != null) {
                    return found;
                }
            } else {
                if (name.equals(child.getName())) {
                    return child;
                }
            }
        }

        return null;
    }
}

