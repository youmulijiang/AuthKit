package view.component;

import utils.I18n;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 配置面板
 * 位于右侧 TabbedPane 的 Configuration 选项卡中，包含四个功能区：
 * 1. 基础控制区 - 插件启停开关、清空数据按钮
 * 2. 域名作用域 - 目标域名白名单
 * 3. 请求过滤规则 - HTTP 方法过滤、路径过滤、状态码过滤
 * 4. 认证头配置 - 未授权检测时需要移除的认证头列表
 */
public class ConfigurationPanel extends JPanel {

    /** DataTable 鉴权列可展示的指标选项 */
    public static final String METRIC_LENGTH = "Length";
    public static final String METRIC_STATUS_CODE = "Status Code";
    public static final String METRIC_HASH = "Hash";
    public static final String METRIC_ATTRIBUTE_NUM = "AttributeNum";
    public static final String METRIC_RANK = "Rank";

    private static final String[] DISPLAY_METRIC_KEYS = {
            METRIC_LENGTH, METRIC_STATUS_CODE, METRIC_HASH, METRIC_ATTRIBUTE_NUM, METRIC_RANK
    };

    /** 禁用时文本框的背景色 */
    private static final Color DISABLED_BG = new Color(230, 230, 230);

    // ===== 基础控制区 =====
    private final JCheckBox checkBoxEnabled;
    private final JButton btnClearTable;
    private final JComboBox<MetricOption> comboBoxDisplayMetric;
    private final JComboBox<I18n.Language> comboBoxLanguage;

    // ===== 域名作用域 =====
    private final JCheckBox checkBoxDomainFilter;
    private final JTextArea textAreaDomain;

    // ===== Tool Type Scope =====
    private final JCheckBox checkBoxScopeProxy;
    private final JCheckBox checkBoxScopeRepeater;
    private final JCheckBox checkBoxScopeIntruder;
    private final JCheckBox checkBoxScopeExtensions;

    // ===== 请求过滤规则 =====
    private final JCheckBox checkBoxMethodFilter;
    private final JTextField textFieldMethod;
    private final JCheckBox checkBoxPathFilter;
    private final JTextArea textAreaPath;
    private final JCheckBox checkBoxStatusCodeFilter;
    private final JTextField textFieldStatusCode;

    // ===== 后缀黑名单 =====
    private final JCheckBox checkBoxExtensionFilter;
    private final JTextField textFieldExtensionBlacklist;

    // ===== 认证头配置 =====
    private final JTextArea textAreaAuthHeaders;

    private TitledBorder borderBasicControl;
    private TitledBorder borderDomainScope;
    private TitledBorder borderToolTypeScope;
    private TitledBorder borderRequestFilter;
    private TitledBorder borderAuthHeaders;
    private JLabel labelDisplay;
    private JLabel labelLanguage;
    private boolean syncingLanguageSelection;

