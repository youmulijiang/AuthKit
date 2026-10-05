package view.component;

import utils.I18n;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 配置面板
 * 位于右侧 TabbedPane 的 Configuration 选项卡中，布局为常驻顶栏 + JTabbedPane 分组：
 * 1. 常驻顶栏 - 插件启停开关、清空数据按钮、显示指标、语言、仅显示越权
 * 2. 捕获过滤 Tab - 捕获范围（域名白名单、Tool Type Scope）+ 请求过滤 + 认证头配置
 * 3. AI 分析 Tab - AI 分析服务配置
 */
public class ConfigurationPanel extends JPanel {

    /** DataTable 鉴权列可展示的指标选项 */
    public static final String METRIC_LENGTH = "Length";
    public static final String METRIC_STATUS_CODE = "Status Code";
    public static final String METRIC_HASH = "Hash";
    public static final String METRIC_ATTRIBUTE_NUM = "AttributeNum";
    public static final String METRIC_RANK = "Rank";
    public static final String METRIC_NOTE = "Note";
    public static final String METRIC_CONTENT_TYPE = "ContentType";

    private static final String[] DISPLAY_METRIC_KEYS = {
            METRIC_RANK, METRIC_LENGTH, METRIC_STATUS_CODE, METRIC_HASH, METRIC_ATTRIBUTE_NUM,
            METRIC_NOTE, METRIC_CONTENT_TYPE
    };

    /** 禁用时文本框的背景色 */
    private static final Color DISABLED_BG = new Color(230, 230, 230);
    private static final Color PLACEHOLDER_COLOR = new Color(155, 155, 155);

    // ===== 基础控制区 =====
    private final JCheckBox checkBoxEnabled;
    private final JButton btnClearTable;
    private final JComboBox<MetricOption> comboBoxDisplayMetric;
    private final JComboBox<I18n.Language> comboBoxLanguage;
    private final JCheckBox checkBoxUnauthorizedOnly;

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
    private final PlaceholderTextArea textAreaAuthHeaders;

    // ===== AI 配置 =====
    private final JTextField textFieldAiApiKey;
    private final JTextField textFieldAiBaseUrl;
    private final JTextField textFieldAiModel;
    private final JComboBox<String> comboBoxAiRequestFormat;
    private final JButton btnAiTest;

    private TitledBorder borderBasicControl;
    private TitledBorder borderDomainScope;
    private TitledBorder borderToolTypeScope;
    private TitledBorder borderRequestFilter;
    private TitledBorder borderAuthHeaders;
    private TitledBorder borderAiConfig;
    private JTabbedPane tabbedPane;
    private JLabel labelDisplay;
    private JLabel labelLanguage;
    private JLabel labelAiApiKey;
    private JLabel labelAiBaseUrl;
    private JLabel labelAiModel;
    private JLabel labelAiRequestFormat;
    private boolean syncingLanguageSelection;

