package de.bund.zrb.excel.commands;

import de.bund.zrb.excel.dialogs.ExcelImportSettingsDialog;
import de.zrb.bund.api.Command;
import de.zrb.bund.api.MainframeContext;

public class ExcelSettingsCommand implements Command {

    private final MainframeContext mainFrame;

    public ExcelSettingsCommand(MainframeContext mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public String getId() {
        return "settings.plugins.excel";
    }

    @Override
    public String getLabel() {
        return "Excel-Import Einstellungen...";
    }

    @Override
    public void perform() {
        ExcelImportSettingsDialog dialog = new ExcelImportSettingsDialog(mainFrame);
        dialog.setVisible(true);
    }
}
