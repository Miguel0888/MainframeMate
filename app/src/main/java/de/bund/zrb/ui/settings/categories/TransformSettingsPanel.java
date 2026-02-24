package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.model.FileEndingOption;
import de.bund.zrb.model.LineEndingOption;
import de.bund.zrb.model.PaddingOption;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;

public class TransformSettingsPanel extends AbstractSettingsPanel {

    private final JComboBox<String> encodingCombo;
    private final JComboBox<String> lineEndingBox;
    private final JCheckBox stripFinalNewlineBox;
    private final JComboBox<String> endMarkerBox;
    private final JComboBox<String> paddingBox;

    public TransformSettingsPanel() {
        super("transform", "Datenumwandlung");
        FormBuilder fb = new FormBuilder();

        encodingCombo = new JComboBox<>();
        SettingsHelper.SUPPORTED_ENCODINGS.forEach(encodingCombo::addItem);
        encodingCombo.setSelectedItem(settings.encoding != null ? settings.encoding : "windows-1252");
        fb.addRowHelp("Zeichenkodierung:", encodingCombo, HelpContentProvider.HelpTopic.TRANSFORM_ENCODING);

        lineEndingBox = LineEndingOption.createLineEndingComboBox(settings.lineEnding);
        fb.addRowHelp("Zeilenumbruch des Servers:", lineEndingBox, HelpContentProvider.HelpTopic.TRANSFORM_LINE_ENDING);

        stripFinalNewlineBox = new JCheckBox("Letzten Zeilenumbruch ausblenden");
        stripFinalNewlineBox.setSelected(settings.removeFinalNewline);
        fb.addWideHelp(stripFinalNewlineBox, HelpContentProvider.HelpTopic.TRANSFORM_STRIP_NEWLINE);

        endMarkerBox = FileEndingOption.createEndMarkerComboBox(settings.fileEndMarker);
        fb.addRowHelp("Datei-Ende-Kennung:", endMarkerBox, HelpContentProvider.HelpTopic.TRANSFORM_EOF_MARKER);

        paddingBox = PaddingOption.createPaddingComboBox(settings.padding);
        fb.addRowHelp("Padding Byte:", paddingBox, HelpContentProvider.HelpTopic.TRANSFORM_PADDING);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.encoding = (String) encodingCombo.getSelectedItem();
        s.lineEnding = LineEndingOption.normalizeInput(lineEndingBox.getSelectedItem());
        s.removeFinalNewline = stripFinalNewlineBox.isSelected();
        s.fileEndMarker = FileEndingOption.normalizeInput(endMarkerBox.getSelectedItem());
        s.padding = PaddingOption.normalizeInput(paddingBox.getSelectedItem());
    }
}

