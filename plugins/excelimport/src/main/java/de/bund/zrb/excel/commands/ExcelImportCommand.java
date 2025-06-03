package de.bund.zrb.excel.commands;

import de.bund.zrb.excel.plugin.ExcelImport;
import de.zrb.bund.api.Command;
import de.zrb.bund.api.MainframeContext;

public class ExcelImportCommand implements Command {

    private final ExcelImport plugin;
    private final MainframeContext mainFrame;

    public ExcelImportCommand(MainframeContext mainFrame, ExcelImport plugin) {
        this.mainFrame = mainFrame;
        this.plugin = plugin;
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
        plugin.onImport(); // ✅ Import-Vorgang auslösen
    }
}
