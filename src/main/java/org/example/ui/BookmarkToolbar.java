package org.example.ui;

import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class BookmarkToolbar extends JPanel {

    public BookmarkToolbar(Consumer<String> onBookmarkClick) {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createTitledBorder("Bookmarks"));

        Settings settings = SettingsManager.load();
        settings.bookmarks.forEach((path, label) -> {
            JButton button = new JButton(label);
            button.setToolTipText(path);
            button.addActionListener(e -> onBookmarkClick.accept(path));
            add(button);
        });
    }
}
