package de.bund.zrb.excel;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ExcelImportParser {

    public static String readExcelAsCsv(File excelFile, boolean evaluateFormulas) throws IOException, InvalidFormatException {
        Workbook workbook = new XSSFWorkbook(excelFile);
        Sheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = evaluateFormulas ? workbook.getCreationHelper().createFormulaEvaluator() : null;

        StringBuilder sb = new StringBuilder();
        for (Row row : sheet) {
            boolean first = true;
            for (Cell cell : row) {
                if (!first) sb.append(",");
                sb.append(getCellValue(cell, evaluator));
                first = false;
            }
            sb.append("\n");
        }
        workbook.close();
        return sb.toString();
    }

    public static Map<String, List<String>> readExcelAsTable(File excelFile, boolean evaluateFormulas)
            throws IOException, InvalidFormatException {
        return readExcelAsTable(excelFile, evaluateFormulas, -1);
    }

    public static Map<String, List<String>> readExcelAsTable(File excelFile, boolean evaluateFormulas, int headerRowIndex)
            throws IOException, InvalidFormatException {
        Workbook workbook = new XSSFWorkbook(excelFile);
        Sheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = evaluateFormulas ? workbook.getCreationHelper().createFormulaEvaluator() : null;

        Map<String, List<String>> table = new LinkedHashMap<>();

        if (headerRowIndex < 0) {
            // Ohne Headerzeile: Erzeuge generische Spaltennamen
            for (Row row : sheet) {
                for (Cell cell : row) {
                    int col = cell.getColumnIndex();
                    String key = "Spalte " + (col + 1);
                    table.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(getCellValue(cell, evaluator));
                }
            }
        } else {
            // Mit Headerzeile
            Map<Integer, String> columnKeys = new LinkedHashMap<>();
            for (Row row : sheet) {
                int rowIndex = row.getRowNum();
                if (rowIndex < headerRowIndex) continue;

                if (rowIndex == headerRowIndex) {
                    for (Cell cell : row) {
                        String header = getCellValue(cell, evaluator);
                        columnKeys.put(cell.getColumnIndex(), header);
                        table.put(header, new ArrayList<>());
                    }
                    continue;
                }

                for (Cell cell : row) {
                    int col = cell.getColumnIndex();
                    String key = columnKeys.get(col);
                    if (key == null) continue;
                    String value = getCellValue(cell, evaluator);
                    table.get(key).add(value);
                }
            }
        }

        workbook.close();
        return table;
    }

    private static String getCellValue(Cell cell, FormulaEvaluator evaluator) {
        CellType type = cell.getCellType();

        if (type == CellType.FORMULA && evaluator != null) {
            type = evaluator.evaluateFormulaCell(cell);
        }

        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return DateUtil.isCellDateFormatted(cell)
                        ? cell.getDateCellValue().toString()
                        : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula(); // Fallback
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
