package de.bund.zrb.ui.commands;

public class ExitCommand implements Command {

    @Override
    public String getId() {
        return "file.exit";
    }

    @Override
    public String getLabel() {
        return "Beenden";
    }

    @Override
    public void perform() {
        System.exit(0);
    }
}
