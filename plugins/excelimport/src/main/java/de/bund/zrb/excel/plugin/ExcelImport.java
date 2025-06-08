package de.bund.zrb.excel.plugin;

import de.bund.zrb.excel.commands.ExcelImportMenuCommand;
import de.bund.zrb.excel.commands.ExcelSettingsMenuCommand;
import de.bund.zrb.excel.controller.ExcelImportController;
import de.bund.zrb.excel.mcp.ImportExcelTool;
import de.bund.zrb.excel.repo.TemplateRepository;
import de.zrb.bund.api.MenuCommand;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.MainframeMatePlugin;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.util.*;

/**
 * This ist the Entry Class for the Java Service Loader, since it implements the MainframeMatePlugin Interface!
 * Therefor this class is necessary for the Plugin to be recognized and to work properly.
 */
public class ExcelImport implements MainframeMatePlugin {

    private static final String PLUGIN_KEY = "excelImporter";

    private MainframeContext context;
    private TemplateRepository templateRepository;
    private ImportExcelTool tool;

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
        this.tool = new ImportExcelTool(this);
        templateRepository = new TemplateRepository(context.getSettingsFolder());
    }

    /** To register all menu commands from this plugin
     *
     * @param mainFrame
     * @return all available commands from this plugin
     */
    @Override
    public List<MenuCommand> getCommands(MainframeContext mainFrame) {
        return Arrays.asList(
                new ExcelImportMenuCommand(mainFrame, this),
                new ExcelSettingsMenuCommand(mainFrame)
        );
    }

    /**
     * To register all mcps tools from this plugin.
     * (MCP Tools are AI compatible, but usable for workflows, too.)
     *
     * @return all available mcps tools from this plugin
     */
    @Override
    public List<McpTool> getTools() {
        return Collections.singletonList(tool);
    }

    public MainframeContext getContext() { return context; }
    public Component getMainFrame() { return context.getMainFrame(); }

    public void onImport() {
        ExcelImportController.handleImport(this);
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


    @Deprecated
    void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    @Deprecated
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
}
