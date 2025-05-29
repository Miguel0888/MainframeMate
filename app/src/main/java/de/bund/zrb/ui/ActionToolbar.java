package de.bund.zrb.ui;

import de.bund.zrb.ftp.FtpManager;

import javax.swing.*;

public class ActionToolbar extends JToolBar {

    public ActionToolbar(MainFrame frame) {
        setFloatable(false);

        JButton connectBtn = new JButton("🔌 Verbinden");
        connectBtn.addActionListener(e -> {
            FtpManager manager = new FtpManager();
            if (ConnectDialog.show(frame, manager)) {
                frame.getTabManager().addTab(new ConnectionTab(manager, frame.getTabManager()));
            }
        });

        JButton saveBtn = new JButton("💾 Speichern");
        saveBtn.addActionListener(e -> frame.getTabManager().saveSelectedComponent());

        add(connectBtn);
        add(saveBtn);

        // Weitere Buttons folgen später modular
    }
}
