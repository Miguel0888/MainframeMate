package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.lock.LockerStyle;
import de.bund.zrb.ui.settings.FormBuilder;

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

        fb.addSection("Bildschirmsperre");

        enableLock = new JCheckBox("Bildschirmsperre aktivieren");
        enableLock.setSelected(settings.lockEnabled);
        fb.addWide(enableLock);

        lockDelay = new JSpinner(new SpinnerNumberModel(settings.lockDelay, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Sperre nach (ms):", lockDelay);

        lockPre = new JSpinner(new SpinnerNumberModel(settings.lockPrenotification, 0, Integer.MAX_VALUE, 100));
        fb.addRow("Ankündigung (ms):", lockPre);

        lockStyleBox = new JComboBox<>(LockerStyle.values());
        lockStyleBox.setSelectedIndex(Math.max(0, Math.min(LockerStyle.values().length - 1, settings.lockStyle)));
        fb.addRow("Design:", lockStyleBox);

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
        s.historyEnabled = historyEnabledBox.isSelected();
        s.historyMaxVersionsPerFile = ((Number) historyMaxVersionsSpinner.getValue()).intValue();
        s.historyMaxAgeDays = ((Number) historyMaxAgeDaysSpinner.getValue()).intValue();
    }
}

