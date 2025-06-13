package de.zrb.bund.newApi.ui;

import de.zrb.bund.api.Bookmarkable;

import javax.swing.*;

public interface FtpTab extends Bookmarkable {
    String getTitle();
    String getTooltip();
    JComponent getComponent();
    void onClose();
    void saveIfApplicable();

    /**
     * Gibt ein Kontextmenü zurück, das vom TabbedPaneManager angezeigt werden kann.
     * Der übergebene Callback wird beim Schließen des Tabs ausgeführt.
     */
    default JPopupMenu createContextMenu(Runnable onCloseCallback) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("❌ Tab schließen");
        closeItem.addActionListener(e -> onCloseCallback.run());
        menu.add(closeItem);
        return menu;
    }

    void focusSearchField();

    void searchFor(String searchPattern);
}

