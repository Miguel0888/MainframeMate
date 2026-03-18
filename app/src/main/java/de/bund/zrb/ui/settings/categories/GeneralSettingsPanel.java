package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.lock.LockerStyle;
import de.bund.zrb.ui.settings.FormBuilder;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.util.PasswordMethod;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final JPanel keepassPsPanel;
    private final JPanel keepassRpcPanel;
    private final JCheckBox historyEnabledBox;
    private final JSpinner historyMaxVersionsSpinner;
    private final JSpinner historyMaxAgeDaysSpinner;
    private final CredentialTableModel credentialTableModel;
    private final JTable credentialTable;

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
        keepassRpcHostField = new JTextField(safe(settings.keepassRpcHost), 15);
        keepassRpcPortSpinner = new JSpinner(new SpinnerNumberModel(settings.keepassRpcPort, 1, 65535, 1));
        keepassRpcKeyField = new JPasswordField(safe(settings.keepassRpcKey), 30);

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
        keepassRpcPanel.add(keepassRpcKeyField, rc);
        rc.gridy = 3; rc.gridx = 0; rc.gridwidth = 2;
        JLabel rpcHint = new JLabel("<html><small>Den Schlüssel erhalten Sie aus KeePass beim ersten "
                + "Verbindungsaufbau (Pairing-Dialog).</small></html>");
        rpcHint.setForeground(java.awt.Color.GRAY);
        keepassRpcPanel.add(rpcHint, rc);

        rc.gridy = 4; rc.gridx = 0; rc.gridwidth = 2;
        rc.fill = GridBagConstraints.NONE; rc.anchor = GridBagConstraints.WEST;
        JButton pairingButton = new JButton("🔗 Pairing starten…");
        pairingButton.setToolTipText("Startet den KeePassRPC-Pairing-Dialog zum automatischen Setzen des SRP-Schlüssels");
        pairingButton.addActionListener(e -> {
            String key = de.bund.zrb.util.KeePassRpcPairingDialog.showAndPair();
            if (key != null && !key.trim().isEmpty()) {
                keepassRpcKeyField.setText(key);
                JOptionPane.showMessageDialog(this,
                        "Pairing erfolgreich! Der SRP-Schlüssel wurde übernommen.",
                        "KeePassRPC", JOptionPane.INFORMATION_MESSAGE);
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
        keepassAccessMethodBox.addActionListener(e -> {
            boolean rpc = keepassAccessMethodBox.getSelectedIndex() == 1;
            keepassPsPanel.setVisible(!rpc);
            keepassRpcPanel.setVisible(rpc);
            keepassConfigPanel.revalidate();
        });

        // Toggle KeePass config visibility
        keepassConfigPanel.setVisible(currentMethod == PasswordMethod.KEEPASS);
        passwordMethodBox.addActionListener(e ->
                keepassConfigPanel.setVisible(passwordMethodBox.getSelectedItem() == PasswordMethod.KEEPASS));

        // ── Generalized credential store ──
        fb.addSection("Gespeicherte Zugangsdaten");
        fb.addInfo("Hier werden alle Zugangsdaten zentral verwaltet. "
                + "Komponenten wie Wiki, BetaView oder FTP legen ihre Credentials hier ab.");

        credentialTableModel = new CredentialTableModel(loadCredentialRows());
        credentialTable = new JTable(credentialTableModel);
        credentialTable.setRowHeight(22);
        credentialTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        credentialTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        credentialTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        credentialTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        credentialTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane credScroll = new JScrollPane(credentialTable);
        credScroll.setPreferredSize(new Dimension(500, 120));
        fb.addWide(credScroll);

        JButton editCredBtn = new JButton("✏️ Bearbeiten…");
        editCredBtn.addActionListener(e -> editCredential(parent));
        JButton addCredBtn = new JButton("➕ Hinzufügen…");
        addCredBtn.addActionListener(e -> addCredential(parent));
        JButton removeCredBtn = new JButton("➖ Entfernen");
        removeCredBtn.addActionListener(e -> removeCredential());
        fb.addButtons(addCredBtn, editCredBtn, removeCredBtn);

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

    // ═══════════════════════════════════════════════════════════
    //  Credential table helpers
    // ═══════════════════════════════════════════════════════════

    private List<CredentialRow> loadCredentialRows() {
        List<CredentialRow> rows = new ArrayList<CredentialRow>();
        Map<String, String> all = CredentialStore.getAll();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String key = entry.getKey();
            String component = CredentialStore.componentLabel(key);
            String id = CredentialStore.componentId(key);
            String user = CredentialStore.resolveUserNameQuietly(key);
            rows.add(new CredentialRow(key, component, id, user != null ? user : "?", user != null));
        }
        return rows;
    }

    private void refreshCredentialTable() {
        credentialTableModel.setRows(loadCredentialRows());
    }

    private void addCredential(Component parent) {
        JTextField keyField = new JTextField("wiki:", 20);
        JTextField userField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Schlüssel:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(keyField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JLabel hint = new JLabel("<html><small>Schlüssel-Format: <code>komponente:kennung</code> "
                + "(z.B. <code>wiki:mywiki</code>, <code>ftp:myhost</code>)</small></html>");
        hint.setForeground(Color.GRAY);
        panel.add(hint, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Neue Zugangsdaten anlegen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String key = keyField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());

        if (key.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Schlüssel und Benutzername dürfen nicht leer sein.",
                    "Fehler", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CredentialStore.store(key, user, pass);
        refreshCredentialTable();
    }

    private void editCredential(Component parent) {
        int row = credentialTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(parent, "Bitte zuerst einen Eintrag auswählen.",
                    "Kein Eintrag ausgewählt", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CredentialRow cr = credentialTableModel.getRow(row);

        // Try to load existing user/pass
        String existingUser = "";
        String existingPass = "";
        try {
            String[] cred = CredentialStore.resolve(cr.key);
            if (cred != null) {
                existingUser = cred[0];
                existingPass = cred[1];
            }
        } catch (Exception ignore) {
            // can't decrypt – start fresh
        }

        JTextField userField = new JTextField(existingUser, 20);
        JPasswordField passField = new JPasswordField(existingPass, 20);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Schlüssel:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel(cr.key), gbc);

        boolean hasCreds = !existingUser.isEmpty();
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel statusHint = new JLabel(hasCreds
                ? "✅ Zugangsdaten sind gespeichert."
                : "⚠️ Noch keine Zugangsdaten hinterlegt.");
        statusHint.setFont(statusHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(statusHint, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Zugangsdaten bearbeiten: " + cr.component + " – " + cr.id,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() && pass.isEmpty()) {
            CredentialStore.remove(cr.key);
        } else {
            CredentialStore.store(cr.key, user, pass);
        }
        refreshCredentialTable();
    }

    private void removeCredential() {
        int row = credentialTable.getSelectedRow();
        if (row < 0) return;
        CredentialRow cr = credentialTableModel.getRow(row);
        CredentialStore.remove(cr.key);
        refreshCredentialTable();
    }

    // ═══════════════════════════════════════════════════════════
    //  Credential table model
    // ═══════════════════════════════════════════════════════════

    private static final class CredentialRow {
        final String key;
        final String component;
        final String id;
        final String user;
        final boolean hasPassword;

        CredentialRow(String key, String component, String id, String user, boolean hasPassword) {
            this.key = key;
            this.component = component;
            this.id = id;
            this.user = user;
            this.hasPassword = hasPassword;
        }
    }

    private static final class CredentialTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Komponente", "Kennung", "Benutzer", "Status"};
        private List<CredentialRow> rows;

        CredentialTableModel(List<CredentialRow> rows) {
            this.rows = new ArrayList<CredentialRow>(rows);
        }

        void setRows(List<CredentialRow> rows) {
            this.rows = new ArrayList<CredentialRow>(rows);
            fireTableDataChanged();
        }

        CredentialRow getRow(int idx) { return rows.get(idx); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public boolean isCellEditable(int row, int col) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            CredentialRow r = rows.get(row);
            switch (col) {
                case 0: return r.component;
                case 1: return r.id;
                case 2: return r.user;
                case 3: return r.hasPassword ? "✅" : "⚠️";
                default: return "";
            }
        }
    }
}

