package de.bund.zrb.betaview.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;

public final class ResultsTablePanel extends JPanel {

    private final JTable table;
    private final HtmlResultsTableModel tableModel;

    public ResultsTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Results"));

        tableModel = new HtmlResultsTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void loadResults(String html) {
        tableModel.loadFromHtml(html);
    }

    public void addSelectionListener(ListSelectionListener listener) {
        table.getSelectionModel().addListSelectionListener(listener);
    }

    public int getSelectedRow() {
        return table.getSelectedRow();
    }

    public String getActionForSelectedRow() {
        int selectedRow = getSelectedRow();
        if (selectedRow >= 0) {
            return tableModel.getActionForRow(selectedRow);
        }
        return null;
    }

    public void clearSelection() {
        table.clearSelection();
    }
}


