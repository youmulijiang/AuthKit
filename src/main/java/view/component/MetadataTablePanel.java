package view.component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * 元数据透视表面板
 * 位于左侧下方，以透视表形式展示选中记录的详细元数据。
 * 行: 原始对象 / 低权限对象 / 未授权对象
 * 列: 鉴权对象 / 状态码 / 包长度 / Hash / 参数个数
 */
public class MetadataTablePanel extends JPanel {

    private static final String[] COLUMN_NAMES = {
            "鉴权对象", "状态码", "包长度", "Hash", "参数个数"
    };

    private static final String[] ROW_LABELS = {
            "原始对象", "低权限对象", "未授权对象"
    };

    private final JTable tableMetadata;
    private final DefaultTableModel tableModel;

    private MetadataTablePanel(Builder builder) {
        this.tableModel = builder.tableModel;
        this.tableMetadata = builder.tableMetadata;
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(tableMetadata);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** 获取元数据表格 */
    public JTable getTableMetadata() {
        return tableMetadata;
    }

    /** 获取表格模型 */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    /**
     * 更新透视表数据
     *
     * @param originalStatusCode   原始对象状态码
     * @param originalLength       原始对象包长度
     * @param originalHash         原始对象哈希
     * @param originalParamCount   原始对象参数个数
     * @param lowPrivStatusCode    低权限对象状态码
     * @param lowPrivLength        低权限对象包长度
     * @param lowPrivHash          低权限对象哈希
     * @param lowPrivParamCount    低权限对象参数个数
     * @param unauthStatusCode     未授权对象状态码
     * @param unauthLength         未授权对象包长度
     * @param unauthHash           未授权对象哈希
     * @param unauthParamCount     未授权对象参数个数
     */
    public void updateMetadata(int originalStatusCode, int originalLength, String originalHash, int originalParamCount,
                               int lowPrivStatusCode, int lowPrivLength, String lowPrivHash, int lowPrivParamCount,
                               int unauthStatusCode, int unauthLength, String unauthHash, int unauthParamCount) {
        tableModel.setRowCount(0);
        tableModel.addRow(new Object[]{ROW_LABELS[0], originalStatusCode, originalLength, originalHash, originalParamCount});
        tableModel.addRow(new Object[]{ROW_LABELS[1], lowPrivStatusCode, lowPrivLength, lowPrivHash, lowPrivParamCount});
        tableModel.addRow(new Object[]{ROW_LABELS[2], unauthStatusCode, unauthLength, unauthHash, unauthParamCount});
    }

    /** 清空透视表数据 */
    public void clearAll() {
        tableModel.setRowCount(0);
    }

    /**
     * 元数据透视表面板建造器
     */
    public static class Builder {

        private DefaultTableModel tableModel;
        private JTable tableMetadata;

        public Builder() {
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

