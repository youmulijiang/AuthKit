package view.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * 工具栏面板
 * 位于左侧顶部，包含筛选类型下拉框和带 placeholder 的筛选输入框。
 */
public class ToolbarPanel extends JPanel {

    /** 筛选类型选项 */
    public static final String[] FILTER_OPTIONS = {
            "All", "Length", "Hash", "Request Content", "Response Content"
    };

    private final JTextField textFieldFilter;
    private final JComboBox<String> comboBoxFilterType;
    private final String placeholder;

    /** 标志位：当前是否处于 placeholder 状态，避免 DocumentListener 时序问题 */
    private volatile boolean placeholderActive;

    private ToolbarPanel(Builder builder) {
        this.textFieldFilter = builder.textFieldFilter;
        this.comboBoxFilterType = builder.comboBoxFilterType;
        this.placeholder = builder.placeholder;
        this.placeholderActive = false;
        initLayout();
        initPlaceholder();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout(5, 2));
        add(textFieldFilter, BorderLayout.CENTER);
        add(comboBoxFilterType, BorderLayout.EAST);
    }

    /** 初始化输入框 placeholder 效果 */
    private void initPlaceholder() {
        if (placeholder == null || placeholder.isEmpty()) {
            return;
        }
        showPlaceholder();
        textFieldFilter.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (placeholderActive) {
                    placeholderActive = true; // 保持标志，防止 setText 触发筛选
                    textFieldFilter.setText("");
                    textFieldFilter.setForeground(UIManager.getColor("TextField.foreground"));
                    placeholderActive = false;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textFieldFilter.getText().isEmpty()) {
                    showPlaceholder();
                }
            }
        });
    }

    /** 显示 placeholder 文本 */
    private void showPlaceholder() {
        placeholderActive = true;
        textFieldFilter.setForeground(Color.GRAY);
        textFieldFilter.setText(placeholder);
    }

    /** 判断当前是否正在显示 placeholder */
    public boolean isShowingPlaceholder() {
        return placeholderActive;
    }

    /** 获取用户实际输入的筛选文本（排除 placeholder） */
    public String getFilterText() {
        return placeholderActive ? "" : textFieldFilter.getText();
    }

    /** 获取筛选输入框 */
    public JTextField getTextFieldFilter() {
        return textFieldFilter;
    }

    /** 获取筛选类型下拉框 */
    public JComboBox<String> getComboBoxFilterType() {
        return comboBoxFilterType;
    }

    /** 获取当前选中的筛选类型 */
    public String getSelectedFilterType() {
        return (String) comboBoxFilterType.getSelectedItem();
    }

    /**
     * 工具栏面板建造器
     */
    public static class Builder {

        private JTextField textFieldFilter;
        private JComboBox<String> comboBoxFilterType;
        private String placeholder;

        public Builder() {
            this.textFieldFilter = new JTextField();
            this.comboBoxFilterType = new JComboBox<>(FILTER_OPTIONS);
            this.placeholder = "";
        }

        /** 设置筛选提示文本（placeholder） */
        public Builder filterPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            this.textFieldFilter.setToolTipText(placeholder);
            return this;
        }

        /** 构建工具栏面板 */
        public ToolbarPanel build() {
            return new ToolbarPanel(this);
        }
    }
}

