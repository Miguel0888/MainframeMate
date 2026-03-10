package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Installs keyboard navigation for a selectable component (JList or JTable)
 * + search field + back/forward callbacks.
 *
 * Features:
 * - ENTER on component = triggers action (same as double-click)
 * - LEFT arrow = navigate back
 * - RIGHT arrow = navigate forward
 * - UP/DOWN in search field = jump to component (last/first element)
 * - Circular navigation (bottom→top, top→bottom)
 */
public final class ListKeyboardNavigation {

    private ListKeyboardNavigation() {}

    // ─── Abstraction over JList / JTable ────────────────────────────

    /**
     * Thin abstraction so the same keyboard logic works for JList and JTable.
     */
    public interface SelectableComponent {
        int getItemCount();
        int getSelectedIndex();
        void setSelectedIndex(int index);
        void scrollToVisible(int index);
        void requestFocusInWindow();
        Component asComponent();
    }

    // ─── Adapters ───────────────────────────────────────────────────

    /** Wrap a JList into a SelectableComponent. */
    public static SelectableComponent of(final JList<?> list) {
        return new SelectableComponent() {
            @Override public int getItemCount() { return list.getModel().getSize(); }
            @Override public int getSelectedIndex() { return list.getSelectedIndex(); }
            @Override public void setSelectedIndex(int i) { list.setSelectedIndex(i); }
            @Override public void scrollToVisible(int i) { list.ensureIndexIsVisible(i); }
            @Override public void requestFocusInWindow() { list.requestFocusInWindow(); }
            @Override public Component asComponent() { return list; }
        };
    }

    /** Wrap a JTable into a SelectableComponent. */
    public static SelectableComponent of(final JTable table) {
        return new SelectableComponent() {
            @Override public int getItemCount() { return table.getRowCount(); }
            @Override public int getSelectedIndex() { return table.getSelectedRow(); }
            @Override public void setSelectedIndex(int i) {
                table.setRowSelectionInterval(i, i);
            }
            @Override public void scrollToVisible(int i) {
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
            }
            @Override public void requestFocusInWindow() { table.requestFocusInWindow(); }
            @Override public Component asComponent() { return table; }
        };
    }

    // ─── Backwards-compatible JList overload (unchanged signature) ──

    /**
     * Install full keyboard navigation for a JList.
     *
     * @param list        the file/item list
     * @param searchField the search/filter text field (may be null)
     * @param onEnter     action for Enter key (same as double-click)
     * @param onBack      action for Left arrow / back navigation (may be null)
     * @param onForward   action for Right arrow / forward navigation (may be null)
     */
    public static void install(JList<?> list, JTextField searchField,
                                Runnable onEnter, Runnable onBack, Runnable onForward) {
        install(of(list), searchField, onEnter, onBack, onForward);
    }

    // ─── JTable overload ────────────────────────────────────────────

    /**
     * Install full keyboard navigation for a JTable.
     */
    public static void install(JTable table, JTextField searchField,
                                Runnable onEnter, Runnable onBack, Runnable onForward) {
        install(of(table), searchField, onEnter, onBack, onForward);
    }

    // ─── Generic implementation ─────────────────────────────────────

    /**
     * Install full keyboard navigation for any SelectableComponent.
     */
    public static void install(final SelectableComponent comp, final JTextField searchField,
                                final Runnable onEnter, final Runnable onBack, final Runnable onForward) {

        // ── Component: ENTER, LEFT, RIGHT, circular UP/DOWN ──
        comp.asComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int size = comp.getItemCount();
                if (size == 0) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        if (comp.getSelectedIndex() >= 0 && onEnter != null) {
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
                        if (comp.getSelectedIndex() == 0) {
                            // At top → wrap to bottom
                            comp.setSelectedIndex(size - 1);
                            comp.scrollToVisible(size - 1);
                            e.consume();
                        }
                        // else: default behavior (move up)
                        break;

                    case KeyEvent.VK_DOWN:
                        if (comp.getSelectedIndex() == size - 1) {
                            // At bottom → wrap to top
                            comp.setSelectedIndex(0);
                            comp.scrollToVisible(0);
                            e.consume();
                        }
                        // else: default behavior (move down)
                        break;
                }
            }
        });

        // ── Search field: UP/DOWN jump to component ──
        if (searchField != null) {
            searchField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int size = comp.getItemCount();
                    if (size == 0) return;

                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        // Jump to first element
                        comp.setSelectedIndex(0);
                        comp.scrollToVisible(0);
                        comp.requestFocusInWindow();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        // Jump to last element
                        comp.setSelectedIndex(size - 1);
                        comp.scrollToVisible(size - 1);
                        comp.requestFocusInWindow();
                        e.consume();
                    }
                }
            });
        }
    }
}
