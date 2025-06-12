package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class FeatureDialog {

    public static void show(Component parent) {
        final FtpManager tempManager = new FtpManager();

        String text = loadFeatures(tempManager);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(500, 300));

        JOptionPane.showMessageDialog(parent, scroll, "Server-Features", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String loadFeatures(FtpManager manager) {
        try {
            int code = manager.getClient().sendCommand("FEAT");
            if (code != 211 && code != 214) return "(FEAT nicht unterstützt)";

            StringBuilder sb = new StringBuilder();
            String[] lines = manager.getClient().getReplyStrings();
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                sb.append(line.trim()).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "Fehler beim Abrufen der Features:\n" + e.getMessage();
        }
    }
}
