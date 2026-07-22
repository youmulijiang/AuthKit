package view.binding;

import model.AuthUserModel;
import view.component.AuthUserConfigPanel;
import view.component.UserPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户面板 ↔ 用户模型 绑定器（V层）
 * <p>
 * 从 UserPanel 收集所有已配置的鉴权用户，转换为 AuthUserModel 列表。
 * 被控制器在被动捕获与右键送测流程中复用。
 */
public final class UserPanelBinder {

    private UserPanelBinder() {
    }

    /**
     * 从 UserPanel 收集所有用户配置
     *
     * @param userPanel 用户面板
     * @return 鉴权用户模型列表
     */
    public static List<AuthUserModel> collectUsers(UserPanel userPanel) {
        List<AuthUserModel> users = new ArrayList<>();
        Map<String, AuthUserConfigPanel> panels = userPanel.getUserPanels();
        for (Map.Entry<String, AuthUserConfigPanel> entry : panels.entrySet()) {
            AuthUserConfigPanel configPanel = entry.getValue();
            AuthUserModel user = new AuthUserModel(configPanel.getUserName());
            user.setEnabled(configPanel.isUserEnabled());
            user.setRawHeaders(configPanel.getTextAreaAuthHeaders().getText());
            user.setRawParams(configPanel.getTextAreaParamReplacement().getText());
            users.add(user);
        }
        return users;
    }
}
