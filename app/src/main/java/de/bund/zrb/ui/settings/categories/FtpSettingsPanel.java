package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.files.ftpconfig.*;
import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.components.ComboBoxHelper;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

public class FtpSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<FtpFileType> typeBox;
    private final JComboBox<FtpTextFormat> formatBox;
    private final JComboBox<FtpFileStructure> structureBox;
    private final JComboBox<FtpTransferMode> modeBox;
    private final JCheckBox hexDumpBox;
    private final JSpinner ftpConnectTimeoutSpinner, ftpControlTimeoutSpinner, ftpDataTimeoutSpinner;
    private final JSpinner ftpRetryMaxAttemptsSpinner, ftpRetryBackoffSpinner, ftpRetryMaxBackoffSpinner;
    private final JComboBox<String> ftpRetryStrategyCombo;
    private final JCheckBox ftpRetryOnTimeoutBox, ftpRetryOnTransientIoBox;
    private final JTextField ftpRetryOnReplyCodesField;
    private final JCheckBox ftpUseLoginAsHlqBox;
    private final JTextField ftpCustomHlqField;

    public FtpSettingsPanel() {
        super("ftp", "FTP-Verbindung");
        FormBuilder fb = new FormBuilder();

        typeBox = ComboBoxHelper.createComboBoxWithNullOption(FtpFileType.class, settings.ftpFileType, "Standard");
        fb.addRowHelp("FTP Datei-Typ (TYPE):", typeBox, HelpContentProvider.HelpTopic.FTP_FILE_TYPE);

        formatBox = ComboBoxHelper.createComboBoxWithNullOption(FtpTextFormat.class, settings.ftpTextFormat, "Standard");
        fb.addRowHelp("FTP Text-Format:", formatBox, HelpContentProvider.HelpTopic.FTP_TEXT_FORMAT);

        structureBox = ComboBoxHelper.createComboBoxWithNullOption(FtpFileStructure.class, settings.ftpFileStructure, "Automatisch");
        fb.addRowHelp("FTP Dateistruktur:", structureBox, HelpContentProvider.HelpTopic.FTP_FILE_STRUCTURE);

        modeBox = ComboBoxHelper.createComboBoxWithNullOption(FtpTransferMode.class, settings.ftpTransferMode, "Standard");
        fb.addRowHelp("FTP Übertragungsmodus:", modeBox, HelpContentProvider.HelpTopic.FTP_TRANSFER_MODE);

        hexDumpBox = new JCheckBox("Hexdump in Konsole anzeigen");
        hexDumpBox.setSelected(settings.enableHexDump);
        fb.addWideHelp(hexDumpBox, HelpContentProvider.HelpTopic.FTP_HEX_DUMP);

        fb.addSection("FTP Timeouts (0 = deaktiviert)");

        ftpConnectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpConnectTimeoutMs, 0, 300_000, 1000));
        fb.addRowHelp("Connect Timeout (ms):", ftpConnectTimeoutSpinner, HelpContentProvider.HelpTopic.FTP_TIMEOUT_CONNECT);

        ftpControlTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpControlTimeoutMs, 0, 300_000, 1000));
        fb.addRow("Control Timeout (ms):", ftpControlTimeoutSpinner);

        ftpDataTimeoutSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpDataTimeoutMs, 0, 300_000, 1000));
        fb.addRow("Data Timeout (ms):", ftpDataTimeoutSpinner);

        fb.addSection("FTP Retries");

        ftpRetryMaxAttemptsSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryMaxAttempts, 1, 10, 1));
        fb.addRowHelp("Maximale Versuche:", ftpRetryMaxAttemptsSpinner, HelpContentProvider.HelpTopic.FTP_RETRY_MAX_ATTEMPTS);

        ftpRetryBackoffSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryBackoffMs, 0, 60_000, 500));
        fb.addRow("Wartezeit (ms):", ftpRetryBackoffSpinner);

        ftpRetryStrategyCombo = new JComboBox<>(new String[]{"FIXED", "EXPONENTIAL"});
        ftpRetryStrategyCombo.setSelectedItem(settings.ftpRetryBackoffStrategy);
        fb.addRow("Backoff-Strategie:", ftpRetryStrategyCombo);

        ftpRetryMaxBackoffSpinner = new JSpinner(new SpinnerNumberModel(settings.ftpRetryMaxBackoffMs, 0, 300_000, 1000));
        fb.addRow("Max Backoff (ms):", ftpRetryMaxBackoffSpinner);

        ftpRetryOnTimeoutBox = new JCheckBox("Retry bei Timeout-Fehlern");
        ftpRetryOnTimeoutBox.setSelected(settings.ftpRetryOnTimeout);
        fb.addWide(ftpRetryOnTimeoutBox);

        ftpRetryOnTransientIoBox = new JCheckBox("Retry bei IO-Fehlern (Connection Reset etc.)");
        ftpRetryOnTransientIoBox.setSelected(settings.ftpRetryOnTransientIo);
        fb.addWide(ftpRetryOnTransientIoBox);

        ftpRetryOnReplyCodesField = new JTextField(settings.ftpRetryOnReplyCodes, 20);
        ftpRetryOnReplyCodesField.setToolTipText("z.B. 421,425,426");
        fb.addRow("Reply Codes (kommasep.):", ftpRetryOnReplyCodesField);

        fb.addSection("Initial HLQ (Startverzeichnis)");

        ftpUseLoginAsHlqBox = new JCheckBox("Login-Namen als HLQ verwenden");
        ftpUseLoginAsHlqBox.setSelected(settings.ftpUseLoginAsHlq);
        fb.addWide(ftpUseLoginAsHlqBox);

        ftpCustomHlqField = new JTextField(settings.ftpCustomHlq != null ? settings.ftpCustomHlq : "", 20);
        ftpCustomHlqField.setToolTipText("z.B. USERID oder PROD.DATA");
        ftpCustomHlqField.setEnabled(!settings.ftpUseLoginAsHlq);
        fb.addRow("Benutzerdefinierter HLQ:", ftpCustomHlqField);

        ftpUseLoginAsHlqBox.addActionListener(e -> ftpCustomHlqField.setEnabled(!ftpUseLoginAsHlqBox.isSelected()));

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.ftpFileType = ComboBoxHelper.getSelectedEnumValue(typeBox, FtpFileType.class);
        s.ftpTextFormat = ComboBoxHelper.getSelectedEnumValue(formatBox, FtpTextFormat.class);
        s.ftpFileStructure = ComboBoxHelper.getSelectedEnumValue(structureBox, FtpFileStructure.class);
        s.ftpTransferMode = ComboBoxHelper.getSelectedEnumValue(modeBox, FtpTransferMode.class);
        s.enableHexDump = hexDumpBox.isSelected();
        s.ftpConnectTimeoutMs = ((Number) ftpConnectTimeoutSpinner.getValue()).intValue();
        s.ftpControlTimeoutMs = ((Number) ftpControlTimeoutSpinner.getValue()).intValue();
        s.ftpDataTimeoutMs = ((Number) ftpDataTimeoutSpinner.getValue()).intValue();
        s.ftpRetryMaxAttempts = ((Number) ftpRetryMaxAttemptsSpinner.getValue()).intValue();
        s.ftpRetryBackoffMs = ((Number) ftpRetryBackoffSpinner.getValue()).intValue();
        s.ftpRetryBackoffStrategy = (String) ftpRetryStrategyCombo.getSelectedItem();
        s.ftpRetryMaxBackoffMs = ((Number) ftpRetryMaxBackoffSpinner.getValue()).intValue();
        s.ftpRetryOnTimeout = ftpRetryOnTimeoutBox.isSelected();
        s.ftpRetryOnTransientIo = ftpRetryOnTransientIoBox.isSelected();
        s.ftpRetryOnReplyCodes = ftpRetryOnReplyCodesField.getText().trim();
        s.ftpUseLoginAsHlq = ftpUseLoginAsHlqBox.isSelected();
        s.ftpCustomHlq = ftpCustomHlqField.getText().trim();
    }
}

