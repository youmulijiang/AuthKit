package view.component;

import utils.I18n;

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

    private static final int FIXED_COLUMN_COUNT = 3;
    private static final String[] INITIAL_FIXED_COLUMNS = {"#", "Method", "URL"};

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
        I18n.getInstance().addLanguageChangeListener(this::rebuildColumns);
        rebuildColumns();
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
        columns.add(I18n.getInstance().text("data_table", "column.id"));
        columns.add(I18n.getInstance().text("data_table", "column.method"));
        columns.add(I18n.getInstance().text("data_table", "column.url"));
        for (String authColumn : authColumns) {
            columns.add(I18n.getInstance().translateAuthObjectName(authColumn));
        }
        return columns;
    }

    public String getAuthColumnKeyAtModelIndex(int modelColumn) {
        int authIndex = modelColumn - FIXED_COLUMN_COUNT;
        if (authIndex < 0 || authIndex >= authColumns.size()) {
            return null;
        }
        return authColumns.get(authIndex);
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
                    case ToolbarPanel.FILTER_ALL:
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

                    case ToolbarPanel.FILTER_LENGTH:
                    case ToolbarPanel.FILTER_HASH:
                        // 搜索鉴权对象列中的数值
                        for (int i = FIXED_COLUMN_COUNT; i < entry.getValueCount(); i++) {
                            String cellValue = String.valueOf(entry.getValue(i));
                            if (cellValue.toLowerCase().contains(lowerKeyword)) {
                                return true;
                            }
                        }
                        return false;

                    case ToolbarPanel.FILTER_REQUEST_CONTENT:
                    case ToolbarPanel.FILTER_RESPONSE_CONTENT:
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
            for (String col : INITIAL_FIXED_COLUMNS) {
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
     */
    private static class AuthDiffCellRenderer extends DefaultTableCellRenderer {

        private static final Color DIFF_COLOR = new Color(255, 204, 204);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);

            if (!isSelected) {
                c.setBackground(table.getBackground());

                int modelColumn = table.convertColumnIndexToModel(column);
                if (modelColumn > FIXED_COLUMN_COUNT && !isEmpty(value)) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Object originalValue = table.getModel().getValueAt(modelRow, FIXED_COLUMN_COUNT);
                    if (!isEmpty(originalValue) && !Objects.equals(value, originalValue)) {
                        c.setBackground(DIFF_COLOR);
                    }
                }
            }

            return c;
        }

        /** 判断值是否为空（null 或空字符串） */
        private boolean isEmpty(Object value) {
            return value == null || "".equals(value.toString().trim());
        }
    }
}

