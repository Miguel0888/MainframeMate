package com.acme.betaview;

import javax.swing.SwingUtilities;

public final class App {

    public static void main(String[] args) {
        final BetaViewAppProperties props = BetaViewAppPropertiesLoader.load();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new BetaViewSwingFrame(props).setVisible(true);
            }
        });
    }
}