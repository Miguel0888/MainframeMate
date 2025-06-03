package de.bund.zrb.excel.model;

import java.util.function.Function;

public class ExcelMappingEntry {

    private String excelColumn;         // Optional: Name der Spalte in Excel (kann null sein, wenn fix)
    private String fixedValue;          // Optional: fixer Wert
    private String expression;          // Optional: z. B. ${today:yyyyMMdd} oder ein JS-Ausdruck
    private String fieldName;           // Name des Ziel-Feldes in der Satzart

    public String getExcelColumn() {
        return excelColumn;
    }

    public void setExcelColumn(String excelColumn) {
        this.excelColumn = excelColumn;
    }

    public String getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

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

    // Optional: Hilfsinfo zur Darstellung
    public boolean isDynamic() {
        return expression != null && !expression.trim().isEmpty();
    }

    public boolean isFixed() {
        return fixedValue != null && !fixedValue.trim().isEmpty();
    }

    public boolean isFromColumn() {
        return excelColumn != null && !excelColumn.trim().isEmpty();
    }
}
