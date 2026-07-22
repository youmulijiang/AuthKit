package view.binding;

import model.ConfigModel;
import view.component.ConfigurationPanel;

/**
 * 配置面板 ↔ 配置模型 绑定器（V层）
 * <p>
 * 将 ConfigurationPanel 的 UI 控件变化同步到 ConfigModel，并完成初始默认值同步。
 * 纯 UI↔Model 双向绑定，不含业务逻辑。
 */
public final class ConfigBinder {

    private ConfigBinder() {
    }

    /**
     * 绑定 ConfigurationPanel UI 控件变化 → ConfigModel 同步
     *
     * @param panel 配置面板
     * @param model 配置模型
     */
    public static void bind(ConfigurationPanel panel, ConfigModel model) {
        panel.getCheckBoxEnabled().addActionListener(e ->
                model.setEnabled(panel.getCheckBoxEnabled().isSelected()));
        panel.getCheckBoxDomainFilter().addActionListener(e ->
                model.setDomainFilterEnabled(panel.getCheckBoxDomainFilter().isSelected()));
        panel.getCheckBoxScopeProxy().addActionListener(e ->
                model.setProxyScopeEnabled(panel.getCheckBoxScopeProxy().isSelected()));
        panel.getCheckBoxScopeRepeater().addActionListener(e ->
                model.setRepeaterScopeEnabled(panel.getCheckBoxScopeRepeater().isSelected()));
        panel.getCheckBoxScopeIntruder().addActionListener(e ->
                model.setIntruderScopeEnabled(panel.getCheckBoxScopeIntruder().isSelected()));
        panel.getCheckBoxScopeExtensions().addActionListener(e ->
                model.setExtensionsScopeEnabled(panel.getCheckBoxScopeExtensions().isSelected()));
        panel.getCheckBoxMethodFilter().addActionListener(e ->
                model.setMethodFilterEnabled(panel.getCheckBoxMethodFilter().isSelected()));
        panel.getCheckBoxPathFilter().addActionListener(e ->
                model.setPathFilterEnabled(panel.getCheckBoxPathFilter().isSelected()));
        panel.getCheckBoxStatusCodeFilter().addActionListener(e ->
                model.setStatusCodeFilterEnabled(panel.getCheckBoxStatusCodeFilter().isSelected()));
        panel.getCheckBoxExtensionFilter().addActionListener(e ->
                model.setExtensionFilterEnabled(panel.getCheckBoxExtensionFilter().isSelected()));

        // 文本区域使用 FocusListener 在失焦时同步
        panel.getTextAreaDomain().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawDomains(panel.getTextAreaDomain().getText());
            }
        });
        panel.getTextFieldMethod().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterMethods(panel.getTextFieldMethod().getText());
            }
        });
        panel.getTextAreaPath().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterPaths(panel.getTextAreaPath().getText());
            }
        });
        panel.getTextFieldStatusCode().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterStatusCodes(panel.getTextFieldStatusCode().getText());
            }
        });
        panel.getTextAreaAuthHeaders().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawAuthHeaders(panel.getTextAreaAuthHeaders().getText());
            }
        });
        panel.getTextFieldExtensionBlacklist().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawExtensionBlacklist(panel.getTextFieldExtensionBlacklist().getText());
            }
        });

        // 初始同步默认值
        model.setEnabled(panel.getCheckBoxEnabled().isSelected());
        model.setDomainFilterEnabled(panel.getCheckBoxDomainFilter().isSelected());
        model.setProxyScopeEnabled(panel.getCheckBoxScopeProxy().isSelected());
        model.setRepeaterScopeEnabled(panel.getCheckBoxScopeRepeater().isSelected());
        model.setIntruderScopeEnabled(panel.getCheckBoxScopeIntruder().isSelected());
        model.setExtensionsScopeEnabled(panel.getCheckBoxScopeExtensions().isSelected());
        model.setMethodFilterEnabled(panel.getCheckBoxMethodFilter().isSelected());
        model.setPathFilterEnabled(panel.getCheckBoxPathFilter().isSelected());
        model.setStatusCodeFilterEnabled(panel.getCheckBoxStatusCodeFilter().isSelected());
        model.setExtensionFilterEnabled(panel.getCheckBoxExtensionFilter().isSelected());
        model.setRawFilterMethods(panel.getTextFieldMethod().getText());
        model.setRawFilterStatusCodes(panel.getTextFieldStatusCode().getText());
        model.setRawAuthHeaders(panel.getTextAreaAuthHeaders().getText());
        model.setRawExtensionBlacklist(panel.getTextFieldExtensionBlacklist().getText());
    }
}
