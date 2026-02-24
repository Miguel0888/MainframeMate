package com.example.toolbarkit.config;

import com.example.toolbarkit.toolbar.ToolbarConfig;

/**
 * Load and save the toolbar configuration.
 */
public interface ToolbarConfigRepository {

    ToolbarConfig loadOrCreate(ConfigFactory defaultFactory);

    void save(ToolbarConfig config);

    interface ConfigFactory {
        ToolbarConfig createDefault();
    }
}
