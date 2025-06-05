package de.bund.zrb.helper;

import de.zrb.bund.newApi.sentence.SentenceField;
import de.zrb.bund.newApi.sentence.SentenceDefinition;

import java.util.*;
import java.util.stream.Collectors;

public final class SentenceFieldUtils {

    private SentenceFieldUtils() {
        // utility class
    }

    public static List<SentenceField> getSortedFieldsForRow(SentenceDefinition def, int rowIndex) {
        if (def == null || def.getFields() == null) return Collections.emptyList();

        return def.getFields().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // nach Position aufsteigend sortieren
                .map(Map.Entry::getValue)
                .filter(field -> {
                    Integer row = field.getRow();
                    return (row == null ? 0 : row - 1) == rowIndex;
                })
                .collect(Collectors.toList());
    }
}
