package view.component;

import utils.I18n;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 新建用户对话框
 * 弹出模态窗口，包含与 AuthUserConfigPanel 相同的 UI（启用开关、名称、认证头、参数替换），
 * 底部增加确认/取消按钮。认证头区域会预填自动提取到的认证头。
 */
public class NewUserDialog {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    /**
     * 对话框返回结果
     *
     * @param name             用户名称
     * @param enabled          是否启用
     * @param authHeaders      认证头配置文本
     * @param paramReplacement 参数替换规则文本
     */
    public record UserConfig(String name, boolean enabled, String authHeaders, String paramReplacement) {
    }

    /**
     * 显示新建用户对话框
     *
     * @param parent       父组件（用于定位对话框）
     * @param defaultName  默认用户名称
     * @param authHeaders  预填的认证头文本
     * @return 用户确认后的配置，取消返回 null
     */
    public static UserConfig show(Component parent, String defaultName, String authHeaders) {
        I18n i18n = I18n.getInstance();

        // 创建组件
        JCheckBox checkBoxEnabled = new JCheckBox(i18n.text("user", "checkbox.enabled"), true);
        JTextField textFieldName = new JTextField(defaultName, 15);
        JTextArea textAreaAuthHeaders = new JTextArea(authHeaders);
        textAreaAuthHeaders.setFont(MONO_FONT);
        JTextArea textAreaParamReplacement = new JTextArea();
        textAreaParamReplacement.setFont(MONO_FONT);

        // 构建内容面板
        JPanel panelContent = new JPanel();
        panelContent.setLayout(new BoxLayout(panelContent, BoxLayout.Y_AXIS));

        // 基础信息区
        JPanel panelBasic = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panelBasic.setBorder(new TitledBorder(i18n.text("user", "section.basic")));
        panelBasic.add(checkBoxEnabled);
        panelBasic.add(new JLabel(i18n.text("user", "label.name")));
        panelBasic.add(textFieldName);
        panelBasic.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        panelContent.add(panelBasic);
        panelContent.add(Box.createVerticalStrut(5));

        // 认证头配置区
        JPanel panelAuth = new JPanel(new BorderLayout(5, 5));
        panelAuth.setBorder(new TitledBorder(i18n.text("user", "section.authHeaders")));
        JLabel labelAuthHint = new JLabel("  " + i18n.text("user", "hint.authHeaders"));
        labelAuthHint.setFont(labelAuthHint.getFont().deriveFont(Font.ITALIC, 11f));
        panelAuth.add(labelAuthHint, BorderLayout.NORTH);
        panelAuth.add(new JScrollPane(textAreaAuthHeaders), BorderLayout.CENTER);
        panelAuth.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        panelAuth.setPreferredSize(new Dimension(0, 200));
        panelContent.add(panelAuth);
        panelContent.add(Box.createVerticalStrut(5));

        // 参数替换规则区
        JPanel panelParam = new JPanel(new BorderLayout(5, 5));
        panelParam.setBorder(new TitledBorder(i18n.text("user", "section.paramReplacement")));
        JLabel labelParamHint = new JLabel("  " + i18n.text("user", "hint.paramReplacement"));
        labelParamHint.setFont(labelParamHint.getFont().deriveFont(Font.ITALIC, 11f));
        panelParam.add(labelParamHint, BorderLayout.NORTH);
        panelParam.add(new JScrollPane(textAreaParamReplacement), BorderLayout.CENTER);
        panelParam.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        panelParam.setPreferredSize(new Dimension(0, 150));
        panelContent.add(panelParam);

        // 滚动面板包裹内容
        JScrollPane scrollPane = new JScrollPane(panelContent);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(500, 500));

        // 创建模态对话框
        Window window = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
        JDialog dialog = new JDialog(window instanceof Frame ? (Frame) window : null,
                i18n.text("auth_context_menu", "dialog.newUser.title"), true);
        dialog.setLayout(new BorderLayout());
        dialog.add(scrollPane, BorderLayout.CENTER);

        // 底部按钮
        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnConfirm = new JButton(i18n.text("auth_context_menu", "dialog.newUser.confirm"));
        JButton btnCancel = new JButton(i18n.text("auth_context_menu", "dialog.newUser.cancel"));
        panelButtons.add(btnConfirm);
        panelButtons.add(btnCancel);
        dialog.add(panelButtons, BorderLayout.SOUTH);

        // 结果容器
        final UserConfig[] result = {null};

        btnConfirm.addActionListener(e -> {
            String name = textFieldName.getText().trim();
            if (name.isEmpty()) {
                name = defaultName;
            }
            result[0] = new UserConfig(name, checkBoxEnabled.isSelected(),
                    textAreaAuthHeaders.getText().trim(),
                    textAreaParamReplacement.getText().trim());
            dialog.dispose();
        });

        btnCancel.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        return result[0];
    }
}

