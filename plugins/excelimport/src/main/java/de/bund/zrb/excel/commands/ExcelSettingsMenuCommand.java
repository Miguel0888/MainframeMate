package de.bund.zrb.excel.commands;

import de.bund.zrb.excel.ui.ExcelImportSettingsDialog;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;

public class ExcelSettingsMenuCommand extends ShortcutMenuCommand {

    private final MainframeContext mainFrame;

    public ExcelSettingsMenuCommand(MainframeContext mainFrame) {
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
