package view.component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 数据表面板
 * 位于左侧上方，展示鉴权比较数据列表。
 * 列: # / 类型 / URL / 原始包长度 / 低权限包长度 / 未授权包长度
 */
public class DataTablePanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "#", "类型", "URL", "原始包长度", "未授权包长度", "use1包长度"
    };

    private final JTable tableData;
    private final DefaultTableModel tableModel;

    private DataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableData = builder.tableData;
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableData);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** 获取数据表格 */
    public JTable getTableData() {
        return tableData;
    }

    /** 获取表格模型 */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    /**
     * 添加一行数据
     *
     * @param row 行数据数组，顺序与列定义一致
     */
    public void addRow(Object[] row) {
        tableModel.addRow(row);
    }

    /** 清空所有数据 */
    public void clearAll() {
        tableModel.setRowCount(0);
    }

    /**
     * 获取选中行的索引
     *
     * @return 选中行索引，未选中返回 -1
     */
    public int getSelectedRow() {
        return tableData.getSelectedRow();
    }

    /**
     * 数据表面板建造器
     */
    public static class Builder {

        private DefaultTableModel tableModel;
        private JTable tableData;

        public Builder() {
            this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
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

