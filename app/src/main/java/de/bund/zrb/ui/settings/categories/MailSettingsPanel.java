package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

public class MailSettingsPanel extends AbstractSettingsPanel {

    private final JTextField mailPathField;
    private final JTextField mailContainerClassesField;
    private final JList<String> mailWhitelistJList;

    // Sync settings
    private final JCheckBox cbSyncEnabled;
    private final JCheckBox cbSyncMails;
    private final JCheckBox cbSyncCalendar;
    private final JCheckBox cbSyncContacts;
    private final JCheckBox cbSyncTasks;
    private final JCheckBox cbSyncNotes;
    private final JCheckBox cbSuppressStderr;
    private final JSpinner cooldownSpinner;

    public MailSettingsPanel() {
        super("mails", "Mails");
        FormBuilder fb = new FormBuilder();

        // ── Pfad ──
        String defaultPath = de.bund.zrb.ui.commands.ConnectMailMenuCommand.getDefaultOutlookPath();
        String currentValue = settings.mailStorePath;
        if (currentValue == null || currentValue.trim().isEmpty()) currentValue = defaultPath;
        mailPathField = new JTextField(currentValue, 30);
        JButton browseButton = new JButton("📂");
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(mailPathField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                mailPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        fb.addRowWithButton("Mail-Speicherort:", mailPathField, browseButton);

        String ccValue = settings.mailContainerClasses;
        if (ccValue == null || ccValue.trim().isEmpty())
            ccValue = de.bund.zrb.mail.model.MailboxCategory.getMailContainerClassesAsString();
        mailContainerClassesField = new JTextField(ccValue, 30);
        mailContainerClassesField.setToolTipText("Standard: IPF.Note,IPF.Imap");
        fb.addRow("ContainerClasses:", mailContainerClassesField);

        // ── Sync-Einstellungen ──
        fb.addSection("Hintergrund-Sync (Indizierung)");
        fb.addInfo("Welche Kategorien sollen automatisch indiziert und bei Änderungen aktualisiert werden?");

        cbSyncEnabled = new JCheckBox("Mail-Sync aktivieren");
        cbSyncEnabled.setSelected(settings.mailSyncEnabled);
        cbSyncEnabled.addActionListener(e -> updateSyncCheckboxStates());
        fb.addWide(cbSyncEnabled);

        cbSyncMails = new JCheckBox("📧 E-Mails (IPF.Note, IPF.Imap)");
        cbSyncMails.setSelected(settings.mailSyncMails);
        fb.addWide(cbSyncMails);

        cbSyncCalendar = new JCheckBox("📅 Kalender (IPF.Appointment)");
        cbSyncCalendar.setSelected(settings.mailSyncCalendar);
        fb.addWide(cbSyncCalendar);

        cbSyncContacts = new JCheckBox("👥 Kontakte (IPF.Contact)");
        cbSyncContacts.setSelected(settings.mailSyncContacts);
        fb.addWide(cbSyncContacts);

        cbSyncTasks = new JCheckBox("✅ Aufgaben (IPF.Task)");
        cbSyncTasks.setSelected(settings.mailSyncTasks);
        fb.addWide(cbSyncTasks);

        cbSyncNotes = new JCheckBox("📝 Notizen (IPF.StickyNote)");
        cbSyncNotes.setSelected(settings.mailSyncNotes);
        fb.addWide(cbSyncNotes);

        fb.addGap(4);
        cbSuppressStderr = new JCheckBox("java-libpst Konsolenmeldungen unterdrücken");
        cbSuppressStderr.setSelected(settings.mailSyncSuppressStderr);
        cbSuppressStderr.setToolTipText("Unterdrückt 'Unknown message type' und 'Can't get children' Meldungen auf stderr");
        fb.addWide(cbSuppressStderr);

        fb.addGap(4);
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(settings.mailSyncCooldownSeconds, 5, 3600, 5));
        cooldownSpinner.setToolTipText("Wartezeit in Sekunden nach einem Sync-Lauf, bevor der nächste gestartet wird (Totzeit/Cooldown)");
        fb.addRow("Totzeit (s):", cooldownSpinner);

        updateSyncCheckboxStates();

        // ── HTML-Whitelist ──
        fb.addSection("HTML-Whitelist");
        fb.addInfo("Absender, deren Mails immer in HTML geöffnet werden.");

        DefaultListModel<String> whitelistModel = new DefaultListModel<>();
        if (settings.mailHtmlWhitelistedSenders != null)
            for (String sender : settings.mailHtmlWhitelistedSenders) whitelistModel.addElement(sender);
        mailWhitelistJList = new JList<>(whitelistModel);
        mailWhitelistJList.setVisibleRowCount(5);
        fb.addWide(new JScrollPane(mailWhitelistJList));

        JButton wlRemoveButton = new JButton("➖ Entfernen");
        wlRemoveButton.addActionListener(e -> { int idx = mailWhitelistJList.getSelectedIndex(); if (idx >= 0) whitelistModel.removeElementAt(idx); });
        JButton wlClearButton = new JButton("🗑 Alle entfernen");
        wlClearButton.addActionListener(e -> whitelistModel.clear());
        fb.addButtons(wlRemoveButton, wlClearButton);

        installPanel(fb);
    }

    private void updateSyncCheckboxStates() {
        boolean enabled = cbSyncEnabled.isSelected();
        cbSyncMails.setEnabled(enabled);
        cbSyncCalendar.setEnabled(enabled);
        cbSyncContacts.setEnabled(enabled);
        cbSyncTasks.setEnabled(enabled);
        cbSyncNotes.setEnabled(enabled);
        cbSuppressStderr.setEnabled(enabled);
        cooldownSpinner.setEnabled(enabled);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.mailStorePath = mailPathField.getText().trim();
        s.mailContainerClasses = mailContainerClassesField.getText().trim();
        de.bund.zrb.mail.model.MailboxCategory.setMailContainerClasses(s.mailContainerClasses);

        s.mailSyncEnabled = cbSyncEnabled.isSelected();
        s.mailSyncMails = cbSyncMails.isSelected();
        s.mailSyncCalendar = cbSyncCalendar.isSelected();
        s.mailSyncContacts = cbSyncContacts.isSelected();
        s.mailSyncTasks = cbSyncTasks.isSelected();
        s.mailSyncNotes = cbSyncNotes.isSelected();
        s.mailSyncSuppressStderr = cbSuppressStderr.isSelected();
        s.mailSyncCooldownSeconds = ((Number) cooldownSpinner.getValue()).intValue();

        s.mailHtmlWhitelistedSenders = new java.util.HashSet<>();
        DefaultListModel<String> wlModel = (DefaultListModel<String>) mailWhitelistJList.getModel();
        for (int i = 0; i < wlModel.size(); i++) s.mailHtmlWhitelistedSenders.add(wlModel.getElementAt(i));
    }
}
