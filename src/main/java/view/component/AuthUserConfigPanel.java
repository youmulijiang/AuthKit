package view.component;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 单个鉴权对象配置面板
 * 作为 UserPanel 中每个选项卡的内容，用于配置一个鉴权角色的认证信息。
 * 包含：启用开关、角色名称、认证头配置、参数替换规则。
 */
public class AuthUserConfigPanel extends JPanel {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    private final JCheckBox checkBoxEnabled;
    private final JTextField textFieldName;
    private final JTextArea textAreaAuthHeaders;
    private final JTextArea textAreaParamReplacement;

    private AuthUserConfigPanel(Builder builder) {
        this.checkBoxEnabled = builder.checkBoxEnabled;
        this.textFieldName = builder.textFieldName;
        this.textAreaAuthHeaders = builder.textAreaAuthHeaders;
        this.textAreaParamReplacement = builder.textAreaParamReplacement;
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());

        JPanel panelContent = new JPanel();
        panelContent.setLayout(new BoxLayout(panelContent, BoxLayout.Y_AXIS));

        panelContent.add(buildBasicSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildAuthHeaderSection());
        panelContent.add(Box.createVerticalStrut(5));
        panelContent.add(buildParamReplacementSection());

        JScrollPane scrollPane = new JScrollPane(panelContent);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
    }

    /** 构建基础信息区（启用开关 + 角色名称） */
    private JPanel buildBasicSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(new TitledBorder("Basic"));
        panel.add(checkBoxEnabled);
        panel.add(new JLabel("Name:"));
        panel.add(textFieldName);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        return panel;
    }

    /** 构建认证头配置区 */
    private JPanel buildAuthHeaderSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Auth Headers (will replace original headers)"));
        JLabel labelHint = new JLabel("  Format: HeaderName: HeaderValue (one per line)");
        labelHint.setFont(labelHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(labelHint, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaAuthHeaders), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        panel.setPreferredSize(new Dimension(0, 200));
        return panel;
    }

    /** 构建参数替换规则区 */
    private JPanel buildParamReplacementSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("Param Replacement"));
        JLabel labelHint = new JLabel("  Format: paramName=newValue (one per line)");
        labelHint.setFont(labelHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(labelHint, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaParamReplacement), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        panel.setPreferredSize(new Dimension(0, 150));
        return panel;
    }

    // ===== Getter 方法 =====

    /** 获取启用开关 */
    public JCheckBox getCheckBoxEnabled() {
        return checkBoxEnabled;
    }

    /** 获取角色名称输入框 */
    public JTextField getTextFieldName() {
        return textFieldName;
    }

    /** 获取角色名称 */
    public String getUserName() {
        return textFieldName.getText().trim();
    }

    /** 获取认证头配置文本区 */
    public JTextArea getTextAreaAuthHeaders() {
        return textAreaAuthHeaders;
    }

    /** 获取参数替换规则文本区 */
    public JTextArea getTextAreaParamReplacement() {
        return textAreaParamReplacement;
    }

    /** 判断该角色是否启用 */
    public boolean isUserEnabled() {
        return checkBoxEnabled.isSelected();
    }

    /**
     * 单个鉴权对象配置面板建造器
     */
    public static class Builder {

        private final JCheckBox checkBoxEnabled;
        private final JTextField textFieldName;
        private final JTextArea textAreaAuthHeaders;
        private final JTextArea textAreaParamReplacement;

        public Builder(String defaultName) {
            this.checkBoxEnabled = new JCheckBox("Enabled", true);
            this.textFieldName = new JTextField(defaultName, 15);
            this.textAreaAuthHeaders = new JTextArea();
            this.textAreaAuthHeaders.setFont(MONO_FONT);
            this.textAreaAuthHeaders.setToolTipText("e.g. Cookie: JSESSIONID=abc");
            this.textAreaParamReplacement = new JTextArea();
            this.textAreaParamReplacement.setFont(MONO_FONT);
            this.textAreaParamReplacement.setToolTipText("e.g. userId=testuser");
        }

        /** 构建鉴权对象配置面板 */
        public AuthUserConfigPanel build() {
            return new AuthUserConfigPanel(this);
        }
    }
}

