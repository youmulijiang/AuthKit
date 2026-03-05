package view.component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * 数据表面板
 * 位于左侧上方，展示鉴权比较数据列表。
 * 默认列: # / Method / URL / Original / Unauthorized
 * 支持动态添加/删除鉴权对象列（用户在 UserPanel 中添加/删除用户时联动）。
 */
public class DataTablePanel extends JPanel {

    /** 固定列（不可删除） */
    private static final String[] FIXED_COLUMNS = {"#", "Method", "URL"};

    /** 默认鉴权对象列 */
    private static final String[] DEFAULT_AUTH_COLUMNS = {"Original", "Unauthorized"};

    private final JTable tableData;
    private final DefaultTableModel tableModel;

    /** 当前所有鉴权对象列名（有序） */
    private final List<String> authColumns;

    private DataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableData = builder.tableData;
        this.authColumns = builder.authColumns;
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableData);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 添加一个鉴权对象列
     *
     * @param name 鉴权对象名称（如 "User1"）
     */
    public void addAuthColumn(String name) {
        if (authColumns.contains(name)) {
            return;
        }
        authColumns.add(name);
        rebuildColumns();
    }

    /**
     * 删除一个鉴权对象列
     *
     * @param name 鉴权对象名称
     */
    public void removeAuthColumn(String name) {
        if (!authColumns.remove(name)) {
            return;
        }
        rebuildColumns();
    }

    /** 重建表格列（保留数据行） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rebuildColumns() {
        Vector dataVector = tableModel.getDataVector();
        Vector<Vector<Object>> dataCopy = new Vector<>();
        for (Object row : dataVector) {
            dataCopy.add(new Vector<>((Vector) row));
        }

        Vector<String> newColumns = buildColumnVector();
        tableModel.setColumnIdentifiers(newColumns);

        tableModel.setRowCount(0);
        for (Vector<Object> row : dataCopy) {
            Vector<Object> newRow = new Vector<>();
            for (int i = 0; i < newColumns.size(); i++) {
                newRow.add(i < row.size() ? row.get(i) : "");
            }
            tableModel.addRow(newRow);
        }
    }

    /** 构建完整列名向量 */
    private Vector<String> buildColumnVector() {
        Vector<String> columns = new Vector<>();
        for (String col : FIXED_COLUMNS) {
            columns.add(col);
        }
        columns.addAll(authColumns);
        return columns;
    }

    /** 获取数据表格 */
    public JTable getTableData() {
        return tableData;
    }

    /** 获取表格模型 */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    /** 获取当前鉴权对象列名列表 */
    public List<String> getAuthColumns() {
        return List.copyOf(authColumns);
    }

    /** 添加一行数据 */
    public void addRow(Object[] row) {
        tableModel.addRow(row);
    }

    /** 清空所有数据 */
    public void clearAll() {
        tableModel.setRowCount(0);
    }

    /** 获取选中行的索引 */
    public int getSelectedRow() {
        return tableData.getSelectedRow();
    }

    /**
     * 数据表面板建造器
     */
    public static class Builder {

        private DefaultTableModel tableModel;
        private JTable tableData;
        private List<String> authColumns;

        public Builder() {
            this.authColumns = new ArrayList<>();
            for (String col : DEFAULT_AUTH_COLUMNS) {
                authColumns.add(col);
            }

            Vector<String> allColumns = new Vector<>();
            for (String col : FIXED_COLUMNS) {
                allColumns.add(col);
            }
            allColumns.addAll(authColumns);

            this.tableModel = new DefaultTableModel(allColumns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            this.tableData = new JTable(this.tableModel);
            this.tableData.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            this.tableData.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        }

        /** 构建数据表面板 */
        public DataTablePanel build() {
            return new DataTablePanel(this);
        }
    }
}

