package de.zrb.bund.api;

import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.workflow.WorkflowRunner;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MainframeContext {

    Map<String, String> loadPluginSettings(String pluginKey);

    void savePluginSettings(String pluginKey, Map<String, String> settings);

    Optional<TabAdapter> getSelectedTab();
    void openFileTab(String content, String sentenceType);

    JFrame getMainFrame();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // For Extensions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    BookmarkManager getBookmarkManager();
    List<TabAdapter> getAllFileTabs();
    void focusFileTab(TabAdapter tab);

    void refresh();

    ToolRegistry getToolRegistry();

    SentenceTypeRegistry getSentenceTypeRegistry();
    ExpressionRegistry getExpressionRegistry();

    File getSettingsFolder();

    WorkflowRunner getWorkflowRunner();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // ToDo

}
