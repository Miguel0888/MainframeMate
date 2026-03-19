package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.lock.LockerStyle;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.PasswordMethod;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GeneralSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> fontCombo;
    private final JComboBox<Integer> fontSizeCombo;
    private final JSpinner marginSpinner;
    private final JCheckBox compareByDefaultBox;
    private final JCheckBox showHelpIconsBox;
    private final JCheckBox enableSound;
    private final JTextField defaultWorkflow;
    private final JSpinner workflowTimeoutSpinner;
    private final JSpinner importDelaySpinner;
    private final JList<String> supportedFileList;
    private final JCheckBox enableLock;
    private final JSpinner lockDelay;
    private final JSpinner lockPre;
    private final JComboBox<LockerStyle> lockStyleBox;
    private final JComboBox<PasswordMethod> passwordMethodBox;
    private final JTextField keepassKpScriptField;
    private final JTextField keepassDatabaseField;
    private final JTextField keepassEntryTitleField;
    private final JPanel keepassConfigPanel;
    private final JComboBox<String> keepassAccessMethodBox;
    private final JTextField keepassRpcHostField;
    private final JSpinner keepassRpcPortSpinner;
    private final JPasswordField keepassRpcKeyField;
    private final JComboBox<String> keepassRpcOriginSchemeBox;
    private final JTextField keepassRpcOriginIdField;
    private final JPanel keepassPsPanel;
    private final JPanel keepassRpcPanel;
    private final JCheckBox historyEnabledBox;
    private final JSpinner historyMaxVersionsSpinner;
    private final JSpinner historyMaxAgeDaysSpinner;

    public GeneralSettingsPanel(Component parent) {
        super("general", "Allgemein");
        FormBuilder fb = new FormBuilder();

        fontCombo = new JComboBox<>(new String[]{"Monospaced", "Consolas", "Courier New", "Menlo", "Dialog"});
        fontCombo.setSelectedItem(settings.editorFont);
        fb.addRowHelp("Editor-Schriftart:", fontCombo, HelpContentProvider.HelpTopic.SETTINGS_GENERAL);

        fontSizeCombo = new JComboBox<>(new Integer[]{10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 72});
        fontSizeCombo.setEditable(true);
        fontSizeCombo.setSelectedItem(settings.editorFontSize);
        fb.addRow("Editor-Schriftgröße:", fontSizeCombo);

        marginSpinner = new JSpinner(new SpinnerNumberModel(Math.max(0, settings.marginColumn), 0, 200, 1));
        fb.addRow("Markierung bei Spalte (0=aus):", marginSpinner);

        fb.addSeparator();

        compareByDefaultBox = new JCheckBox("Vergleich automatisch einblenden");
        compareByDefaultBox.setSelected(settings.compareByDefault);
        fb.addWide(compareByDefaultBox);

        showHelpIconsBox = new JCheckBox("Hilfe-Icons anzeigen");
        showHelpIconsBox.setSelected(settings.showHelpIcons);
        showHelpIconsBox.setToolTipText("Deaktivieren für erfahrene Benutzer");
        fb.addWide(showHelpIconsBox);

        enableSound = new JCheckBox("Sounds abspielen");
        enableSound.setSelected(settings.soundEnabled);
        fb.addWide(enableSound);

        fb.addSection("App-Design");

        lockStyleBox = new JComboBox<>(LockerStyle.values());
        lockStyleBox.setSelectedIndex(Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle)));
        fb.addRow("Design:", lockStyleBox);

        fb.addSection("Workflow");

        defaultWorkflow = new JTextField(settings.defaultWorkflow);
        fb.addRow("Default Workflow:", defaultWorkflow);

        workflowTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.workflowTimeout, 100, 300_000, 500));
        fb.addRow("Workflow Timeout (ms):", workflowTimeoutSpinner);

        importDelaySpinner = new JSpinner(new SpinnerNumberModel(settings.importDelay, 0, 60, 1));
        fb.addRow("Import-Verzögerung (s):", importDelaySpinner);

        fb.addSection("Dateiendungen");

        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        for (String ext : settings.supportedFiles) fileListModel.addElement(ext);
        supportedFileList = new JList<>(fileListModel);
        supportedFileList.setVisibleRowCount(4);
        fb.addWide(new JScrollPane(supportedFileList));

        JButton addExtButton = new JButton("➕ Hinzufügen");
        addExtButton.addActionListener(e -> {
            String ext = JOptionPane.showInputDialog(parent, "Neue Dateiendung eingeben (mit Punkt):", ".xyz");
            if (ext != null && !ext.trim().isEmpty()) fileListModel.addElement(ext.trim());
        });
        JButton removeExtButton = new JButton("➖ Entfernen");
        removeExtButton.addActionListener(e -> {
            int index = supportedFileList.getSelectedIndex();
            if (index >= 0) fileListModel.remove(index);
        });
        fb.addButtons(addExtButton, removeExtButton);

        fb.addSection("Sicherheit");

        passwordMethodBox = new JComboBox<>(PasswordMethod.values());
        PasswordMethod currentMethod = PasswordMethod.WINDOWS_DPAPI;
        try {
            if (settings.passwordMethod != null && !settings.passwordMethod.isEmpty()) {
                currentMethod = PasswordMethod.valueOf(settings.passwordMethod);
            }
        } catch (IllegalArgumentException ignore) { /* keep default */ }
        passwordMethodBox.setSelectedItem(currentMethod);
        fb.addRow("Passwort-Verschlüsselung:", passwordMethodBox);
        fb.addInfo("<html><small>"
                + "<b>Windows DPAPI (JNA):</b> Verschlüsselung durch Windows — an den Benutzer gebunden. "
                + "Kann auf gehärteten Systemen (Win 11 / AppLocker) blockiert werden.<br>"
                + "<b>Windows DPAPI (PowerShell):</b> Gleiche DPAPI-Sicherheit, aber ohne JNA. "
                + "Benötigt <code>powershell.exe</code> — kann bei restriktiver Execution Policy blockiert sein.<br>"
                + "<b>Java AES-256:</b> Plattformunabhängig, reines Java — Master-Key in <code>~/.mainframemate/.master.key</code>.<br>"
                + "<b>KeePass:</b> Passwörter werden in einer KeePass-Datenbank gespeichert. "
                + "Benötigt KeePass 2.x (die KeePassLib ist in <code>KeePass.exe</code> enthalten, wird per PowerShell geladen)."
                + "</small></html>");

        // ── KeePass configuration (shown/hidden based on selected method) ──
        keepassKpScriptField = new JTextField(safe(settings.keepassInstallPath), 30);
        keepassDatabaseField = new JTextField(safe(settings.keepassDatabasePath), 30);
        keepassEntryTitleField = new JTextField(safe(settings.keepassEntryTitle), 20);
        keepassAccessMethodBox = new JComboBox<>(new String[]{"PowerShell", "KeePassRPC"});
        keepassRpcHostField = new JTextField(settings.getEffectiveRpcHost(), 15);
        keepassRpcPortSpinner = new JSpinner(new SpinnerNumberModel(settings.keepassRpcPort, 1, 65535, 1));
        keepassRpcKeyField = new JPasswordField(safe(settings.keepassRpcKey), 30);

        keepassRpcOriginSchemeBox = new JComboBox<>(new String[]{
                "chrome-extension://", "moz-extension://", "safari-web-extension://",
                "ms-browser-extension://", "resource://gre-resources/"
        });
        String savedScheme = safe(settings.keepassRpcOriginScheme);
        if (!savedScheme.isEmpty()) {
            keepassRpcOriginSchemeBox.setSelectedItem(savedScheme);
            // if the saved value isn't in the list, add it
            if (!savedScheme.equals(keepassRpcOriginSchemeBox.getSelectedItem())) {
                keepassRpcOriginSchemeBox.addItem(savedScheme);
                keepassRpcOriginSchemeBox.setSelectedItem(savedScheme);
            }
        }
        keepassRpcOriginSchemeBox.setEditable(true);
        keepassRpcOriginIdField = new JTextField(safe(settings.keepassRpcOriginId).isEmpty()
                ? "mainframemate" : settings.keepassRpcOriginId, 18);

        boolean isRpc = "RPC".equalsIgnoreCase(settings.keepassAccessMethod);
        keepassAccessMethodBox.setSelectedIndex(isRpc ? 1 : 0);

        keepassConfigPanel = new JPanel(new GridBagLayout());
        keepassConfigPanel.setBorder(BorderFactory.createTitledBorder("KeePass-Konfiguration"));
        GridBagConstraints kc = new GridBagConstraints();
        kc.insets = new Insets(3, 6, 3, 6);
        kc.anchor = GridBagConstraints.WEST;
        kc.gridy = 0;

        // Row 0: Access method
        kc.gridx = 0; kc.fill = GridBagConstraints.NONE; kc.weightx = 0;
        keepassConfigPanel.add(new JLabel("Zugriffsmethode:"), kc);
        kc.gridx = 1; kc.fill = GridBagConstraints.HORIZONTAL; kc.weightx = 1;
        keepassConfigPanel.add(keepassAccessMethodBox, kc);

        // Row 1: Datenbank (shared)
        kc.gridy = 1;
        kc.gridx = 0; kc.fill = GridBagConstraints.NONE; kc.weightx = 0;
        keepassConfigPanel.add(new JLabel("Datenbank (.kdbx):"), kc);
        kc.gridx = 1; kc.fill = GridBagConstraints.HORIZONTAL; kc.weightx = 1;
        keepassConfigPanel.add(keepassDatabaseField, kc);
        kc.gridx = 2; kc.fill = GridBagConstraints.NONE; kc.weightx = 0;
        JButton browseDb = new JButton("…");
        browseDb.addActionListener(e -> browseFile(keepassDatabaseField, "KeePass-Datenbank", "kdbx"));
        keepassConfigPanel.add(browseDb, kc);

        // Row 2: Eintragstitel (shared)
        kc.gridy = 2;
        kc.gridx = 0; kc.fill = GridBagConstraints.NONE; kc.weightx = 0;
        keepassConfigPanel.add(new JLabel("Eintragstitel:"), kc);
        kc.gridx = 1; kc.fill = GridBagConstraints.HORIZONTAL; kc.weightx = 1;
        keepassConfigPanel.add(keepassEntryTitleField, kc);

        // ── PowerShell-specific sub-panel ──
        keepassPsPanel = new JPanel(new GridBagLayout());
        keepassPsPanel.setBorder(BorderFactory.createTitledBorder("PowerShell"));
        GridBagConstraints pc = new GridBagConstraints();
        pc.insets = new Insets(3, 6, 3, 6);
        pc.anchor = GridBagConstraints.WEST;
        pc.gridy = 0;
        pc.gridx = 0; pc.fill = GridBagConstraints.NONE; pc.weightx = 0;
        keepassPsPanel.add(new JLabel("KeePass-Verzeichnis:"), pc);
        pc.gridx = 1; pc.fill = GridBagConstraints.HORIZONTAL; pc.weightx = 1;
        keepassPsPanel.add(keepassKpScriptField, pc);
        pc.gridx = 2; pc.fill = GridBagConstraints.NONE; pc.weightx = 0;
        JButton browseKpDir = new JButton("…");
        browseKpDir.addActionListener(e -> browseDirectory(keepassKpScriptField, "KeePass-Installationsverzeichnis"));
        keepassPsPanel.add(browseKpDir, pc);

        kc.gridy = 3; kc.gridx = 0; kc.gridwidth = 3;
        kc.fill = GridBagConstraints.HORIZONTAL; kc.weightx = 1;
        keepassConfigPanel.add(keepassPsPanel, kc);
        kc.gridwidth = 1;

        // ── KeePassRPC-specific sub-panel ──
        keepassRpcPanel = new JPanel(new GridBagLayout());
        keepassRpcPanel.setBorder(BorderFactory.createTitledBorder("KeePassRPC"));
        GridBagConstraints rc = new GridBagConstraints();
        rc.insets = new Insets(3, 6, 3, 6);
        rc.anchor = GridBagConstraints.WEST;
        rc.gridy = 0;
        rc.gridx = 0; rc.fill = GridBagConstraints.NONE; rc.weightx = 0;
        keepassRpcPanel.add(new JLabel("Host:"), rc);
        rc.gridx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.weightx = 1;
        keepassRpcPanel.add(keepassRpcHostField, rc);
        rc.gridy = 1;
        rc.gridx = 0; rc.fill = GridBagConstraints.NONE; rc.weightx = 0;
        keepassRpcPanel.add(new JLabel("Port:"), rc);
        rc.gridx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.weightx = 1;
        keepassRpcPanel.add(keepassRpcPortSpinner, rc);
        rc.gridy = 2;
        rc.gridx = 0; rc.fill = GridBagConstraints.NONE; rc.weightx = 0;
        keepassRpcPanel.add(new JLabel("SRP-Schlüssel:"), rc);
        rc.gridx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.weightx = 1;
        JPanel keyPanel = new JPanel(new BorderLayout(4, 0));
        keyPanel.add(keepassRpcKeyField, BorderLayout.CENTER);
        final char echoDefault = keepassRpcKeyField.getEchoChar();
        JButton showKeyButton = new JButton("\uD83D\uDC41"); // 👁
        showKeyButton.setToolTipText("Schlüssel anzeigen / verbergen");
        showKeyButton.setMargin(new Insets(0, 4, 0, 4));
        showKeyButton.setFocusable(false);
        showKeyButton.addActionListener(ev -> {
            if (keepassRpcKeyField.getEchoChar() == 0) {
                keepassRpcKeyField.setEchoChar(echoDefault);
                showKeyButton.setText("\uD83D\uDC41"); // 👁
            } else {
                keepassRpcKeyField.setEchoChar((char) 0);
                showKeyButton.setText("\uD83D\uDE48"); // 🙈
            }
        });
        keyPanel.add(showKeyButton, BorderLayout.EAST);
        keepassRpcPanel.add(keyPanel, rc);
        rc.gridy = 3; rc.gridx = 0; rc.gridwidth = 2;
        JLabel rpcHint = new JLabel("<html><small>Den Schlüssel erhalten Sie aus KeePass beim ersten "
                + "Verbindungsaufbau (Pairing-Dialog).</small></html>");
        rpcHint.setForeground(java.awt.Color.GRAY);
        keepassRpcPanel.add(rpcHint, rc);

        // Origin row
        rc.gridy = 4; rc.gridwidth = 1;
        rc.gridx = 0; rc.fill = GridBagConstraints.NONE; rc.weightx = 0;
        keepassRpcPanel.add(new JLabel("Origin:"), rc);

        rc.gridx = 1; rc.fill = GridBagConstraints.HORIZONTAL; rc.weightx = 1;
        JPanel originPanel = new JPanel(new GridBagLayout());
        GridBagConstraints oc = new GridBagConstraints();
        oc.insets = new Insets(0, 0, 0, 3);
        oc.gridy = 0;
        oc.gridx = 0; oc.fill = GridBagConstraints.NONE; oc.weightx = 0;
        originPanel.add(keepassRpcOriginSchemeBox, oc);
        oc.gridx = 1; oc.fill = GridBagConstraints.HORIZONTAL; oc.weightx = 1;
        originPanel.add(keepassRpcOriginIdField, oc);
        oc.gridx = 2; oc.fill = GridBagConstraints.NONE; oc.weightx = 0;
        JButton uuidButton = new JButton("\uD83C\uDFB2"); // 🎲
        uuidButton.setMargin(new Insets(2, 6, 2, 6));
        uuidButton.setToolTipText("Zufällige UUID generieren");
        uuidButton.addActionListener(e ->
                keepassRpcOriginIdField.setText(java.util.UUID.randomUUID().toString()));
        originPanel.add(uuidButton, oc);
        keepassRpcPanel.add(originPanel, rc);

        rc.gridy = 5; rc.gridx = 0; rc.gridwidth = 2;
        JLabel originHint = new JLabel("<html><small>KeePassRPC prüft den Origin-Header. "
                + "Standardmäßig sind Browser-Extensions (chrome-extension://, moz-extension://, …) erlaubt.</small></html>");
        originHint.setForeground(java.awt.Color.GRAY);
        keepassRpcPanel.add(originHint, rc);

        rc.gridy = 6; rc.gridx = 0; rc.gridwidth = 2;
        rc.fill = GridBagConstraints.NONE; rc.anchor = GridBagConstraints.WEST;
        JButton pairingButton = new JButton("🔗 Pairing starten…");
        pairingButton.setToolTipText("Startet den KeePassRPC-Pairing-Dialog zum automatischen Setzen des SRP-Schlüssels");
        pairingButton.addActionListener(e -> {
            String key = de.bund.zrb.util.KeePassRpcPairingDialog.showAndPair();
            if (key != null && !key.trim().isEmpty()) {
                keepassRpcKeyField.setText(key);
            }
        });
        keepassRpcPanel.add(pairingButton, rc);

        kc.gridy = 4; kc.gridx = 0; kc.gridwidth = 3;
        kc.fill = GridBagConstraints.HORIZONTAL; kc.weightx = 1;
        keepassConfigPanel.add(keepassRpcPanel, kc);
        kc.gridwidth = 1;

        fb.addWide(keepassConfigPanel);

        // Toggle sub-panel visibility based on access method
        keepassPsPanel.setVisible(!isRpc);
        keepassRpcPanel.setVisible(isRpc);
        keepassDatabaseField.setEnabled(!isRpc);
        browseDb.setEnabled(!isRpc);
        keepassAccessMethodBox.addActionListener(e -> {
            boolean rpc = keepassAccessMethodBox.getSelectedIndex() == 1;
            keepassPsPanel.setVisible(!rpc);
            keepassRpcPanel.setVisible(rpc);
            keepassDatabaseField.setEnabled(!rpc);
            browseDb.setEnabled(!rpc);
            keepassConfigPanel.revalidate();
        });

        // Toggle KeePass config visibility + migration prompt
        keepassConfigPanel.setVisible(currentMethod == PasswordMethod.KEEPASS);
        final PasswordMethod[] lastConfirmedMethod = { currentMethod };
        passwordMethodBox.addActionListener(e -> {
            PasswordMethod selected = (PasswordMethod) passwordMethodBox.getSelectedItem();
            keepassConfigPanel.setVisible(selected == PasswordMethod.KEEPASS);

            // Show migration dialog immediately when switching TO KeePass
            if (selected == PasswordMethod.KEEPASS && lastConfirmedMethod[0] != PasswordMethod.KEEPASS) {
                de.bund.zrb.ui.settings.KeePassMigrationDialog dlg =
                        new de.bund.zrb.ui.settings.KeePassMigrationDialog(parent, lastConfirmedMethod[0]);
                boolean accepted = dlg.showAndMigrate();
                if (!accepted) {
                    // User cancelled — revert combo box selection
                    passwordMethodBox.setSelectedItem(lastConfirmedMethod[0]);
                    keepassConfigPanel.setVisible(false);
                    return;
                }
            }
            lastConfirmedMethod[0] = selected;
        });


        fb.addSection("Bildschirmsperre");

        enableLock = new JCheckBox("Bildschirmsperre aktivieren");
        enableLock.setSelected(settings.lockEnabled);
        fb.addWide(enableLock);

        lockDelay = new JSpinner(new SpinnerNumberModel(settings.lockDelay, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Sperre nach (ms):", lockDelay);

        lockPre = new JSpinner(new SpinnerNumberModel(settings.lockPrenotification, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Ankündigung (ms):", lockPre);


        fb.addSection("Lokale Historie");

        historyEnabledBox = new JCheckBox("Lokale Historie aktivieren");
        historyEnabledBox.setSelected(settings.historyEnabled);
        fb.addWide(historyEnabledBox);

        historyMaxVersionsSpinner = new JSpinner(new SpinnerNumberModel(settings.historyMaxVersionsPerFile, 1, 10000, 10));
        fb.addRow("Max. Versionen pro Datei:", historyMaxVersionsSpinner);

        historyMaxAgeDaysSpinner = new JSpinner(new SpinnerNumberModel(settings.historyMaxAgeDays, 1, 3650, 10));
        fb.addRow("Max. Alter (Tage):", historyMaxAgeDaysSpinner);

        JButton pruneNowButton = new JButton("🧹 Bereinigen");
        pruneNowButton.addActionListener(e -> {
            de.bund.zrb.history.LocalHistoryService.getInstance().prune(
                    ((Number) historyMaxVersionsSpinner.getValue()).intValue(),
                    ((Number) historyMaxAgeDaysSpinner.getValue()).intValue());
            JOptionPane.showMessageDialog(parent, "Bereinigung abgeschlossen.", "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton clearAllHistoryButton = new JButton("🗑️ Alles löschen");
        clearAllHistoryButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(parent, "Gesamte lokale Historie löschen?",
                    "Historie löschen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                de.bund.zrb.history.LocalHistoryService svc = de.bund.zrb.history.LocalHistoryService.getInstance();
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.LOCAL);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.FTP);
                svc.clearBackend(de.bund.zrb.ui.VirtualBackendType.NDV);
                JOptionPane.showMessageDialog(parent, "Gesamte Historie gelöscht.", "Lokale Historie", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        fb.addButtons(pruneNowButton, clearAllHistoryButton);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.editorFont = (String) fontCombo.getSelectedItem();
        s.editorFontSize = java.util.Optional.ofNullable(fontSizeCombo.getEditor().getItem())
                .map(Object::toString)
                .map(str -> { try { return Integer.parseInt(str); } catch (NumberFormatException e) { return null; } })
                .orElse(12);
        s.marginColumn = (Integer) marginSpinner.getValue();
        s.compareByDefault = compareByDefaultBox.isSelected();
        s.showHelpIcons = showHelpIconsBox.isSelected();
        s.soundEnabled = enableSound.isSelected();
        s.defaultWorkflow = defaultWorkflow.getText();
        s.workflowTimeout = ((Number) workflowTimeoutSpinner.getValue()).longValue();
        s.importDelay = (Integer) importDelaySpinner.getValue();
        DefaultListModel<String> model = (DefaultListModel<String>) supportedFileList.getModel();
        List<String> extensions = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) extensions.add(model.get(i));
        s.supportedFiles = extensions;
        s.lockEnabled = enableLock.isSelected();
        s.lockDelay = (Integer) lockDelay.getValue();
        s.lockPrenotification = (Integer) lockPre.getValue();
        s.lockStyle = lockStyleBox.getSelectedIndex();
        PasswordMethod selectedMethod = (PasswordMethod) passwordMethodBox.getSelectedItem();
        s.passwordMethod = selectedMethod != null ? selectedMethod.name() : PasswordMethod.WINDOWS_DPAPI.name();
        s.keepassInstallPath = keepassKpScriptField.getText().trim();
        s.keepassDatabasePath = keepassDatabaseField.getText().trim();
        s.keepassEntryTitle = keepassEntryTitleField.getText().trim();
        s.keepassAccessMethod = keepassAccessMethodBox.getSelectedIndex() == 1 ? "RPC" : "POWERSHELL";
        s.keepassRpcHost = keepassRpcHostField.getText().trim();
        s.keepassRpcPort = ((Number) keepassRpcPortSpinner.getValue()).intValue();
        s.keepassRpcKey = new String(keepassRpcKeyField.getPassword()).trim();
        s.keepassRpcOriginScheme = String.valueOf(keepassRpcOriginSchemeBox.getSelectedItem()).trim();
        s.keepassRpcOriginId = keepassRpcOriginIdField.getText().trim();
        s.historyEnabled = historyEnabledBox.isSelected();
        s.historyMaxVersionsPerFile = ((Number) historyMaxVersionsSpinner.getValue()).intValue();
        s.historyMaxAgeDays = ((Number) historyMaxAgeDaysSpinner.getValue()).intValue();
    }

    private void browseFile(JTextField target, String description, String extension) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(description + " auswählen");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                description + " (*." + extension + ")", extension));
        String current = target.getText().trim();
        if (!current.isEmpty()) {
            java.io.File f = new java.io.File(current);
            if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                fc.setCurrentDirectory(f.getParentFile());
            }
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseDirectory(JTextField target, String description) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(description + " auswählen");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = target.getText().trim();
        if (!current.isEmpty()) {
            java.io.File f = new java.io.File(current);
            if (f.isDirectory()) {
                fc.setCurrentDirectory(f);
            } else if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                fc.setCurrentDirectory(f.getParentFile());
            }
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

