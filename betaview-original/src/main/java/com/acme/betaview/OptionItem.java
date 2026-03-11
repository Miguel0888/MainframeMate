package com.acme.betaview;

public final class OptionItem {

    private final String value;
    private final String label;

    public OptionItem(String value, String label) {
        this.value = value == null ? "" : value;
        this.label = label == null ? "" : label;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        // Render human readable entry in JComboBox
        return label;
    }
}