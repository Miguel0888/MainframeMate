package de.bund.zrb.plugins.excel.commands;

import de.bund.zrb.plugins.excel.dialogs.ExcelImportSettingsDialog;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.commands.Command;

public class ExcelSettingsCommand implements Command {

    private final MainFrame mainFrame;

    public ExcelSettingsCommand(MainFrame mainFrame) {
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
