package view.component;

import utils.I18n;

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

    private TitledBorder borderBasic;
    private TitledBorder borderAuthHeaders;
    private TitledBorder borderParamReplacement;
    private JLabel labelName;
    private JLabel labelAuthHint;
    private JLabel labelParamHint;

    private AuthUserConfigPanel(Builder builder) {
        this.checkBoxEnabled = builder.checkBoxEnabled;
        this.textFieldName = builder.textFieldName;
        this.textAreaAuthHeaders = builder.textAreaAuthHeaders;
        this.textAreaParamReplacement = builder.textAreaParamReplacement;
        initLayout();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
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
        borderBasic = new TitledBorder("");
        panel.setBorder(borderBasic);
        labelName = new JLabel();
        panel.add(checkBoxEnabled);
        panel.add(labelName);
        panel.add(textFieldName);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        return panel;
    }

    /** 构建认证头配置区 */
    private JPanel buildAuthHeaderSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        borderAuthHeaders = new TitledBorder("");
        panel.setBorder(borderAuthHeaders);
        labelAuthHint = new JLabel();
        labelAuthHint.setFont(labelAuthHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(labelAuthHint, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaAuthHeaders), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        panel.setPreferredSize(new Dimension(0, 200));
        return panel;
    }

    /** 构建参数替换规则区 */
    private JPanel buildParamReplacementSection() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        borderParamReplacement = new TitledBorder("");
        panel.setBorder(borderParamReplacement);
        labelParamHint = new JLabel();
        labelParamHint.setFont(labelParamHint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(labelParamHint, BorderLayout.NORTH);
        panel.add(new JScrollPane(textAreaParamReplacement), BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        panel.setPreferredSize(new Dimension(0, 150));
        return panel;
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        borderBasic.setTitle(i18n.text("user", "section.basic"));
        borderAuthHeaders.setTitle(i18n.text("user", "section.authHeaders"));
        borderParamReplacement.setTitle(i18n.text("user", "section.paramReplacement"));
        checkBoxEnabled.setText(i18n.text("user", "checkbox.enabled"));
        labelName.setText(i18n.text("user", "label.name"));
        labelAuthHint.setText("  " + i18n.text("user", "hint.authHeaders"));
        labelParamHint.setText("  " + i18n.text("user", "hint.paramReplacement"));
        textAreaAuthHeaders.setToolTipText(i18n.text("user", "tooltip.authHeaders"));
        textAreaParamReplacement.setToolTipText(i18n.text("user", "tooltip.paramReplacement"));
        revalidate();
        repaint();
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
            this.checkBoxEnabled = new JCheckBox("", true);
            this.textFieldName = new JTextField(defaultName, 15);
            this.textAreaAuthHeaders = new JTextArea();
            this.textAreaAuthHeaders.setFont(MONO_FONT);
            this.textAreaParamReplacement = new JTextArea();
            this.textAreaParamReplacement.setFont(MONO_FONT);
        }

        /** 构建鉴权对象配置面板 */
        public AuthUserConfigPanel build() {
            return new AuthUserConfigPanel(this);
        }
    }
}

