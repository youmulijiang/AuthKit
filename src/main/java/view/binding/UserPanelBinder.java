package view.binding;

import model.AuthUserModel;
import utils.LogUtils;
import view.component.AuthUserConfigPanel;
import view.component.UserPanel;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
     * 从 UserPanel 收集所有用户配置（必须在 EDT 上调用）
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

    /**
     * 从 UserPanel 收集所有用户配置（线程安全版本）。
     * 后台线程（被动捕获 / 右键送测）调用时切换到 EDT 读取 Swing 组件状态，
     * 避免非 EDT 读组件导致的竞态。
     *
     * @param userPanel 用户面板
     * @return 鉴权用户模型列表（读取失败返回空列表）
     */
    public static List<AuthUserModel> collectUsersOnEdt(UserPanel userPanel) {
        if (SwingUtilities.isEventDispatchThread()) {
            return collectUsers(userPanel);
        }
        AtomicReference<List<AuthUserModel>> holder = new AtomicReference<>(List.of());
        try {
            SwingUtilities.invokeAndWait(() -> holder.set(collectUsers(userPanel)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            LogUtils.INSTANCE.error("Failed to collect users from UserPanel", ex);
        }
        return holder.get();
    }
}
