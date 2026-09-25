package com.myyuyin.assistant.client;

import com.myyuyin.assistant.common.ConfigItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ConfigTableModel extends AbstractTableModel {
    private final String[] columns = {"参数", "值", "类型", "说明", "可编辑"};
    private final List<ConfigItem> items = new ArrayList<>();

    public void setItems(List<ConfigItem> values) {
        items.clear();
        items.addAll(values);
        fireTableDataChanged();
    }

    public ConfigItem itemAt(int row) {
        return items.get(row);
    }

    public List<ConfigItem> items() {
        return List.copyOf(items);
    }

    @Override
    public int getRowCount() {
        return items.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        ConfigItem item = items.get(row);
        return switch (column) {
            case 0 -> item.key();
            case 1 -> item.value();
            case 2 -> item.valueType();
            case 3 -> item.description();
            case 4 -> item.editable();
            default -> null;
        };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == 1 && items.get(row).editable();
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column != 1 || !items.get(row).editable()) {
            return;
        }
        ConfigItem old = items.get(row);
        items.set(row, new ConfigItem(old.key(), String.valueOf(value), old.valueType(),
                old.description(), old.editable()));
        fireTableCellUpdated(row, column);
    }
}
