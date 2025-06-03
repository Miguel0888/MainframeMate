package de.bund.zrb.excel.plugin;

import de.bund.zrb.excel.commands.ExcelImportCommand;
import de.bund.zrb.excel.commands.ExcelSettingsCommand;
import de.bund.zrb.excel.controller.ExcelImportController;
import de.bund.zrb.excel.mcp.ImportExcelTool;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.Command;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.util.*;

public class ExcelImport implements MainframeMatePlugin {

    private static final String PLUGIN_KEY = "excelImporter";

    MainframeContext context;
    private TemplateRepository templateRepository;

    @Override
    public String getPluginName() {
        return "Excel-Importer";
    }

    @Override
    public List<Object> getCommands(Object context) {
        return MainframeMatePlugin.super.getCommands(context);
    }

    @Override
    public void initialize(MainframeContext mainFrame) {
        this.context = mainFrame;
        templateRepository = new TemplateRepository(context.getSettingsFolder());
    }

    @Override
    public List<Command> getCommands(MainframeContext mainFrame) {
        return Arrays.asList(
                new ExcelImportCommand(mainFrame, this),
                new ExcelSettingsCommand(mainFrame)
        );
    }

    @Override
    public List<McpTool> getTools() {
        return Collections.singletonList(new ImportExcelTool());
    }

    public MainframeContext getContext() { return context; }
    public Component getMainFrame() { return context.getMainFrame(); }

    public void onImport() {
        ExcelImportController.handleImport(this);
    }

    void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    File chooseFile(Component parent, String title, String fileDesc, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(fileDesc, extensions));
        int result = chooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    public Map<String, String> getSettings() {
        return context.loadPluginSettings(PLUGIN_KEY);
    }

    void savePluginSettings(Map<String, String> newValues) {
        context.savePluginSettings(PLUGIN_KEY, new LinkedHashMap<>(newValues));
    }

    public TemplateRepository getTemplateRepository() {
        return templateRepository;
    }
}
