package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

public class MailSettingsPanel extends AbstractSettingsPanel {

    private final JTextField mailPathField;
    private final JTextField mailContainerClassesField;
    private final JList<String> mailWhitelistJList;

    public MailSettingsPanel() {
        super("mails", "Mails");
        FormBuilder fb = new FormBuilder();

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

    @Override
    protected void applyToSettings(Settings s) {
        s.mailStorePath = mailPathField.getText().trim();
        s.mailContainerClasses = mailContainerClassesField.getText().trim();
        de.bund.zrb.mail.model.MailboxCategory.setMailContainerClasses(s.mailContainerClasses);
        s.mailHtmlWhitelistedSenders = new java.util.HashSet<>();
        DefaultListModel<String> wlModel = (DefaultListModel<String>) mailWhitelistJList.getModel();
        for (int i = 0; i < wlModel.size(); i++) s.mailHtmlWhitelistedSenders.add(wlModel.getElementAt(i));
    }
}