    private ConfigurationPanel(Builder builder) {
        this.checkBoxEnabled = builder.checkBoxEnabled;
        this.btnClearTable = builder.btnClearTable;
        this.comboBoxDisplayMetric = builder.comboBoxDisplayMetric;
        this.comboBoxLanguage = builder.comboBoxLanguage;
        this.checkBoxUnauthorizedOnly = builder.checkBoxUnauthorizedOnly;
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
        this.textFieldAiApiKey = builder.textFieldAiApiKey;
        this.textFieldAiBaseUrl = builder.textFieldAiBaseUrl;
        this.textFieldAiModel = builder.textFieldAiModel;
        this.comboBoxAiRequestFormat = builder.comboBoxAiRequestFormat;
        this.btnAiTest = builder.btnAiTest;
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

    /** 初始化布局：常驻顶栏 + JTabbedPane 分组 */
    private void initLayout() {
        setLayout(new BorderLayout());
        add(buildBasicControlSection(), BorderLayout.NORTH);
        add(buildTabbedPane(), BorderLayout.CENTER);
    }

    /** 构建 Tab 分组面板：捕获过滤（捕获范围 + 过滤规则 + 认证配置）/ AI 分析 */
    private JTabbedPane buildTabbedPane() {
        tabbedPane = new JTabbedPane();

        // Tab 1：捕获过滤（捕获范围 + 请求过滤 + 认证头配置）
        JPanel captureFilterTab = buildVerticalTabContent(
                buildDomainSection(),
                buildToolTypeScopeSection(),
                buildFilterSection(),
                buildAuthHeaderSection());
        tabbedPane.addTab("", new JScrollPane(captureFilterTab));

        // Tab 2：AI 分析
        JPanel aiTab = new JPanel(new BorderLayout());
        aiTab.add(buildAiConfigSection(), BorderLayout.NORTH);
        tabbedPane.addTab("", new JScrollPane(aiTab));

        return tabbedPane;
    }

    /**
     * 构建 Tab 内纵向堆叠内容面板。
     * 实现 Scrollable 接口让内容宽度跟随视口（不产生横向滚动），
     * 高度按首选值纵向滚动。
     */
    private JPanel buildVerticalTabContent(JComponent... sections) {
        JPanel panel = new ScrollablePanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < sections.length; i++) {
            if (i > 0) {
                panel.add(Box.createVerticalStrut(5));
            }
            panel.add(sections[i]);
        }
        panel.add(Box.createVerticalGlue());
        return panel;
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
        panel.add(checkBoxUnauthorizedOnly);
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

    /** 构建 AI 配置区（API Key / Base URL / 模型 / 请求格式 / 测试连接） */
    private JPanel buildAiConfigSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        borderAiConfig = new TitledBorder("");
        panel.setBorder(borderAiConfig);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // API Key
        addAiConfigRow(panel, gbc, 0, labelAiApiKey = new JLabel(), textFieldAiApiKey);
        // Base URL
        addAiConfigRow(panel, gbc, 1, labelAiBaseUrl = new JLabel(), textFieldAiBaseUrl);

        // 模型 + 请求格式
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(labelAiModel = new JLabel(), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(textFieldAiModel, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(labelAiRequestFormat = new JLabel(), gbc);
        gbc.gridx = 3;
        panel.add(comboBoxAiRequestFormat, gbc);

        // 测试按钮
        gbc.gridx = 2;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(btnAiTest, gbc);
        return panel;
    }

    /** 添加“标签 + 输入框”形式的一行（标签固定宽，输入框铺满剩余宽度） */
    private void addAiConfigRow(JPanel panel, GridBagConstraints gbc, int row,
                                JLabel label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(label, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        borderBasicControl.setTitle(i18n.text("configuration", "section.basic"));
        borderDomainScope.setTitle(i18n.text("configuration", "section.domain"));
        borderToolTypeScope.setTitle(i18n.text("configuration", "section.toolScope"));
        borderRequestFilter.setTitle(i18n.text("configuration", "section.filter"));
        borderAuthHeaders.setTitle(i18n.text("configuration", "section.authHeaders"));
        borderAiConfig.setTitle(i18n.text("configuration", "section.ai"));

        if (tabbedPane != null) {
            tabbedPane.setTitleAt(0, i18n.text("configuration", "tab.captureFilter"));
            tabbedPane.setTitleAt(1, i18n.text("configuration", "tab.ai"));
        }

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
        checkBoxUnauthorizedOnly.setText(i18n.text("configuration", "checkbox.unauthorizedOnly"));

        labelAiApiKey.setText(i18n.text("ai", "label.apiKey"));
        labelAiBaseUrl.setText(i18n.text("ai", "label.baseUrl"));
        labelAiModel.setText(i18n.text("ai", "label.model"));
        labelAiRequestFormat.setText(i18n.text("ai", "label.requestFormat"));
        btnAiTest.setText(i18n.text("ai", "button.test"));

        textAreaDomain.setToolTipText(i18n.text("configuration", "tooltip.domain"));
        textAreaPath.setToolTipText(i18n.text("configuration", "tooltip.path"));
        textFieldExtensionBlacklist.setToolTipText(i18n.text("configuration", "tooltip.extensionBlacklist"));
        textAreaAuthHeaders.setToolTipText(i18n.text("configuration", "tooltip.authHeaders"));
        textAreaAuthHeaders.setPlaceholder(i18n.text("configuration", "placeholder.authHeaders"));

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
            case METRIC_NOTE -> I18n.getInstance().text("configuration", "metric.note");
            case METRIC_CONTENT_TYPE -> I18n.getInstance().text("configuration", "metric.contentType");
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

    /** 获取当前选中的展示指标，初始化或无选中项时默认使用 Rank。 */
    public String getSelectedDisplayMetric() {
        MetricOption option = (MetricOption) comboBoxDisplayMetric.getSelectedItem();
        return option != null ? option.key : METRIC_RANK;
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

    /** 获取仅显示越权行开关 */
    public JCheckBox getCheckBoxUnauthorizedOnly() {
        return checkBoxUnauthorizedOnly;
    }

    /** 获取 AI API Key 输入框 */
    public JTextField getTextFieldAiApiKey() {
        return textFieldAiApiKey;
    }

    /** 获取 AI Base URL 输入框 */
    public JTextField getTextFieldAiBaseUrl() {
        return textFieldAiBaseUrl;
    }

    /** 获取 AI 模型输入框 */
    public JTextField getTextFieldAiModel() {
        return textFieldAiModel;
    }

    /** 获取 AI 请求格式下拉框 */
    public JComboBox<String> getComboBoxAiRequestFormat() {
        return comboBoxAiRequestFormat;
    }

    /** 获取 AI 测试连接按钮 */
    public JButton getBtnAiTest() {
        return btnAiTest;
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
        private final PlaceholderTextArea textAreaAuthHeaders;
        private final JCheckBox checkBoxUnauthorizedOnly;
        private final JTextField textFieldAiApiKey;
        private final JTextField textFieldAiBaseUrl;
        private final JTextField textFieldAiModel;
        private final JComboBox<String> comboBoxAiRequestFormat;
        private final JButton btnAiTest;

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

            this.textAreaAuthHeaders = new PlaceholderTextArea();
            this.textAreaAuthHeaders.setFont(monoFont);
            this.textAreaAuthHeaders.setPlaceholderColor(PLACEHOLDER_COLOR);
            this.textAreaAuthHeaders.setLineWrap(true);
            this.textAreaAuthHeaders.setWrapStyleWord(true);

            this.checkBoxUnauthorizedOnly = new JCheckBox("", false);

            // AI 配置控件
            this.textFieldAiApiKey = new JTextField();
            this.textFieldAiApiKey.setFont(monoFont);
            this.textFieldAiBaseUrl = new JTextField("https://api.openai.com/v1");
            this.textFieldAiBaseUrl.setFont(monoFont);
            this.textFieldAiModel = new JTextField("gpt-4o-mini");
            this.textFieldAiModel.setFont(monoFont);
            this.comboBoxAiRequestFormat = new JComboBox<>(model.AiConfigModel.REQUEST_FORMATS);
            this.btnAiTest = new JButton();
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

    /**
     * 跟随视口宽度的滚动面板：宽度始终铺满 JScrollPane 视口（无横向滚动条），
     * 高度按内容首选值纵向滚动。用于 Tab 内容区。
     */
    private static class ScrollablePanel extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}

