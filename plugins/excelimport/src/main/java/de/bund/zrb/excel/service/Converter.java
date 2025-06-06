package de.bund.zrb.excel.service;

import de.bund.zrb.excel.model.ExcelMapping;
import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.FieldMap;
import de.zrb.bund.newApi.sentence.SentenceField;

import java.util.Map;
import java.util.function.Function;

public class Converter {

    private static final Converter instance = new Converter();
    private Converter() {}
    public static Converter getInstance() { return instance; }

    public String generateRecordLines(FieldMap fields,
                                      int schemaLines,
                                      ExcelMapping mapping,
                                      Function<String, String> valueProvider) {
        StringBuilder[] lines = new StringBuilder[schemaLines];
        for (int i = 0; i < schemaLines; i++) {
            lines[i] = new StringBuilder();
        }

        for (Map.Entry<FieldCoordinate, SentenceField> entry : fields.entrySet()) {
            FieldCoordinate coord = entry.getKey();
            SentenceField field = entry.getValue();

            int fieldRow = coord.getRow() - 1;
            int start = coord.getPosition() - 1;
            int len = field.getLength() != null ? field.getLength() : 0;

            if (fieldRow < 0 || fieldRow >= schemaLines || start < 0 || len <= 0) continue;

            String value = mapping.getContentForFieldName(field.getName(), valueProvider);
            String padded = padOrTruncate(value, len);

            StringBuilder line = lines[fieldRow];
            while (line.length() < start) {
                line.append(' ');
            }

            for (int i = 0; i < padded.length(); i++) {
                if (start + i < line.length()) {
                    line.setCharAt(start + i, padded.charAt(i));
                } else {
                    line.append(padded.charAt(i));
                }
            }
        }

        return String.join("\n", lines);
    }

    private String padOrTruncate(String input, int length) {
        if (input == null) input = "";
        if (input.length() > length) {
            return input.substring(0, length);
        }
        return String.format("%-" + length + "s", input);
    }
}
