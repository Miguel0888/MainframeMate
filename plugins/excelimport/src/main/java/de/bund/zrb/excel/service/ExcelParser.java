package de.bund.zrb.excel.service;

import de.bund.zrb.excel.service.cache.ExcelReadMode;
import de.bund.zrb.excel.service.cache.ExcelRowSnapshot;
import de.bund.zrb.excel.service.cache.ExcelSheetKey;
import de.bund.zrb.excel.service.cache.ExcelSheetSnapshot;
import de.bund.zrb.excel.service.cache.ExcelSheetSnapshotCache;
import de.bund.zrb.excel.service.cache.ExcelTableCache;
import de.bund.zrb.excel.service.cache.ExcelTableKey;
import de.bund.zrb.excel.service.cache.LruExcelSheetSnapshotCache;
import de.bund.zrb.excel.service.cache.LruExcelTableCache;
import de.bund.zrb.excel.service.cache.SoftReferenceExcelSheetSnapshotCache;
import de.bund.zrb.excel.service.cache.SoftReferenceExcelTableCache;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class ExcelParser {

    private static final int CACHE_MAX_ENTRIES = Integer.getInteger("mainframemate.excelimport.cacheMaxEntries", 0);
    private static final ExcelTableCache CACHE = createTableCache(CACHE_MAX_ENTRIES);
    private static final ExcelSheetSnapshotCache SHEET_CACHE = createSheetCache(CACHE_MAX_ENTRIES);
    private static final Map<ExcelTableKey, Object> TABLE_LOAD_LOCKS = new ConcurrentHashMap<>();
    private static final Map<ExcelSheetKey, Object> SHEET_LOAD_LOCKS = new ConcurrentHashMap<>();

    public static Map<String, List<String>> readExcelAsTable(File excelFile, boolean evaluateFormulas, int headerRowIndex, boolean stopOnEmptyCell, boolean stopOnEmptyLine)
            throws IOException, InvalidFormatException {
        return readExcelAsTable(excelFile, evaluateFormulas, headerRowIndex, stopOnEmptyCell, stopOnEmptyLine, ExcelReadMode.USE_CACHE);
    }

    public static Map<String, List<String>> readExcelAsTable(File excelFile,
                                                            boolean evaluateFormulas,
                                                            int headerRowIndex,
                                                            boolean stopOnEmptyCell,
                                                            boolean stopOnEmptyLine,
                                                            ExcelReadMode readMode)
            throws IOException, InvalidFormatException {

        ExcelTableKey cacheKey = ExcelTableKey.from(excelFile, evaluateFormulas, headerRowIndex, stopOnEmptyCell, stopOnEmptyLine);

        if (readMode == ExcelReadMode.USE_CACHE) {
            Optional<Map<String, List<String>>> cached = CACHE.get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        } else {
            CACHE.invalidate(cacheKey);
        }

        Object tableLock = TABLE_LOAD_LOCKS.computeIfAbsent(cacheKey, key -> new Object());
        synchronized (tableLock) {
            try {
                if (readMode == ExcelReadMode.USE_CACHE) {
                    Optional<Map<String, List<String>>> cached = CACHE.get(cacheKey);
                    if (cached.isPresent()) {
                        return cached.get();
                    }
                }

                ExcelSheetSnapshot snapshot = readSheetSnapshot(excelFile, evaluateFormulas, readMode);
                Map<String, List<String>> table = buildTable(snapshot, headerRowIndex, stopOnEmptyCell, stopOnEmptyLine);

                Map<String, List<String>> result = toUnmodifiableSnapshot(table);
                CACHE.put(cacheKey, result);
                return result;
            } finally {
                TABLE_LOAD_LOCKS.remove(cacheKey, tableLock);
            }
        }
    }

    public static void clearCache() {
        CACHE.clear();
        SHEET_CACHE.clear();
    }

    private static ExcelSheetSnapshot readSheetSnapshot(File excelFile,
                                                        boolean evaluateFormulas,
                                                        ExcelReadMode readMode)
            throws IOException, InvalidFormatException {

        ExcelSheetKey sheetKey = ExcelSheetKey.from(excelFile, evaluateFormulas);

        if (readMode == ExcelReadMode.USE_CACHE) {
            Optional<ExcelSheetSnapshot> cached = SHEET_CACHE.get(sheetKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        } else {
            SHEET_CACHE.invalidate(sheetKey);
        }

        Object sheetLock = SHEET_LOAD_LOCKS.computeIfAbsent(sheetKey, key -> new Object());
        synchronized (sheetLock) {
            try {
                if (readMode == ExcelReadMode.USE_CACHE) {
                    Optional<ExcelSheetSnapshot> cached = SHEET_CACHE.get(sheetKey);
                    if (cached.isPresent()) {
                        return cached.get();
                    }
                }

                List<ExcelRowSnapshot> rows = new ArrayList<>();
                try (Workbook workbook = new XSSFWorkbook(excelFile)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    FormulaEvaluator evaluator = evaluateFormulas ? workbook.getCreationHelper().createFormulaEvaluator() : null;

                    for (Row row : sheet) {
                        rows.add(snapshotOf(row, evaluator));
                    }
                }

                ExcelSheetSnapshot snapshot = new ExcelSheetSnapshot(rows);
                SHEET_CACHE.put(sheetKey, snapshot);
                return snapshot;
            } finally {
                SHEET_LOAD_LOCKS.remove(sheetKey, sheetLock);
            }
        }
    }

    private static ExcelTableCache createTableCache(int maxEntries) {
        if (maxEntries == 0) {
            return new SoftReferenceExcelTableCache();
        }
        return new LruExcelTableCache(maxEntries);
    }

    private static ExcelSheetSnapshotCache createSheetCache(int maxEntries) {
        if (maxEntries == 0) {
            return new SoftReferenceExcelSheetSnapshotCache();
        }
        return new LruExcelSheetSnapshotCache(maxEntries);
    }

    private static ExcelRowSnapshot snapshotOf(Row row, FormulaEvaluator evaluator) {
        Map<Integer, String> cellsByColumn = new LinkedHashMap<>();
        for (Cell cell : row) {
            cellsByColumn.put(cell.getColumnIndex(), getCellValue(cell, evaluator));
        }
        return new ExcelRowSnapshot(row.getRowNum(), cellsByColumn);
    }

    private static Map<String, List<String>> buildTable(ExcelSheetSnapshot snapshot,
                                                        int headerRowIndex,
                                                        boolean stopOnEmptyCell,
                                                        boolean stopOnEmptyLine) {

        Map<String, List<String>> table = new LinkedHashMap<>();

        if (headerRowIndex < 0) {
            buildTableWithoutHeader(snapshot, stopOnEmptyCell, stopOnEmptyLine, table);
            return table;
        }

        buildTableWithHeader(snapshot, headerRowIndex, stopOnEmptyCell, stopOnEmptyLine, table);
        return table;
    }

    private static void buildTableWithoutHeader(ExcelSheetSnapshot snapshot,
                                                boolean stopOnEmptyCell,
                                                boolean stopOnEmptyLine,
                                                Map<String, List<String>> table) {
        for (ExcelRowSnapshot row : snapshot.getRows()) {
            if (shouldStop(row, stopOnEmptyCell, stopOnEmptyLine)) {
                break;
            }
            for (Map.Entry<Integer, String> cell : row.getCellsByColumn().entrySet()) {
                int col = cell.getKey();
                String key = "Spalte " + (col + 1);
                table.computeIfAbsent(key, k -> new ArrayList<>()).add(cell.getValue());
            }
        }
    }

    private static void buildTableWithHeader(ExcelSheetSnapshot snapshot,
                                             int headerRowIndex,
                                             boolean stopOnEmptyCell,
                                             boolean stopOnEmptyLine,
                                             Map<String, List<String>> table) {

        Map<Integer, String> columnKeys = new LinkedHashMap<>();

        for (ExcelRowSnapshot row : snapshot.getRows()) {
            int rowIndex = row.getRowIndex();
            if (rowIndex < headerRowIndex) {
                continue;
            }

            if (rowIndex == headerRowIndex) {
                for (Map.Entry<Integer, String> cell : row.getCellsByColumn().entrySet()) {
                    String header = cell.getValue();
                    columnKeys.put(cell.getKey(), header);
                    table.put(header, new ArrayList<>());
                }
                continue;
            }

            if (shouldStop(row, stopOnEmptyCell, stopOnEmptyLine)) {
                break;
            }

            for (Map.Entry<Integer, String> entry : columnKeys.entrySet()) {
                int col = entry.getKey();
                String key = entry.getValue();
                table.get(key).add(row.getCellValueOrEmpty(col));
            }
        }
    }

    private static boolean shouldStop(ExcelRowSnapshot row, boolean stopOnEmptyCell, boolean stopOnEmptyLine) {
        boolean allEmpty = true;

        for (String value : row.getCellsByColumn().values()) {
            if (!value.trim().isEmpty()) {
                allEmpty = false;
                if (stopOnEmptyCell) {
                    return false;
                }
            } else if (stopOnEmptyCell) {
                return true;
            }
        }

        return stopOnEmptyLine && allEmpty;
    }

    private static String getCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

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

    private static Map<String, List<String>> toUnmodifiableSnapshot(Map<String, List<String>> table) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
