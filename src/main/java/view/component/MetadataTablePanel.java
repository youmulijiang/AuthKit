package view.component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 元数据透视表面板
 * 位于左侧下方，以透视表形式展示选中记录的详细元数据。
 * 行: 鉴权对象（动态，默认 Original / Unauthorized，用户添加后追加）
 * 列: 鉴权对象 / 状态码 / 包长度 / Hash / 参数个数
 */
public class MetadataTablePanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "鉴权对象", "状态码", "包长度", "Hash", "参数个数"
    };

    /** 默认鉴权对象行 */
    private static final String[] DEFAULT_AUTH_ROWS = {"Original", "Unauthorized"};

    private final JTable tableMetadata;
    private final DefaultTableModel tableModel;

    /** 当前所有鉴权对象行名（有序） */
    private final List<String> authRows;

    private MetadataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableMetadata = builder.tableMetadata;
        this.authRows = builder.authRows;
        initLayout();
        rebuildRows();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableMetadata);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 添加一个鉴权对象行
     *
     * @param name 鉴权对象名称（如 "User1"）
     */
    public void addAuthRow(String name) {
        if (authRows.contains(name)) {
            return;
        }
        authRows.add(name);
        rebuildRows();
    }

    /**
     * 删除一个鉴权对象行
     *
     * @param name 鉴权对象名称
     */
    public void removeAuthRow(String name) {
        if (!authRows.remove(name)) {
            return;
        }
        rebuildRows();
    }

    /** 重建透视表行（每个鉴权对象一行，数据清空） */
    private void rebuildRows() {
        tableModel.setRowCount(0);
        for (String name : authRows) {
            tableModel.addRow(new Object[]{name, "", "", "", ""});
        }
    }

    /**
     * 更新指定鉴权对象行的元数据
     *
     * @param name       鉴权对象名称
     * @param statusCode 状态码
     * @param length     包长度
     * @param hash       哈希值
     * @param paramCount 参数个数
     */
    public void updateRow(String name, int statusCode, int length, String hash, int paramCount) {
        int rowIndex = authRows.indexOf(name);
        if (rowIndex < 0) {
            return;
        }
        tableModel.setValueAt(statusCode, rowIndex, 1);
        tableModel.setValueAt(length, rowIndex, 2);
        tableModel.setValueAt(hash, rowIndex, 3);
        tableModel.setValueAt(paramCount, rowIndex, 4);
    }

    /** 获取元数据表格 */
    public JTable getTableMetadata() {
        return tableMetadata;
    }

    /** 获取表格模型 */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    /** 获取当前鉴权对象行名列表 */
    public List<String> getAuthRows() {
        return List.copyOf(authRows);
    }

    /** 清空透视表数据（保留行结构） */
    public void clearAll() {
        rebuildRows();
    }

    /**
     * 元数据透视表面板建造器
     */
    public static class Builder {

        private DefaultTableModel tableModel;
        private JTable tableMetadata;
        private List<String> authRows;

        public Builder() {
            this.authRows = new ArrayList<>();
            for (String row : DEFAULT_AUTH_ROWS) {
                authRows.add(row);
            }
            this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            this.tableMetadata = new JTable(this.tableModel);
            this.tableMetadata.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }

        /** 构建元数据透视表面板 */
        public MetadataTablePanel build() {
            return new MetadataTablePanel(this);
        }
    }
}

