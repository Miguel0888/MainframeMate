package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Installs keyboard navigation for a JList + search field + back/forward callbacks.
 *
 * Features:
 * - ENTER on list = triggers action (same as double-click)
 * - LEFT arrow on list = navigate back
 * - RIGHT arrow on list = navigate forward
 * - UP/DOWN in search field = jump to list (last/first element)
 * - Circular navigation in list (bottom→top, top→bottom)
 */
public final class ListKeyboardNavigation {

    private ListKeyboardNavigation() {}

    /**
     * Install full keyboard navigation.
     *
     * @param list        the file/item list
     * @param searchField the search/filter text field (may be null)
     * @param onEnter     action for Enter key (same as double-click)
     * @param onBack      action for Left arrow / back navigation (may be null)
     * @param onForward   action for Right arrow / forward navigation (may be null)
     */
    public static void install(JList<?> list, JTextField searchField,
                                Runnable onEnter, Runnable onBack, Runnable onForward) {

        // ── List: ENTER, LEFT, RIGHT, circular UP/DOWN ──
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int size = list.getModel().getSize();
                if (size == 0) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        if (list.getSelectedIndex() >= 0 && onEnter != null) {
                            onEnter.run();
                        }
                        e.consume();
                        break;

                    case KeyEvent.VK_LEFT:
                        if (onBack != null) {
                            onBack.run();
                            e.consume();
                        }
                        break;

                    case KeyEvent.VK_RIGHT:
                        if (onForward != null) {
                            onForward.run();
                            e.consume();
                        }
                        break;

                    case KeyEvent.VK_UP:
                        if (list.getSelectedIndex() == 0) {
                            // At top → wrap to bottom
                            list.setSelectedIndex(size - 1);
                            list.ensureIndexIsVisible(size - 1);
                            e.consume();
                        }
                        // else: default JList behavior (move up)
                        break;

                    case KeyEvent.VK_DOWN:
                        if (list.getSelectedIndex() == size - 1) {
                            // At bottom → wrap to top
                            list.setSelectedIndex(0);
                            list.ensureIndexIsVisible(0);
                            e.consume();
                        }
                        // else: default JList behavior (move down)
                        break;
                }
            }
        });

        // ── Search field: UP/DOWN jump to list ──
        if (searchField != null) {
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int size = list.getModel().getSize();
                    if (size == 0) return;

                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        // Jump to first element
                        list.setSelectedIndex(0);
                        list.ensureIndexIsVisible(0);
                        list.requestFocusInWindow();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        // Jump to last element
                        list.setSelectedIndex(size - 1);
                        list.ensureIndexIsVisible(size - 1);
                        list.requestFocusInWindow();
                        e.consume();
                    }
                }
            });
        }
    }
}
