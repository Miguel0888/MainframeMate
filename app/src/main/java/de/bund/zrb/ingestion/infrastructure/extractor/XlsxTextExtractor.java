package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for Excel files (XLS/XLSX) using Apache POI.
 * Preserves the table/sheet structure by rendering each sheet as a Markdown table.
 */
public class XlsxTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",  // .xlsx
            "application/vnd.ms-excel"                                             // .xls
    );

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return SUPPORTED_MIME_TYPES.contains(baseMime);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "XlsxTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        Workbook workbook = null;
        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes));
            DataFormatter dataFormatter = new DataFormatter();
            FormulaEvaluator evaluator;
            try {
                evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            } catch (Exception e) {
                // Some workbooks don't support formula evaluation
                evaluator = null;
                warnings.add("Formeln konnten nicht ausgewertet werden");
            }

            int sheetCount = workbook.getNumberOfSheets();
            metadata.put("sheets", String.valueOf(sheetCount));

            StringBuilder sb = new StringBuilder();

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                // Sheet heading
                if (sheetCount > 1) {
                    sb.append("## ").append(sheetName != null ? sheetName : "Sheet " + (s + 1)).append("\n\n");
                }

                int firstRow = sheet.getFirstRowNum();
                int lastRow = sheet.getLastRowNum();

                if (firstRow < 0 || lastRow < 0) {
                    sb.append("*Leeres Blatt*\n\n");
                    continue;
                }

                // Determine the maximum column index across all rows
                int maxCol = 0;
                for (int r = firstRow; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getLastCellNum() > maxCol) {
                        maxCol = row.getLastCellNum();
                    }
                }

                if (maxCol == 0) {
                    sb.append("*Leeres Blatt*\n\n");
                    continue;
                }

                // Cap columns to avoid huge tables
                if (maxCol > 50) {
                    maxCol = 50;
                    warnings.add("Blatt '" + sheetName + "': Spalten auf 50 begrenzt");
                }

                // Cap rows
                int rowLimit = Math.min(lastRow, firstRow + 5000);
                if (lastRow > rowLimit) {
                    warnings.add("Blatt '" + sheetName + "': Zeilen auf 5000 begrenzt");
                }

                boolean headerDone = false;
                for (int r = firstRow; r <= rowLimit; r++) {
                    Row row = sheet.getRow(r);
                    sb.append('|');
                    for (int c = 0; c < maxCol; c++) {
                        String cellValue = "";
                        if (row != null) {
                            Cell cell = row.getCell(c);
                            if (cell != null) {
                                cellValue = formatCell(cell, dataFormatter, evaluator);
                            }
                        }
                        sb.append(' ').append(cellValue.replace('|', '¦').replace('\n', ' ')).append(" |");
                    }
                    sb.append('\n');

                    // Separator row after first row (header)
                    if (!headerDone) {
                        sb.append('|');
                        for (int c = 0; c < maxCol; c++) {
                            sb.append(" --- |");
                        }
                        sb.append('\n');
                        headerDone = true;
                    }
                }
                sb.append('\n');
            }

            String result = sb.toString().trim();
            if (result.isEmpty()) {
                warnings.add("Arbeitsmappe enthält keine Daten");
                return ExtractionResult.success("", warnings, metadata, getName());
            }

            return ExtractionResult.success(result, warnings, metadata, getName());

        } catch (IOException e) {
            return ExtractionResult.failure("Fehler beim Lesen der Excel-Datei: " + e.getMessage(), getName());
        } catch (Exception e) {
            return ExtractionResult.failure("Unerwarteter Fehler bei der Excel-Verarbeitung: " + e.getMessage(), getName());
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Format a cell value as a string, handling different cell types.
     */
    private String formatCell(Cell cell, DataFormatter dataFormatter, FormulaEvaluator evaluator) {
        try {
            if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
                try {
                    return dataFormatter.formatCellValue(cell, evaluator);
                } catch (Exception e) {
                    // Fall back to formula string
                    return "=" + cell.getCellFormula();
                }
            }
            return dataFormatter.formatCellValue(cell);
        } catch (Exception e) {
            return "";
        }
    }
}

