package de.bund.zrb.ui.commands;

import de.bund.zrb.BuildInfo;
import de.bund.zrb.ui.branding.IconThemeInstaller;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;

public class ShowAboutDialogMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;

    public ShowAboutDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.about";
    }

    @Override
    public String getLabel() {
        return "ℹ Über MainframeMate";
    }

    @Override
    public void perform() {
        // Use the largest available app logo instead of the default info icon
        ImageIcon logoIcon = null;
        Image appIcon = IconThemeInstaller.getAppIcon(1024);
        if (appIcon == null) {
            appIcon = IconThemeInstaller.getAppIcon(256);
        }
        if (appIcon != null) {
            // Scale to a nice display size for the dialog (128px)
            Image scaled = appIcon.getScaledInstance(128, 128, Image.SCALE_SMOOTH);
            logoIcon = new ImageIcon(scaled);
        }

        // --- Build the about panel ---
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Logo
        if (logoIcon != null) {
            JLabel logoLabel = new JLabel(logoIcon);
            logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(logoLabel);
            panel.add(Box.createVerticalStrut(12));
        }

        // App name + version + copyright
        JLabel nameLabel = new JLabel("MainframeMate");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(nameLabel);

        JLabel versionLabel = new JLabel("Version " + BuildInfo.getVersion());
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(versionLabel);

        JLabel copyrightLabel = new JLabel("© 2026 GZD");
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(copyrightLabel);

        panel.add(Box.createVerticalStrut(16));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(8));

        // MIT License text
        String licenseText =
                "MIT License\n\n" +
                "Copyright (c) 2026 GZD\n\n" +
                "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                "of this software and associated documentation files (the \"Software\"), to deal\n" +
                "in the Software without restriction, including without limitation the rights\n" +
                "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
                "copies of the Software, and to permit persons to whom the Software is\n" +
                "furnished to do so, subject to the following conditions:\n\n" +
                "The above copyright notice and this permission notice shall be included in all\n" +
                "copies or substantial portions of the Software.\n\n" +
                "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                "SOFTWARE.";

        JTextArea licenseArea = new JTextArea(licenseText);
        licenseArea.setEditable(false);
        licenseArea.setLineWrap(true);
        licenseArea.setWrapStyleWord(true);
        licenseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        licenseArea.setBackground(panel.getBackground());
        licenseArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane scrollPane = new JScrollPane(licenseArea);
        scrollPane.setPreferredSize(new Dimension(520, 200));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Lizenz"));
        scrollPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(scrollPane);

        JOptionPane.showMessageDialog(parent,
                panel,
                "Über MainframeMate",
                JOptionPane.PLAIN_MESSAGE);
    }
}
