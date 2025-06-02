package de.zrb.bund.newApi.sentence;

import java.util.List;

public class SentenceDefinition {

    private SentenceMeta meta;
    private List<SentenceField> fields;

    public SentenceMeta getMeta() {
        return meta;
    }

    public void setMeta(SentenceMeta meta) {
        this.meta = meta;
    }

    public List<SentenceField> getFields() {
        return fields;
    }

    public void setFields(List<SentenceField> fields) {
        this.fields = fields;
    }

    /** Calculate the maximum row count based on the fields
     * This method iterates through the fields and determines the highest row index.
     * @return The total number of rows, which is the highest row index plus one.
     */
    public Integer getRowCount() {
        int maxRow = 0;
        if (fields == null || fields.isEmpty()) {
            return null; // No fields, no rows
        }
        for (SentenceField field : fields) {
            if (field.getRow() != null && field.getRow() > maxRow) {
                maxRow = field.getRow();
            }
        }
        return maxRow + 1; // Rows are 0-indexed, so add 1 for count
    }
}