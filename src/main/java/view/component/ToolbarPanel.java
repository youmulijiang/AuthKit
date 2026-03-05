package view.component;

import javax.swing.*;
import java.awt.*;

/**
 * 工具栏面板
 * 位于左侧顶部，包含设置按钮和筛选输入区域。
 */
public class ToolbarPanel extends JPanel {

    private final JTextField textFieldFilter;
    private final JButton btnFilter;

    private ToolbarPanel(Builder builder) {
        this.textFieldFilter = builder.textFieldFilter;
        this.btnFilter = builder.btnFilter;
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout(5, 2));
        add(textFieldFilter, BorderLayout.CENTER);
        add(btnFilter, BorderLayout.EAST);
    }

    /** 获取筛选输入框 */
    public JTextField getTextFieldFilter() {
        return textFieldFilter;
    }

    /** 获取筛选按钮 */
    public JButton getBtnFilter() {
        return btnFilter;
    }

    /**
     * 工具栏面板建造器
     */
    public static class Builder {

        private JTextField textFieldFilter;
        private JButton btnFilter;

        public Builder() {
            this.textFieldFilter = new JTextField();
            this.btnFilter = new JButton("筛选");
        }

        /** 设置筛选提示文本 */
        public Builder filterPlaceholder(String placeholder) {
            this.textFieldFilter.setToolTipText(placeholder);
            return this;
        }

        /** 构建工具栏面板 */
        public ToolbarPanel build() {
            return new ToolbarPanel(this);
        }
    }
}

