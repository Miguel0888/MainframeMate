package de.bund.zrb.excel.model;

import java.util.function.Function;

public class ExcelMappingEntry {

    private String expression;          // Optional: z. B. ${today:yyyyMMdd} oder ein JS-Ausdruck
    private String fieldName;           // Name des Ziel-Feldes in der Satzart

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getRawValue() {
        return expression;
    }
}
