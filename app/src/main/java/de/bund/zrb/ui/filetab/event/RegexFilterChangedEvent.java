package de.bund.zrb.ui.filetab.event;

public class RegexFilterChangedEvent {
    public final String regex;

    public RegexFilterChangedEvent(String regex) {
        this.regex = regex;
    }
}
