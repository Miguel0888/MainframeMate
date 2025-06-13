package de.zrb.bund.api;

import de.zrb.bund.newApi.ToolRegistry;
import de.zrb.bund.newApi.ui.FileTab;
import de.zrb.bund.newApi.ui.FtpTab;
import de.zrb.bund.newApi.workflow.WorkflowRunner;

import javax.annotation.Nullable;
import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MainframeContext {

    Map<String, String> loadPluginSettings(String pluginKey);

    void savePluginSettings(String pluginKey, Map<String, String> settings);

    Optional<Bookmarkable> getSelectedTab();
    FileTab createFile(String content, String sentenceType);

    FtpTab openFileOrDirectory(String path);

    FtpTab openFileOrDirectory(String path, @Nullable String sentenceType);

    FtpTab openFileOrDirectory(String path, @Nullable String sentenceType, String searchPattern);

    FtpTab openFileOrDirectory(String path, @Nullable String sentenceType, String searchPattern, Boolean toCompare);

    JFrame getMainFrame();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // For Extensions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    BookmarkManager getBookmarkManager();
    List<Bookmarkable> getAllFileTabs();
    void focusFileTab(Bookmarkable tab);

    void refresh();

    ToolRegistry getToolRegistry();

    SentenceTypeRegistry getSentenceTypeRegistry();
    ExpressionRegistry getExpressionRegistry();

    File getSettingsFolder();

    WorkflowRunner getWorkflowRunner();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // ToDo

}
