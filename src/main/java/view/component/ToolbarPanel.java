package view.component;

import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * 工具栏面板
 * 位于左侧顶部，包含筛选类型下拉框和带 placeholder 的筛选输入框。
 */
public class ToolbarPanel extends JPanel {

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_LENGTH = "LENGTH";
    public static final String FILTER_HASH = "HASH";
    public static final String FILTER_REQUEST_CONTENT = "REQUEST_CONTENT";
    public static final String FILTER_RESPONSE_CONTENT = "RESPONSE_CONTENT";

    private static final String[] FILTER_OPTION_KEYS = {
            FILTER_ALL, FILTER_LENGTH, FILTER_HASH,
            FILTER_REQUEST_CONTENT, FILTER_RESPONSE_CONTENT
    };

    private final JTextField textFieldFilter;
    private final JComboBox<FilterOption> comboBoxFilterType;
    private final String customPlaceholder;

    /** 标志位：当前是否处于 placeholder 状态，避免 DocumentListener 时序问题 */
    private volatile boolean placeholderActive;

    private ToolbarPanel(Builder builder) {
        this.textFieldFilter = builder.textFieldFilter;
        this.comboBoxFilterType = builder.comboBoxFilterType;
        this.customPlaceholder = builder.placeholder;
        this.placeholderActive = false;
        initLayout();
        initPlaceholder();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout(5, 2));
        add(textFieldFilter, BorderLayout.CENTER);
        add(comboBoxFilterType, BorderLayout.EAST);
    }

    /** 初始化输入框 placeholder 效果 */
    private void initPlaceholder() {
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
        textFieldFilter.setText(resolvePlaceholder());
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
    public JComboBox<FilterOption> getComboBoxFilterType() {
        return comboBoxFilterType;
    }

    /** 获取当前选中的筛选类型 */
    public String getSelectedFilterType() {
        FilterOption option = (FilterOption) comboBoxFilterType.getSelectedItem();
        return option != null ? option.key : FILTER_ALL;
    }

    private void refreshTexts() {
        String selectedKey = getSelectedFilterType();
        DefaultComboBoxModel<FilterOption> model = new DefaultComboBoxModel<>();
        for (String key : FILTER_OPTION_KEYS) {
            model.addElement(new FilterOption(key, getFilterLabel(key)));
        }
        comboBoxFilterType.setModel(model);
        restoreSelectedFilterOption(selectedKey);
        textFieldFilter.setToolTipText(resolvePlaceholder());
        if (placeholderActive) {
            showPlaceholder();
        }
    }

    private void restoreSelectedFilterOption(String selectedKey) {
        for (int i = 0; i < comboBoxFilterType.getItemCount(); i++) {
            FilterOption option = comboBoxFilterType.getItemAt(i);
            if (option.key.equals(selectedKey)) {
                comboBoxFilterType.setSelectedIndex(i);
                return;
            }
        }
    }

    private String resolvePlaceholder() {
        if (customPlaceholder != null && !customPlaceholder.isEmpty()) {
            return customPlaceholder;
        }
        return I18n.getInstance().text("toolbar", "placeholder.filter");
    }

    private String getFilterLabel(String key) {
        return switch (key) {
            case FILTER_LENGTH -> I18n.getInstance().text("toolbar", "filter.length");
            case FILTER_HASH -> I18n.getInstance().text("toolbar", "filter.hash");
            case FILTER_REQUEST_CONTENT -> I18n.getInstance().text("toolbar", "filter.requestContent");
            case FILTER_RESPONSE_CONTENT -> I18n.getInstance().text("toolbar", "filter.responseContent");
            default -> I18n.getInstance().text("toolbar", "filter.all");
        };
    }

    /**
     * 工具栏面板建造器
     */
    public static class Builder {

        private JTextField textFieldFilter;
        private JComboBox<FilterOption> comboBoxFilterType;
        private String placeholder;

        public Builder() {
            this.textFieldFilter = new JTextField();
            this.comboBoxFilterType = new JComboBox<>();
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

    public static final class FilterOption {
        private final String key;
        private final String label;

        private FilterOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

