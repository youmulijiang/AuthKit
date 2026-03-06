package view.component;

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
    public static final String[] DISPLAY_METRICS = {"Length", "Status Code", "Hash"};

    // ===== 基础控制区 =====
    private final JCheckBox checkBoxEnabled;
    private final JButton btnClearTable;
    private final JComboBox<String> comboBoxDisplayMetric;

    // ===== 域名作用域 =====
    private final JCheckBox checkBoxDomainFilter;
    private final JTextArea textAreaDomain;

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

    private ConfigurationPanel(Builder builder) {
        this.checkBoxEnabled = builder.checkBoxEnabled;
        this.btnClearTable = builder.btnClearTable;
        this.comboBoxDisplayMetric = builder.comboBoxDisplayMetric;
        this.checkBoxDomainFilter = builder.checkBoxDomainFilter;
        this.textAreaDomain = builder.textAreaDomain;
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
        panel.setBorder(new TitledBorder("Basic Control"));
        panel.add(checkBoxEnabled);
        panel.add(btnClearTable);
        panel.add(new JLabel("Display:"));
        panel.add(comboBoxDisplayMetric);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        return panel;
    }

    /** 构建域名作用域区 */
    private JPanel buildDomainSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Domain Scope"));
        panel.add(checkBoxDomainFilter, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaDomain), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        panel.setPreferredSize(new Dimension(0, 120));
        return panel;
    }

    /** 构建请求过滤规则区 */
    private JPanel buildFilterSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("Request Filter"));

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
        panel.setBorder(new TitledBorder("Auth Headers (remove for unauthenticated request)"));
        panel.add(new JScrollPane(textAreaAuthHeaders), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        panel.setPreferredSize(new Dimension(0, 150));
        return panel;
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
    public JComboBox<String> getComboBoxDisplayMetric() {
        return comboBoxDisplayMetric;
    }

    /** 获取当前选中的展示指标 */
    public String getSelectedDisplayMetric() {
        return (String) comboBoxDisplayMetric.getSelectedItem();
    }

    /** 获取域名过滤开关 */
    public JCheckBox getCheckBoxDomainFilter() {
        return checkBoxDomainFilter;
    }

    /** 获取域名白名单文本区 */
    public JTextArea getTextAreaDomain() {
        return textAreaDomain;
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
        private final JComboBox<String> comboBoxDisplayMetric;
        private final JCheckBox checkBoxDomainFilter;
        private final JTextArea textAreaDomain;
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

            this.checkBoxEnabled = new JCheckBox("Enable Plugin", true);
            this.btnClearTable = new JButton("Clear Table");
            this.comboBoxDisplayMetric = new JComboBox<>(DISPLAY_METRICS);
            this.comboBoxDisplayMetric.setSelectedItem("Length"); // 默认展示包长度

            this.checkBoxDomainFilter = new JCheckBox("Enable Domain Filter", false);
            this.textAreaDomain = new JTextArea();
            this.textAreaDomain.setFont(monoFont);
            this.textAreaDomain.setToolTipText("One domain per line, e.g. example.com");

            this.checkBoxMethodFilter = new JCheckBox("Method Filter", false);
            this.textFieldMethod = new JTextField("OPTIONS, HEAD, CONNECT");

            this.checkBoxPathFilter = new JCheckBox("Path Filter", false);
            this.textAreaPath = new JTextArea();
            this.textAreaPath.setFont(monoFont);
            this.textAreaPath.setToolTipText("One path per line, e.g. /logout");

            this.checkBoxStatusCodeFilter = new JCheckBox("Status Code Filter", true);
            this.textFieldStatusCode = new JTextField("304, 204");

            this.checkBoxExtensionFilter = new JCheckBox("Extension Blacklist", true);
            this.textFieldExtensionBlacklist = new JTextField(model.ConfigModel.DEFAULT_EXTENSION_BLACKLIST);
            this.textFieldExtensionBlacklist.setToolTipText("Comma-separated file extensions to exclude, e.g. css, js, png");

            this.textAreaAuthHeaders = new JTextArea("Cookie\nAuthorization\nToken");
            this.textAreaAuthHeaders.setFont(monoFont);
            this.textAreaAuthHeaders.setToolTipText("One header name per line (case-sensitive)");
        }

        /** 构建配置面板 */
        public ConfigurationPanel build() {
            return new ConfigurationPanel(this);
        }
    }
}

