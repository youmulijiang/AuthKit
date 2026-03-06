package view.component;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Function;

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
    private final TableRowSorter<DefaultTableModel> rowSorter;

    /** 当前所有鉴权对象列名（有序） */
    private final List<String> authColumns;

    /** 数据提供器：根据行索引（model index）返回对应的可搜索文本 */
    private Function<Integer, String> dataProvider;

    private DataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableData = builder.tableData;
        this.authColumns = builder.authColumns;
        this.rowSorter = new TableRowSorter<>(tableModel);
        this.tableData.setRowSorter(rowSorter);
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

    /**
     * 重命名一个鉴权对象列
     *
     * @param oldName 旧名称
     * @param newName 新名称
     */
    public void renameAuthColumn(String oldName, String newName) {
        int index = authColumns.indexOf(oldName);
        if (index < 0) {
            return;
        }
        authColumns.set(index, newName);
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

    /** 获取选中行的索引（返回 model 索引） */
    public int getSelectedRow() {
        int viewRow = tableData.getSelectedRow();
        if (viewRow < 0) {
            return -1;
        }
        return tableData.convertRowIndexToModel(viewRow);
    }

    /**
     * 设置数据提供器，用于筛选时获取行对应的可搜索文本
     *
     * @param provider 根据 model 行索引返回对应文本的函数
     */
    public void setDataProvider(Function<Integer, String> provider) {
        this.dataProvider = provider;
    }

    /**
     * 应用筛选
     *
     * @param filterType 筛选类型（All / Length / Hash / Request Content / Response Content）
     * @param keyword    筛选关键字
     */
    public void applyFilter(String filterType, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            rowSorter.setRowFilter(null);
            return;
        }

        String lowerKeyword = keyword.trim().toLowerCase();

        rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                int modelRow = entry.getIdentifier();

                switch (filterType) {
                    case "All":
                        // 搜索所有可见列 + 数据提供器的文本
                        for (int i = 0; i < entry.getValueCount(); i++) {
                            String cellValue = String.valueOf(entry.getValue(i));
                            if (cellValue.toLowerCase().contains(lowerKeyword)) {
                                return true;
                            }
                        }
                        if (dataProvider != null) {
                            String text = dataProvider.apply(modelRow);
                            if (text != null && text.toLowerCase().contains(lowerKeyword)) {
                                return true;
                            }
                        }
                        return false;

                    case "Length":
                    case "Hash":
                        // 搜索鉴权对象列中的数值
                        for (int i = FIXED_COLUMNS.length; i < entry.getValueCount(); i++) {
                            String cellValue = String.valueOf(entry.getValue(i));
                            if (cellValue.toLowerCase().contains(lowerKeyword)) {
                                return true;
                            }
                        }
                        return false;

                    case "Request Content":
                    case "Response Content":
                        // 通过数据提供器搜索报文内容
                        if (dataProvider != null) {
                            String text = dataProvider.apply(modelRow);
                            return text != null && text.toLowerCase().contains(lowerKeyword);
                        }
                        return false;

                    default:
                        return true;
                }
            }
        });
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

            // 自定义 CellRenderer：鉴权对象列值与 Original 不同时染浅红色
            this.tableData.setDefaultRenderer(Object.class, new AuthDiffCellRenderer());
        }

        /** 构建数据表面板 */
        public DataTablePanel build() {
            return new DataTablePanel(this);
        }
    }

    /**
     * 自定义单元格渲染器
     * 非 Original 的鉴权对象列，值与 Original 列不同且非空时，背景染浅红色。
     * 通过列名动态查找 Original 列位置，不依赖固定索引。
     */
    private static class AuthDiffCellRenderer extends DefaultTableCellRenderer {

        private static final String ORIGINAL_COLUMN_NAME = "Original";
        private static final Color DIFF_COLOR = new Color(255, 204, 204);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);

            if (!isSelected) {
                c.setBackground(table.getBackground());

                String currentColName = table.getColumnName(column);
                // 当前列不是固定列且不是 Original 列，才需要比较染色
                if (!isFixedColumn(currentColName) && !ORIGINAL_COLUMN_NAME.equals(currentColName)) {
                    int originalColIndex = findColumnIndex(table, ORIGINAL_COLUMN_NAME);
                    if (originalColIndex >= 0 && !isEmpty(value)) {
                        Object originalValue = table.getModel().getValueAt(row, originalColIndex);
                        if (!isEmpty(originalValue) && !Objects.equals(value, originalValue)) {
                            c.setBackground(DIFF_COLOR);
                        }
                    }
                }
            }

            return c;
        }

        /** 判断是否为固定列（#/Method/URL） */
        private boolean isFixedColumn(String colName) {
            for (String fixed : FIXED_COLUMNS) {
                if (fixed.equals(colName)) {
                    return true;
                }
            }
            return false;
        }

        /** 根据列名查找列索引，找不到返回 -1 */
        private int findColumnIndex(JTable table, String columnName) {
            for (int i = 0; i < table.getColumnCount(); i++) {
                if (columnName.equals(table.getColumnName(i))) {
                    return i;
                }
            }
            return -1;
        }

        /** 判断值是否为空（null 或空字符串） */
        private boolean isEmpty(Object value) {
            return value == null || "".equals(value.toString().trim());
        }
    }
}

