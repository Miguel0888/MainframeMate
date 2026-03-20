package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.BodyElementType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for DOCX files using Apache POI.
 * Extracts structured text preserving headings, tables, and paragraph structure
 * as Markdown for better rendering in the preview.
 */
public class DocxTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
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
        return "DocxTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        XWPFDocument document = null;
        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            document = new XWPFDocument(new ByteArrayInputStream(bytes));

            // Extract metadata
            if (document.getProperties() != null && document.getProperties().getCoreProperties() != null) {
                org.apache.poi.ooxml.POIXMLProperties.CoreProperties core = document.getProperties().getCoreProperties();
                if (core.getTitle() != null && !core.getTitle().isEmpty()) {
                    metadata.put("title", core.getTitle());
                }
                if (core.getCreator() != null && !core.getCreator().isEmpty()) {
                    metadata.put("author", core.getCreator());
                }
            }
            metadata.put("pages", String.valueOf(document.getProperties().getExtendedProperties().getUnderlyingProperties().getPages()));

            // Walk body elements in document order to preserve structure
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    XWPFParagraph para = (XWPFParagraph) element;
                    String text = para.getText();
                    if (text == null || text.trim().isEmpty()) {
                        sb.append('\n');
                        continue;
                    }

                    String style = para.getStyleID();
                    // Map heading styles to Markdown headings
                    if (style != null) {
                        String lower = style.toLowerCase();
                        if (lower.startsWith("heading") || lower.startsWith("berschrift")) {
                            int level = parseHeadingLevel(lower);
                            if (level > 0 && level <= 6) {
                                for (int i = 0; i < level; i++) sb.append('#');
                                sb.append(' ');
                                sb.append(text.trim());
                                sb.append("\n\n");
                                continue;
                            }
                        }
                        // Detect list items
                        if (lower.contains("listparagraph") || lower.contains("list")
                                || para.getNumID() != null) {
                            sb.append("- ").append(text.trim()).append('\n');
                            continue;
                        }
                    }
                    // Also detect numbered/bullet via numID
                    if (para.getNumID() != null) {
                        sb.append("- ").append(text.trim()).append('\n');
                        continue;
                    }

                    sb.append(text.trim()).append("\n\n");

                } else if (element.getElementType() == BodyElementType.TABLE) {
                    XWPFTable table = (XWPFTable) element;
                    renderTable(table, sb);
                    sb.append('\n');
                }
            }

            String result = sb.toString().trim();
            if (result.isEmpty()) {
                warnings.add("Dokument enthält keinen Text");
                return ExtractionResult.success("", warnings, metadata, getName());
            }

            return ExtractionResult.success(result, warnings, metadata, getName());

        } catch (IOException e) {
            return ExtractionResult.failure("Fehler beim Lesen der DOCX-Datei: " + e.getMessage(), getName());
        } catch (Exception e) {
            return ExtractionResult.failure("Unerwarteter Fehler bei der DOCX-Verarbeitung: " + e.getMessage(), getName());
        } finally {
            if (document != null) {
                try { document.close(); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * Parse heading level from a style name like "Heading1", "heading2", "berschrift3".
     */
    private int parseHeadingLevel(String styleLower) {
        for (int i = styleLower.length() - 1; i >= 0; i--) {
            char c = styleLower.charAt(i);
            if (c >= '1' && c <= '6') {
                return c - '0';
            }
            if (!Character.isDigit(c)) break;
        }
        return 1; // Default: H1
    }

    /**
     * Render a table as Markdown.
     */
    private void renderTable(XWPFTable table, StringBuilder sb) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;

        // Determine column count from widest row
        int maxCols = 0;
        for (XWPFTableRow row : rows) {
            int cols = row.getTableCells().size();
            if (cols > maxCols) maxCols = cols;
        }

        boolean first = true;
        for (XWPFTableRow row : rows) {
            sb.append('|');
            List<XWPFTableCell> cells = row.getTableCells();
            for (int c = 0; c < maxCols; c++) {
                String cellText = c < cells.size() ? cells.get(c).getText() : "";
                if (cellText == null) cellText = "";
                sb.append(' ').append(cellText.trim().replace('|', '¦')).append(" |");
            }
            sb.append('\n');

            // Separator row after header
            if (first) {
                sb.append('|');
                for (int c = 0; c < maxCols; c++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
                first = false;
            }
        }
    }
}