    private ConfigurationPanel(Builder builder) {
        this.checkBoxEnabled = builder.checkBoxEnabled;
        this.btnClearTable = builder.btnClearTable;
        this.comboBoxDisplayMetric = builder.comboBoxDisplayMetric;
        this.comboBoxLanguage = builder.comboBoxLanguage;
        this.checkBoxDomainFilter = builder.checkBoxDomainFilter;
        this.textAreaDomain = builder.textAreaDomain;
        this.checkBoxScopeProxy = builder.checkBoxScopeProxy;
        this.checkBoxScopeRepeater = builder.checkBoxScopeRepeater;
        this.checkBoxScopeIntruder = builder.checkBoxScopeIntruder;
        this.checkBoxScopeExtensions = builder.checkBoxScopeExtensions;
        this.checkBoxMethodFilter = builder.checkBoxMethodFilter;
        this.textFieldMethod = builder.textFieldMethod;
        this.checkBoxPathFilter = builder.checkBoxPathFilter;
        this.textAreaPath = builder.textAreaPath;
        this.checkBoxStatusCodeFilter = builder.checkBoxStatusCodeFilter;
        this.textFieldStatusCode = builder.textFieldStatusCode;
        this.checkBoxExtensionFilter = builder.checkBoxExtensionFilter;
        this.textFieldExtensionBlacklist = builder.textFieldExtensionBlacklist;
        this.textAreaAuthHeaders = builder.textAreaAuthHeaders;
        initLayout();
        comboBoxLanguage.setSelectedItem(I18n.getInstance().getCurrentLanguage());
        // 根据默认状态设置可编辑性
        setConfigEditable(checkBoxEnabled.isSelected());
        // 绑定启停联动
        checkBoxEnabled.addActionListener(e -> setConfigEditable(checkBoxEnabled.isSelected()));
        comboBoxLanguage.addActionListener(e -> {
            if (syncingLanguageSelection) {
                return;
            }
            I18n.Language language = (I18n.Language) comboBoxLanguage.getSelectedItem();
            I18n.getInstance().setLanguage(language);
        });
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /**
     * 根据插件启停状态设置所有配置文本框的可编辑性和背景色。
     * 启用插件时锁定配置（不可编辑、浅灰色背景），未启用时可自由编辑。
     *
     * @param pluginEnabled 插件是否启用
     */
    private void setConfigEditable(boolean pluginEnabled) {
        boolean editable = !pluginEnabled;
        Color bg = editable ? Color.WHITE : DISABLED_BG;

        textAreaDomain.setEditable(editable);
        textAreaDomain.setBackground(bg);

        textFieldMethod.setEditable(editable);
        textFieldMethod.setBackground(bg);

        textAreaPath.setEditable(editable);
        textAreaPath.setBackground(bg);

        textFieldStatusCode.setEditable(editable);
        textFieldStatusCode.setBackground(bg);

        textFieldExtensionBlacklist.setEditable(editable);
        textFieldExtensionBlacklist.setBackground(bg);

        textAreaAuthHeaders.setEditable(editable);
        textAreaAuthHeaders.setBackground(bg);

        // 复选框和下拉框也联动
        checkBoxDomainFilter.setEnabled(editable);
        checkBoxScopeProxy.setEnabled(editable);
        checkBoxScopeRepeater.setEnabled(editable);
        checkBoxScopeIntruder.setEnabled(editable);
        checkBoxScopeExtensions.setEnabled(editable);
        checkBoxMethodFilter.setEnabled(editable);
        checkBoxPathFilter.setEnabled(editable);
        checkBoxStatusCodeFilter.setEnabled(editable);
        checkBoxExtensionFilter.setEnabled(editable);
        // comboBoxDisplayMetric 和 comboBoxLanguage 始终可用
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        JPanel panelContent = new JPanel();
        panelContent.setLayout(new BoxLayout(panelContent, BoxLayout.Y_AXIS));

        panelContent.add(buildBasicControlSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildDomainSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildToolTypeScopeSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildFilterSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildAuthHeaderSection());

        JScrollPane scrollPane = new JScrollPane(panelContent);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** 构建基础控制区 */
    private JPanel buildBasicControlSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        borderBasicControl = new TitledBorder("");
        panel.setBorder(borderBasicControl);
        labelDisplay = new JLabel();
        labelLanguage = new JLabel();
        panel.add(checkBoxEnabled);
        panel.add(btnClearTable);
        panel.add(labelDisplay);
        panel.add(comboBoxDisplayMetric);
        panel.add(labelLanguage);
        panel.add(comboBoxLanguage);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        return panel;
    }

    /** 构建域名作用域区 */
    private JPanel buildDomainSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        borderDomainScope = new TitledBorder("");
        panel.setBorder(borderDomainScope);
        panel.add(checkBoxDomainFilter, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaDomain), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        panel.setPreferredSize(new Dimension(0, 120));
        return panel;
    }

