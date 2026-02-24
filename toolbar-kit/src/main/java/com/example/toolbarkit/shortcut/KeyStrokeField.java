package com.example.toolbarkit.shortcut;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Capture a keystroke and render it as text.
 */
public class KeyStrokeField extends JTextField {

    private KeyStroke keyStroke;

    public KeyStrokeField(KeyStroke initial) {
        super(20);
        setEditable(false);

        this.keyStroke = initial;
        if (initial != null) {
            setText(toText(initial));
        }

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                keyStroke = KeyStroke.getKeyStrokeForEvent(e);
                setText(toText(keyStroke));
            }
        });
    }

    public KeyStroke getKeyStroke() {
        return keyStroke;
    }

    public void clear() {
        setText("");
        keyStroke = null;
    }

    private String toText(KeyStroke ks) {
        if (ks == null) return "";
        return ks.toString().replace("pressed ", "");
    }
}
