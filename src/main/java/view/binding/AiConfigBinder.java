package view.binding;

import core.AiChatService;
import model.AiConfigModel;
import utils.I18n;
import view.component.ConfigurationPanel;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.MessageFormat;

/**
 * 配置面板 AI 区块 ↔ AI 配置模型 绑定器（V层）
 * <p>
 * 将 ConfigurationPanel 中 AI 配置控件的变化同步到 AiConfigModel，
 * 并完成初始值回填（模型 → UI）与本地持久化、测试连接事件。
 */
public final class AiConfigBinder {

    private AiConfigBinder() {
    }

    /**
     * 绑定 ConfigurationPanel 的 AI 配置控件 ↔ AiConfigModel
     *
     * @param panel 配置面板
     * @param model AI 配置模型（供 AiChatPanel 共享读取）
     */
    public static void bind(ConfigurationPanel panel, AiConfigModel model) {
        // 模型初始值 → UI
        panel.getTextFieldAiApiKey().setText(model.getApiKey());
        panel.getTextFieldAiBaseUrl().setText(model.getBaseUrl());
        panel.getTextFieldAiModel().setText(model.getModel());
        panel.getComboBoxAiRequestFormat().setSelectedItem(model.getRequestFormat());

        // UI 变化 → 模型 + 持久化
        saveOnEdit(panel.getTextFieldAiApiKey(), () -> model.setApiKey(
                panel.getTextFieldAiApiKey().getText().trim()), model);
        saveOnEdit(panel.getTextFieldAiBaseUrl(), () -> model.setBaseUrl(
                panel.getTextFieldAiBaseUrl().getText().trim()), model);
        saveOnEdit(panel.getTextFieldAiModel(), () -> model.setModel(
                panel.getTextFieldAiModel().getText().trim()), model);
        panel.getComboBoxAiRequestFormat().addActionListener(e -> {
            Object selected = panel.getComboBoxAiRequestFormat().getSelectedItem();
            if (selected != null) {
                model.setRequestFormat(selected.toString());
                model.save();
            }
        });

        // 测试连接：使用当前 UI 值即时测试
        panel.getBtnAiTest().addActionListener(e -> {
            syncFromUi(panel, model);
            JButton button = panel.getBtnAiTest();
            button.setEnabled(false);
            new Thread(() -> {
                try {
                    AiChatService testService = new AiChatService(model);
                    String reply = testService.testConnection();
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        JOptionPane.showMessageDialog(panel,
                                MessageFormat.format(
                                        I18n.getInstance().text("ai", "message.testOk"), reply),
                                I18n.getInstance().text("ai", "message.saved"),
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        button.setEnabled(true);
                        JOptionPane.showMessageDialog(panel,
                                MessageFormat.format(
                                        I18n.getInstance().text("ai", "message.testFailed"),
                                        ex.getMessage()),
                                I18n.getInstance().text("ai", "message.saved"),
                                JOptionPane.WARNING_MESSAGE);
                    });
                }
            }, "authkit-ai-config-test").start();
        });
    }

    /** 文本框失焦或回车时同步到模型并持久化 */
    private static void saveOnEdit(JTextField field, Runnable apply, AiConfigModel model) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                apply.run();
                model.save();
            }
        });
        field.addActionListener(e -> {
            apply.run();
            model.save();
        });
    }

    /** 从 UI 控件同步全部 AI 配置到模型并持久化 */
    public static void syncFromUi(ConfigurationPanel panel, AiConfigModel model) {
        model.setApiKey(panel.getTextFieldAiApiKey().getText().trim());
        model.setBaseUrl(panel.getTextFieldAiBaseUrl().getText().trim());
        model.setModel(panel.getTextFieldAiModel().getText().trim());
        Object format = panel.getComboBoxAiRequestFormat().getSelectedItem();
        if (format != null) {
            model.setRequestFormat(format.toString());
        }
        model.save();
    }
}