    /** 构建 Tool Type Scope 区 */
    private JPanel buildToolTypeScopeSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        borderToolTypeScope = new TitledBorder("");
        panel.setBorder(borderToolTypeScope);
        panel.add(checkBoxScopeProxy);
        panel.add(checkBoxScopeRepeater);
        panel.add(checkBoxScopeIntruder);
        panel.add(checkBoxScopeExtensions);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        return panel;
    }

    /** 构建请求过滤规则区 */
    private JPanel buildFilterSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        borderRequestFilter = new TitledBorder("");
        panel.setBorder(borderRequestFilter);

        // HTTP 方法过滤
        JPanel panelMethod = new JPanel(new BorderLayout(5, 0));
        panelMethod.add(checkBoxMethodFilter, BorderLayout.WEST);
        panelMethod.add(textFieldMethod, BorderLayout.CENTER);
        panelMethod.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(panelMethod);
        panel.add(Box.createVerticalStrut(5));

        // 路径过滤
        JPanel panelPath = new JPanel(new BorderLayout(5, 0));
        panelPath.add(checkBoxPathFilter, BorderLayout.NORTH);
        panelPath.add(new JScrollPane(textAreaPath), BorderLayout.CENTER);
        panelPath.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        panelPath.setPreferredSize(new Dimension(0, 100));
        panel.add(panelPath);
        panel.add(Box.createVerticalStrut(5));

        // 状态码过滤
        JPanel panelStatus = new JPanel(new BorderLayout(5, 0));
        panelStatus.add(checkBoxStatusCodeFilter, BorderLayout.WEST);
        panelStatus.add(textFieldStatusCode, BorderLayout.CENTER);
        panelStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(panelStatus);
        panel.add(Box.createVerticalStrut(5));

        // 后缀黑名单
        JPanel panelExtension = new JPanel(new BorderLayout(5, 0));
        panelExtension.add(checkBoxExtensionFilter, BorderLayout.WEST);
        panelExtension.add(textFieldExtensionBlacklist, BorderLayout.CENTER);
        panelExtension.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(panelExtension);

        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 290));
        return panel;
    }

    /** 构建认证头配置区 */
    private JPanel buildAuthHeaderSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        borderAuthHeaders = new TitledBorder("");
        panel.setBorder(borderAuthHeaders);
        panel.add(new JScrollPane(textAreaAuthHeaders), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        panel.setPreferredSize(new Dimension(0, 150));
        return panel;
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        borderBasicControl.setTitle(i18n.text("configuration", "section.basic"));
        borderDomainScope.setTitle(i18n.text("configuration", "section.domain"));
        borderToolTypeScope.setTitle(i18n.text("configuration", "section.toolScope"));
        borderRequestFilter.setTitle(i18n.text("configuration", "section.filter"));
        borderAuthHeaders.setTitle(i18n.text("configuration", "section.authHeaders"));

        labelDisplay.setText(i18n.text("configuration", "label.display"));
        labelLanguage.setText(i18n.text("configuration", "label.language"));
        btnClearTable.setText(i18n.text("configuration", "button.clear"));
        checkBoxEnabled.setText(i18n.text("configuration", "checkbox.enablePlugin"));
        checkBoxDomainFilter.setText(i18n.text("configuration", "checkbox.enableDomainFilter"));
        checkBoxScopeProxy.setText(i18n.text("configuration", "option.proxy"));
        checkBoxScopeRepeater.setText(i18n.text("configuration", "option.repeater"));
        checkBoxScopeIntruder.setText(i18n.text("configuration", "option.intruder"));
        checkBoxScopeExtensions.setText(i18n.text("configuration", "option.extensions"));
        checkBoxMethodFilter.setText(i18n.text("configuration", "checkbox.methodFilter"));
        checkBoxPathFilter.setText(i18n.text("configuration", "checkbox.pathFilter"));
        checkBoxStatusCodeFilter.setText(i18n.text("configuration", "checkbox.statusCodeFilter"));
        checkBoxExtensionFilter.setText(i18n.text("configuration", "checkbox.extensionBlacklist"));

        textAreaDomain.setToolTipText(i18n.text("configuration", "tooltip.domain"));
        textAreaPath.setToolTipText(i18n.text("configuration", "tooltip.path"));
        textFieldExtensionBlacklist.setToolTipText(i18n.text("configuration", "tooltip.extensionBlacklist"));
        textAreaAuthHeaders.setToolTipText(i18n.text("configuration", "tooltip.authHeaders"));

        refreshMetricOptions();
        syncingLanguageSelection = true;
        try {
            comboBoxLanguage.setSelectedItem(i18n.getCurrentLanguage());
        } finally {
            syncingLanguageSelection = false;
        }
        revalidate();
        repaint();
    }

    private void refreshMetricOptions() {
        String selectedMetric = getSelectedDisplayMetric();
        DefaultComboBoxModel<MetricOption> model = new DefaultComboBoxModel<>();
        for (String key : DISPLAY_METRIC_KEYS) {
            model.addElement(new MetricOption(key, getMetricLabel(key)));
        }
        comboBoxDisplayMetric.setModel(model);
        restoreSelectedMetric(selectedMetric);
    }

    private void restoreSelectedMetric(String selectedMetric) {
        for (int i = 0; i < comboBoxDisplayMetric.getItemCount(); i++) {
            MetricOption option = comboBoxDisplayMetric.getItemAt(i);
            if (option.key.equals(selectedMetric)) {
                comboBoxDisplayMetric.setSelectedIndex(i);
                return;
            }
        }
    }

    private String getMetricLabel(String metricKey) {
        return switch (metricKey) {
            case METRIC_STATUS_CODE -> I18n.getInstance().text("configuration", "metric.statusCode");
            case METRIC_HASH -> I18n.getInstance().text("configuration", "metric.hash");
            case METRIC_ATTRIBUTE_NUM -> I18n.getInstance().text("configuration", "metric.attributeNum");
            case METRIC_RANK -> I18n.getInstance().text("configuration", "metric.rank");
            default -> I18n.getInstance().text("configuration", "metric.length");
        };
    }

    // ===== Getter 方法 =====

    /** 获取插件启停开关 */
    public JCheckBox getCheckBoxEnabled() {
        return checkBoxEnabled;
    }

    /** 获取清空数据按钮 */
    public JButton getBtnClearTable() {
        return btnClearTable;
    }

    /** 获取数据展示指标下拉框 */
    public JComboBox<MetricOption> getComboBoxDisplayMetric() {
        return comboBoxDisplayMetric;
    }

    public JComboBox<I18n.Language> getComboBoxLanguage() {
        return comboBoxLanguage;
    }

    /** 获取当前选中的展示指标 */
    public String getSelectedDisplayMetric() {
        MetricOption option = (MetricOption) comboBoxDisplayMetric.getSelectedItem();
        return option != null ? option.key : METRIC_LENGTH;
    }

    /** 获取域名过滤开关 */
    public JCheckBox getCheckBoxDomainFilter() {
        return checkBoxDomainFilter;
    }

    /** 获取域名白名单文本区 */
    public JTextArea getTextAreaDomain() {
        return textAreaDomain;
    }

    /** 获取 Proxy Scope 开关 */
    public JCheckBox getCheckBoxScopeProxy() {
        return checkBoxScopeProxy;
    }

    /** 获取 Repeater Scope 开关 */
    public JCheckBox getCheckBoxScopeRepeater() {
        return checkBoxScopeRepeater;
    }

    /** 获取 Intruder Scope 开关 */
    public JCheckBox getCheckBoxScopeIntruder() {
        return checkBoxScopeIntruder;
    }

    /** 获取 Extensions Scope 开关 */
    public JCheckBox getCheckBoxScopeExtensions() {
        return checkBoxScopeExtensions;
    }

    /** 获取 HTTP 方法过滤开关 */
    public JCheckBox getCheckBoxMethodFilter() {
        return checkBoxMethodFilter;
    }

    /** 获取 HTTP 方法过滤输入框 */
    public JTextField getTextFieldMethod() {
        return textFieldMethod;
    }

    /** 获取路径过滤开关 */
    public JCheckBox getCheckBoxPathFilter() {
        return checkBoxPathFilter;
    }

    /** 获取路径过滤文本区 */
    public JTextArea getTextAreaPath() {
        return textAreaPath;
    }

    /** 获取状态码过滤开关 */
    public JCheckBox getCheckBoxStatusCodeFilter() {
        return checkBoxStatusCodeFilter;
    }

    /** 获取状态码过滤输入框 */
    public JTextField getTextFieldStatusCode() {
        return textFieldStatusCode;
    }

    /** 获取后缀黑名单过滤开关 */
    public JCheckBox getCheckBoxExtensionFilter() {
        return checkBoxExtensionFilter;
    }

    /** 获取后缀黑名单输入框 */
    public JTextField getTextFieldExtensionBlacklist() {
        return textFieldExtensionBlacklist;
    }

    /** 获取认证头配置文本区 */
    public JTextArea getTextAreaAuthHeaders() {
        return textAreaAuthHeaders;
    }

    /**
     * 配置面板建造器
     */
    public static class Builder {

        private final JCheckBox checkBoxEnabled;
        private final JButton btnClearTable;
        private final JComboBox<MetricOption> comboBoxDisplayMetric;
        private final JComboBox<I18n.Language> comboBoxLanguage;
        private final JCheckBox checkBoxDomainFilter;
        private final JTextArea textAreaDomain;
        private final JCheckBox checkBoxScopeProxy;
        private final JCheckBox checkBoxScopeRepeater;
        private final JCheckBox checkBoxScopeIntruder;
        private final JCheckBox checkBoxScopeExtensions;
        private final JCheckBox checkBoxMethodFilter;
        private final JTextField textFieldMethod;
        private final JCheckBox checkBoxPathFilter;
        private final JTextArea textAreaPath;
        private final JCheckBox checkBoxStatusCodeFilter;
        private final JTextField textFieldStatusCode;
        private final JCheckBox checkBoxExtensionFilter;
        private final JTextField textFieldExtensionBlacklist;
        private final JTextArea textAreaAuthHeaders;

        public Builder() {
            Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

            this.checkBoxEnabled = new JCheckBox("", false);
            this.btnClearTable = new JButton();
            this.comboBoxDisplayMetric = new JComboBox<>();
            this.comboBoxLanguage = new JComboBox<>(I18n.Language.values());

            this.checkBoxDomainFilter = new JCheckBox("", false);
            this.textAreaDomain = new JTextArea();
            this.textAreaDomain.setFont(monoFont);

            this.checkBoxScopeProxy = new JCheckBox("", true);
            this.checkBoxScopeRepeater = new JCheckBox("", true);
            this.checkBoxScopeIntruder = new JCheckBox("", false);
            this.checkBoxScopeExtensions = new JCheckBox("", false);

            this.checkBoxMethodFilter = new JCheckBox("", false);
            this.textFieldMethod = new JTextField("OPTIONS, HEAD, CONNECT");

            this.checkBoxPathFilter = new JCheckBox("", false);
            this.textAreaPath = new JTextArea();
            this.textAreaPath.setFont(monoFont);

            this.checkBoxStatusCodeFilter = new JCheckBox("", true);
            this.textFieldStatusCode = new JTextField("304, 204");

            this.checkBoxExtensionFilter = new JCheckBox("", true);
            this.textFieldExtensionBlacklist = new JTextField(model.ConfigModel.DEFAULT_EXTENSION_BLACKLIST);

            this.textAreaAuthHeaders = new JTextArea("Cookie\nAuthorization\nToken");
            this.textAreaAuthHeaders.setFont(monoFont);
        }

        /** 构建配置面板 */
        public ConfigurationPanel build() {
            return new ConfigurationPanel(this);
        }
    }

    public static final class MetricOption {
        private final String key;
        private final String label;

        private MetricOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

