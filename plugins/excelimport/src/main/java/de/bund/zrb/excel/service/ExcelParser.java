package de.bund.zrb.excel.service;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ExcelParser {

    public static Map<String, List<String>> readExcelAsTable(File excelFile, boolean evaluateFormulas, int headerRowIndex, boolean stopOnEmptyCell, boolean stopOnEmptyLine)
            throws IOException, InvalidFormatException {
        Workbook workbook = new XSSFWorkbook(excelFile);
        Sheet sheet = workbook.getSheetAt(0);
        FormulaEvaluator evaluator = evaluateFormulas ? workbook.getCreationHelper().createFormulaEvaluator() : null;

        Map<String, List<String>> table = new LinkedHashMap<>();

        if (headerRowIndex < 0) {
            // No header: Generate generic column names
            for (Row row : sheet) {
                if (shouldStop(row, evaluator, stopOnEmptyCell, stopOnEmptyLine)) break;
                for (Cell cell : row) {
                    int col = cell.getColumnIndex();
                    String key = "Spalte " + (col + 1);
                    table.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(getCellValue(cell, evaluator));
                }
            }
        } else {
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

                if (shouldStop(row, evaluator, stopOnEmptyCell, stopOnEmptyLine)) break;

                for (Map.Entry<Integer, String> entry : columnKeys.entrySet()) {
                    int col = entry.getKey();
                    String key = entry.getValue();
                    Cell cell = row.getCell(col);
                    String value = getCellValue(cell, evaluator);
                    table.get(key).add(value);
                }
            }
        }

        workbook.close();
        return table;
    }

    private static boolean shouldStop(Row row, FormulaEvaluator evaluator, boolean stopOnEmptyCell, boolean stopOnEmptyLine) {
        boolean allEmpty = true;

        for (Cell cell : row) {
            String value = getCellValue(cell, evaluator);
            if (!value.trim().isEmpty()) {
                allEmpty = false;
                if (stopOnEmptyCell) return false; // at least one non-empty → continue
            } else if (stopOnEmptyCell) {
                return true; // first empty cell triggers stop
            }
        }

        return stopOnEmptyLine && allEmpty;
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
