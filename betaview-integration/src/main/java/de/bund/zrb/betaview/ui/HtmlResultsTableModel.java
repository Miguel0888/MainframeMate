package de.bund.zrb.betaview.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HtmlResultsTableModel extends AbstractTableModel {

    private final List<Map<String, String>> rows = new ArrayList<>();
    private final List<String> columnNames = new ArrayList<>();

    public void loadFromHtml(String html) {
        rows.clear();
        columnNames.clear();

        if (html == null || html.trim().isEmpty()) {
            fireTableStructureChanged();
            return;
        }

        Document doc = Jsoup.parse(html);
        Element table = doc.selectFirst("table");
        if (table == null) {
            fireTableStructureChanged();
            return;
        }

        Elements headers = table.select("thead th");
        if (headers.isEmpty()) {
            Element firstRow = table.selectFirst("tbody tr");
            if (firstRow != null) {
                Elements firstRowCells = firstRow.select("td");
                for (Element th : firstRowCells) {
                    columnNames.add(th.text().trim());
                }
            }
        } else {
            for (Element th : headers) {
                columnNames.add(th.text().trim());
            }
        }

        Elements tbody = table.select("tbody tr");
        for (Element tr : tbody) {
            Elements cells = tr.select("td");
            if (cells.isEmpty()) {
                continue;
            }

            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < cells.size() && i < columnNames.size(); i++) {
                Element cell = cells.get(i);
                String cellValue = extractCellValue(cell);
                row.put(columnNames.get(i), cellValue);
            }

            Element link = tr.selectFirst("a");
            if (link != null) {
                String href = link.attr("href");
                String onclick = link.attr("onclick");
                String actionLink = null;

                if (onclick != null && !onclick.isEmpty()) {
                    actionLink = extractActionFromOnclick(onclick);
                }
                if (actionLink == null && href != null && !href.isEmpty()) {
                    actionLink = href;
                }

                if (actionLink != null) {
                    // Decode HTML entities (&amp; -> &)
                    actionLink = org.jsoup.parser.Parser.unescapeEntities(actionLink, false);
                    actionLink = actionLink.trim();
                    row.put("__action__", actionLink);
                }
            }

            rows.add(row);
        }

        fireTableStructureChanged();
    }

    private String extractCellValue(Element cell) {
        Element link = cell.selectFirst("a");
        if (link != null) {
            return link.text().trim();
        }
        return cell.text().trim();
    }
    private String extractActionFromOnclick(String onclick) {
        // Extrahiere URL aus JavaScript-Funktionen
        // Beispiel: "javascript:bwe.result.openDocument('open.action?index=0&source=resulttable');"
        
        // Suche nach String in einfachen oder doppelten AnfÃ¼hrungszeichen
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("['\"]([^'\"]+\\.action[^'\"]*)['\"]");
        java.util.regex.Matcher matcher = pattern.matcher(onclick);
        if (matcher.find()) {
            String action = matcher.group(1);
            // Decode HTML entities (&amp; -> &)
            action = org.jsoup.parser.Parser.unescapeEntities(action, false);
            return action.trim();
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size() || columnIndex < 0 || columnIndex >= columnNames.size()) {
            return "";
        }
        Map<String, String> row = rows.get(rowIndex);
        String columnName = columnNames.get(columnIndex);
        return row.getOrDefault(columnName, "");
    }

    @Override
    public String getColumnName(int column) {
        if (column < 0 || column >= columnNames.size()) {
            return "";
        }
        return columnNames.get(column);
    }

    public String getActionForRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        return rows.get(rowIndex).get("__action__");
    }
}

