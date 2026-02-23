package de.bund.zrb.ui.commands;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.mail.model.MailboxCategory;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.TabbedPaneManager;
import de.bund.zrb.ui.mail.MailConnectionTab;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Menu command "Mail-Verbindung..." to open local Outlook mail stores (OST/PST).
 * If no path is configured, shows an initial dialog to set the mail store path.
 */
public class ConnectMailMenuCommand extends ShortcutMenuCommand {

    private final JFrame parent;
    private final TabbedPaneManager tabManager;

    public ConnectMailMenuCommand(JFrame parent, TabbedPaneManager tabManager) {
        this.parent = parent;
        this.tabManager = tabManager;
    }

    @Override
    public String getId() {
        return "file.mail";
    }

    @Override
    public String getLabel() {
        return "Mail-Verbindung...";
    }

    @Override
    public void perform() {
        Settings settings = SettingsHelper.load();

        // Apply mail container class configuration
        MailboxCategory.setMailContainerClasses(settings.mailContainerClasses);

        String path = settings.mailStorePath;

        if (path == null || path.trim().isEmpty()) {
            // Show initial dialog
            path = showInitialPathDialog();
            if (path == null) {
                return; // user cancelled
            }
            // Save to settings
            settings.mailStorePath = path;
            SettingsHelper.save(settings);
        }

        // Validate path
        File dir = new File(path);
        if (!dir.isDirectory()) {
            JOptionPane.showMessageDialog(parent,
                    "Der konfigurierte Pfad existiert nicht oder ist kein Ordner:\n" + path
                            + "\n\nBitte unter Einstellungen → Mails den Pfad korrigieren.",
                    "Ungültiger Pfad", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tabManager.addTab(new MailConnectionTab(tabManager, path));
    }

    /**
     * Shows the initial dialog to set the mail store path.
     * Returns the validated path, or null if cancelled.
     */
    private String showInitialPathDialog() {
        String defaultPath = getDefaultOutlookPath();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        // Info text
        JLabel infoLabel = new JLabel("<html><b>Bitte gib den Speicherort deiner Outlook-Datendateien an.</b></html>");
        panel.add(infoLabel, gbc);
        gbc.gridy++;

        JLabel hintLabel = new JLabel(
                "<html><small>In Outlook findest du ihn unter:<br>"
                        + "Datei → Kontoeinstellungen → Kontoeinstellungen → Datendateien</small></html>");
        hintLabel.setForeground(Color.GRAY);
        panel.add(hintLabel, gbc);
        gbc.gridy++;

        // Path input
        gbc.gridy++;
        panel.add(new JLabel("Ordner:"), gbc);
        gbc.gridy++;

        JTextField pathInput = new JTextField(defaultPath, 40);
        pathInput.setSelectionStart(0);
        pathInput.setSelectionEnd(pathInput.getText().length());
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        panel.add(pathInput, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        JButton browseButton = new JButton("📂");
        browseButton.setToolTipText("Ordner auswählen…");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(pathInput.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Mail-Speicherort auswählen");
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                pathInput.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        panel.add(browseButton, gbc);

        // Show dialog in loop until valid or cancelled
        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, panel,
                    "Mail-Speicherort", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            String inputPath = pathInput.getText().trim();
            if (inputPath.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Bitte einen Pfad eingeben.",
                        "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            File dir = new File(inputPath);
            if (!dir.isDirectory()) {
                JOptionPane.showMessageDialog(parent,
                        "Der angegebene Pfad existiert nicht oder ist kein Ordner:\n" + inputPath,
                        "Ungültiger Pfad", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            return inputPath;
        }
    }

    /**
     * Returns the default Outlook data files path on Windows.
     * Prefers LOCALAPPDATA, falls back to APPDATA\..\Local.
     */
    public static String getDefaultOutlookPath() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            return localAppData + "\\Microsoft\\Outlook";
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            return appData + "\\..\\Local\\Microsoft\\Outlook";
        }
        return "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Microsoft\\Outlook";
    }
}
