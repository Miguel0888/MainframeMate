package de.bund.zrb.ui;

import javax.swing.*;
import java.awt.*;

public class FeatureDialog {

    public static void show(Component parent) {
        JTextArea area = new JTextArea("Server-Features (FEAT) sind in der stateless FileService-Migration noch nicht implementiert.\n" +
                "Bitte nutze vorerst einen bestehenden FTP-Tab/Connection oder erstelle ein Follow-up-Ticket, um FEAT via CommonsNetFtpFileService abzubilden.");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(500, 200));

        JOptionPane.showMessageDialog(parent, scroll, "Server-Features", JOptionPane.INFORMATION_MESSAGE);
    }
}
