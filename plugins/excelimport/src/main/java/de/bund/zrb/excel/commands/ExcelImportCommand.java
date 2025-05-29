package de.bund.zrb.excel.commands;

import de.bund.zrb.excel.dialogs.ExcelImportDialog;
import de.zrb.bund.api.Command;
import de.zrb.bund.api.MainframeContext;

public class ExcelImportCommand implements Command {

    private final MainframeContext mainFrame;

    public ExcelImportCommand(MainframeContext mainFrame) {
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
