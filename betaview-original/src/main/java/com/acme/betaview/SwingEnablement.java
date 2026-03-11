package com.acme.betaview;

import java.awt.Component;
import java.awt.Container;

public final class SwingEnablement {

    private SwingEnablement() {
        // Prevent instantiation
    }

    public static void setEnabledRecursive(Container root, boolean enabled) {
        root.setEnabled(enabled);
        Component[] children = root.getComponents();
        for (Component child : children) {
            child.setEnabled(enabled);
            if (child instanceof Container) {
                setEnabledRecursive((Container) child, enabled);
            }
        }
    }
}