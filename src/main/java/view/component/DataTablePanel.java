package view.component;

import utils.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final JPopupMenu selectionPopupMenu;
    private final JMenuItem itemExportCsv;
    private final JMenuItem itemExportHtml;
    private final JMenuItem itemCopyUrls;

    /** 当前所有鉴权对象列名（有序） */
    private final List<String> authColumns;

    /** 选中行右键动作处理器，由协调层注入。 */
    private SelectionActionHandler selectionActionHandler;

    /** 数据提供器：根据行索引（model index）返回对应的可搜索文本 */
    private Function<Integer, String> dataProvider;

    /** 是否仅显示越权的行 */
    private boolean unauthorizedOnly;

    /** 当前筛选类型 */
    private String currentFilterType;
    /** 当前筛选关键字 */
    private String currentKeyword;

    private DataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableData = builder.tableData;
        this.authColumns = builder.authColumns;
        this.selectionPopupMenu = new JPopupMenu();
        this.itemExportCsv = new JMenuItem();
        this.itemExportHtml = new JMenuItem();
        this.itemCopyUrls = new JMenuItem();
        this.rowSorter = new TableRowSorter<>(tableModel);
        this.tableData.setRowSorter(rowSorter);
        initLayout();
        initSelectionPopupMenu();
        I18n.getInstance().addLanguageChangeListener(() -> {
            rebuildColumns();
            refreshContextMenuTexts();
        });
        rebuildColumns();
        refreshContextMenuTexts();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableData);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** 初始化选中行右键菜单。 */
    private void initSelectionPopupMenu() {
        itemExportCsv.addActionListener(e -> triggerSelectionAction(SelectionAction.EXPORT_CSV));
        itemExportHtml.addActionListener(e -> triggerSelectionAction(SelectionAction.EXPORT_HTML));
        itemCopyUrls.addActionListener(e -> triggerSelectionAction(SelectionAction.COPY_URLS));
        selectionPopupMenu.add(itemExportCsv);
        selectionPopupMenu.add(itemExportHtml);
        selectionPopupMenu.addSeparator();
        selectionPopupMenu.add(itemCopyUrls);

        tableData.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showSelectionPopupIfNeeded(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showSelectionPopupIfNeeded(e);
            }
        });
    }

    private void showSelectionPopupIfNeeded(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int viewRow = tableData.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        if (!tableData.isRowSelected(viewRow)) {
            tableData.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
        selectionPopupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void refreshContextMenuTexts() {
        I18n i18n = I18n.getInstance();
        itemExportCsv.setText(i18n.text("data_table", "menu.exportCsv"));
        itemExportHtml.setText(i18n.text("data_table", "menu.exportHtml"));
        itemCopyUrls.setText(i18n.text("data_table", "menu.copyUrls"));
    }

    private void triggerSelectionAction(SelectionAction action) {
        if (selectionActionHandler == null) {
            return;
        }
        List<Integer> modelRows = getSelectedRows();
        if (modelRows.isEmpty()) {
            return;
        }
        switch (action) {
            case EXPORT_CSV -> selectionActionHandler.exportCsv(modelRows);
            case EXPORT_HTML -> selectionActionHandler.exportHtml(modelRows);
            case COPY_URLS -> selectionActionHandler.copyUrls(modelRows);
        }
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

    /** 获取所有选中行的索引（返回 model 索引，按当前视图顺序）。 */
    public List<Integer> getSelectedRows() {
        int[] viewRows = tableData.getSelectedRows();
        List<Integer> modelRows = new ArrayList<>(viewRows.length);
        for (int viewRow : viewRows) {
            modelRows.add(tableData.convertRowIndexToModel(viewRow));
        }
        return modelRows;
    }

    /** 设置选中行右键动作处理器。 */
    public void setSelectionActionHandler(SelectionActionHandler handler) {
        this.selectionActionHandler = handler;
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
     * 设置是否仅显示越权的行
     *
     * @param unauthorizedOnly true 表示仅显示存在越权（鉴权对象列与 Original 相同）的行
     */
    public void setUnauthorizedOnly(boolean unauthorizedOnly) {
        this.unauthorizedOnly = unauthorizedOnly;
        updateRowFilter();
    }

    /**
     * 应用筛选
     *
     * @param filterType 筛选类型（All / Length / Hash / Host / Request Content / Response Content）
     * @param keyword    筛选关键字
     */
    public void applyFilter(String filterType, String keyword) {
        this.currentFilterType = filterType;
        this.currentKeyword = keyword;
        updateRowFilter();
    }

    /** 统一更新 RowFilter，组合关键字过滤和差异过滤 */
    private void updateRowFilter() {
        boolean hasKeyword = currentKeyword != null && !currentKeyword.trim().isEmpty();

        if (!hasKeyword && !unauthorizedOnly) {
            rowSorter.setRowFilter(null);
            return;
        }

        String lowerKeyword = hasKeyword ? currentKeyword.trim().toLowerCase() : null;
        String filterType = currentFilterType;

        rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                // 越权过滤：检查是否存在与 Original 相同的鉴权对象列（即越权）
                if (unauthorizedOnly && !hasAnyUnauthorized(entry)) {
                    return false;
                }

                // 关键字过滤
                if (lowerKeyword == null) {
                    return true;
                }

                int modelRow = entry.getIdentifier();
                switch (filterType) {
                    case ToolbarPanel.FILTER_ALL:
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
                        for (int i = FIXED_COLUMN_COUNT; i < entry.getValueCount(); i++) {
                            String cellValue = String.valueOf(entry.getValue(i));
                            if (cellValue.toLowerCase().contains(lowerKeyword)) {
                                return true;
                            }
                        }
                        return false;

                    case ToolbarPanel.FILTER_HOST:
                        if (dataProvider != null) {
                            String hostText = dataProvider.apply(modelRow);
                            return hostText != null && hostText.toLowerCase().contains(lowerKeyword);
                        }
                        return false;

                    case ToolbarPanel.FILTER_REQUEST_CONTENT:
                    case ToolbarPanel.FILTER_RESPONSE_CONTENT:
                        if (dataProvider != null) {
                            String text = dataProvider.apply(modelRow);
                            return text != null && text.toLowerCase().contains(lowerKeyword);
                        }
                        return false;

                    default:
                        return true;
                }
            }

            /** 检查该行是否存在越权：任一鉴权对象列的值与 Original 相同即为越权 */
            private boolean hasAnyUnauthorized(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                if (entry.getValueCount() <= FIXED_COLUMN_COUNT + 1) {
                    return false;
                }
                Object originalValue = entry.getValue(FIXED_COLUMN_COUNT);
                for (int i = FIXED_COLUMN_COUNT + 1; i < entry.getValueCount(); i++) {
                    Object cellValue = entry.getValue(i);
                    if (Objects.equals(originalValue, cellValue)) {
                        return true;
                    }
                }
                return false;
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
            this.tableData.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            this.tableData.setRowSelectionAllowed(true);
            this.tableData.setColumnSelectionAllowed(false);
            this.tableData.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            // 自定义 CellRenderer：鉴权对象列值与 Original 不同时染浅红色
            this.tableData.setDefaultRenderer(Object.class, new AuthDiffCellRenderer());
        }

        /** 构建数据表面板 */
        public DataTablePanel build() {
            return new DataTablePanel(this);
        }
    }

    private enum SelectionAction {
        EXPORT_CSV,
        EXPORT_HTML,
        COPY_URLS
    }

    public interface SelectionActionHandler {
        void exportCsv(List<Integer> modelRows);

        void exportHtml(List<Integer> modelRows);

        void copyUrls(List<Integer> modelRows);
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

