package de.bund.zrb.ui.commands.sub;

import de.bund.zrb.ui.FileTabImpl;

import de.bund.zrb.ui.TabbedPaneManager;
import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.FtpTab;

public class FocusSearchFieldCommand extends ShortcutMenuCommand {

    private final MainframeContext context;

    public FocusSearchFieldCommand(MainframeContext context) {
        this.context = context;
    }

    @Override
    public String getId() {
        return "edit.search";
    }

    @Override
    public String getLabel() {
        return "Suche fokussieren";
    }

    @Override
    public void perform() {
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof FtpTab) {
                ((FtpTab) tab).focusSearchField();
            }
        });
    }
}
