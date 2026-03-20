package de.bund.zrb.ui.commands.sub;

import de.zrb.bund.api.MainframeContext;
import de.zrb.bund.api.ShortcutMenuCommand;
import de.zrb.bund.newApi.ui.AppTab;

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
        return "\u2315 Suche fokussieren";
    }

    @Override
    public void perform() {
        context.getSelectedTab().ifPresent(tab -> {
            if (tab instanceof AppTab) {
                ((AppTab) tab).focusSearchField();
            }
        });
    }
}
