package de.bund.zrb.plugins.excel.commands;

import de.bund.zrb.plugins.excel.dialogs.ExcelImportDialog;
import de.bund.zrb.ui.MainFrame;
import de.bund.zrb.ui.commands.Command;

public class ExcelImportCommand implements Command {

    private final MainFrame mainFrame;

    public ExcelImportCommand(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    @Override
    public String getId() {
        return "plugin.excel.import";
    }

    @Override
    public String getLabel() {
        return "Excel-Import...";
    }

    @Override
    public void perform() {
        ExcelImportDialog dialog = new ExcelImportDialog(mainFrame);
        dialog.setVisible(true);
        // Das eigentliche Handling wird von der Dialogklasse übernommen
    }
}
