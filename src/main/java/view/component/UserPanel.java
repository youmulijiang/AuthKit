package view.component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 用户面板
 * 位于右侧 TabbedPane 的 User 选项卡中。
 * 使用 JTabbedPane 管理多个鉴权对象，每个选项卡对应一个 AuthUserConfigPanel。
 * 每个选项卡头部带有 × 关闭按钮，末尾有一个 "+" 选项卡用于添加新用户。
 */
public class UserPanel extends JPanel {

    /** "+" 占位面板，点击该 Tab 时触发添加逻辑 */
    private static final JPanel PLACEHOLDER_ADD = new JPanel();
    private static final String ADD_TAB_TITLE = "+";

    private final JTabbedPane tabbedUsers;

    /** 选项卡名称 -> AuthUserConfigPanel 的映射 */
    private final Map<String, AuthUserConfigPanel> userPanels;

    /** 用户计数器，用于生成默认名称 */
    private int userCounter;

    /** 用户添加回调列表 */
    private final List<Consumer<String>> onUserAddedListeners = new ArrayList<>();

    /** 用户删除回调列表 */
    private final List<Consumer<String>> onUserRemovedListeners = new ArrayList<>();

    /** 用户重命名回调列表：(oldName, newName) */
    private final List<BiConsumer<String, String>> onUserRenamedListeners = new ArrayList<>();

    private UserPanel(Builder builder) {
        this.tabbedUsers = builder.tabbedUsers;
        this.userPanels = builder.userPanels;
        this.userCounter = builder.userCounter;
        initLayout();
        appendAddTab();
        bindEvents();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        add(tabbedUsers, BorderLayout.CENTER);
    }

    /** 在末尾追加 "+" 占位选项卡，并禁用该 Tab 的内容区 */
    private void appendAddTab() {
        tabbedUsers.addTab(ADD_TAB_TITLE, PLACEHOLDER_ADD);
    }

    /** 绑定事件：通过 MouseListener 监听点击 "+" Tab */
    private void bindEvents() {
        tabbedUsers.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int clickedIndex = tabbedUsers.indexAtLocation(e.getX(), e.getY());
                if (clickedIndex < 0) {
                    return;
                }
                if (tabbedUsers.getComponentAt(clickedIndex) == PLACEHOLDER_ADD) {
                    SwingUtilities.invokeLater(() -> addUser());
                }
            }
        });
    }

    /**
     * 添加一个新的鉴权对象选项卡
     *
     * @return 新创建的 AuthUserConfigPanel
     */
    public AuthUserConfigPanel addUser() {
        // 跳过已被占用的名称，避免与用户手动重命名后的名称冲突
        do {
            userCounter++;
        } while (userPanels.containsKey("User" + userCounter));
        String name = "User" + userCounter;
        AuthUserConfigPanel configPanel = new AuthUserConfigPanel.Builder(name).build();
        userPanels.put(name, configPanel);

        // 插入到 "+" Tab 之前
        int addTabIndex = tabbedUsers.indexOfComponent(PLACEHOLDER_ADD);
        tabbedUsers.insertTab(name, null, configPanel, null, addTabIndex);

        // 设置可关闭的选项卡头部（使用当前名称查找，而非捕获初始名称）
        CloseableTabHeader header = new CloseableTabHeader(name,
                () -> removeUser(findKeyByPanel(configPanel)));
        tabbedUsers.setTabComponentAt(addTabIndex, header);

        // 监听用户名输入框失焦，检测名称变更并同步
        configPanel.getTextFieldName().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String oldName = findKeyByPanel(configPanel);
                String newName = configPanel.getUserName();
                if (oldName != null && !oldName.equals(newName) && !newName.isEmpty()) {
                    renameUser(oldName, newName, configPanel);
                }
            }
        });

        tabbedUsers.setSelectedComponent(configPanel);
        onUserAddedListeners.forEach(listener -> listener.accept(name));
        return configPanel;
    }

    /**
     * 重命名鉴权用户：更新 map key、Tab 标题、CloseableTabHeader，触发回调
     */
    private void renameUser(String oldName, String newName, AuthUserConfigPanel configPanel) {
        // 新名称已存在，弹窗提示并恢复旧名称
        if (userPanels.containsKey(newName)) {
            JOptionPane.showMessageDialog(this,
                    "The name \"" + newName + "\" is already in use. Please choose a different name.",
                    "Duplicate Name",
                    JOptionPane.WARNING_MESSAGE);
            configPanel.getTextFieldName().setText(oldName);
            configPanel.getTextFieldName().requestFocusInWindow();
            return;
        }

        // 更新 map：保持插入顺序
        LinkedHashMap<String, AuthUserConfigPanel> newMap = new LinkedHashMap<>();
        for (Map.Entry<String, AuthUserConfigPanel> entry : userPanels.entrySet()) {
            if (entry.getKey().equals(oldName)) {
                newMap.put(newName, entry.getValue());
            } else {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        userPanels.clear();
        userPanels.putAll(newMap);

        // 更新 Tab 标题
        int tabIndex = tabbedUsers.indexOfComponent(configPanel);
        if (tabIndex >= 0) {
            tabbedUsers.setTitleAt(tabIndex, newName);
            // 更新 CloseableTabHeader 的显示文本
            Component tabComponent = tabbedUsers.getTabComponentAt(tabIndex);
            if (tabComponent instanceof CloseableTabHeader) {
                ((CloseableTabHeader) tabComponent).setTitle(newName);
            }
        }

        onUserRenamedListeners.forEach(listener -> listener.accept(oldName, newName));
    }

    /**
     * 通过 panel 实例反查当前 map 中的 key
     */
    private String findKeyByPanel(AuthUserConfigPanel panel) {
        for (Map.Entry<String, AuthUserConfigPanel> entry : userPanels.entrySet()) {
            if (entry.getValue() == panel) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 根据名称移除指定的鉴权对象选项卡
     *
     * @param name 鉴权对象名称
     */
    public void removeUser(String name) {
        AuthUserConfigPanel panel = userPanels.remove(name);
        if (panel == null) {
            return;
        }
        int index = tabbedUsers.indexOfComponent(panel);
        if (index >= 0) {
            tabbedUsers.removeTabAt(index);
        }
        onUserRemovedListeners.forEach(listener -> listener.accept(name));
    }

    /**
     * 注册用户添加回调
     *
     * @param listener 回调函数，参数为用户名称
     */
    public void onUserAdded(Consumer<String> listener) {
        onUserAddedListeners.add(listener);
    }

    /**
     * 注册用户删除回调
     *
     * @param listener 回调函数，参数为用户名称
     */
    public void onUserRemoved(Consumer<String> listener) {
        onUserRemovedListeners.add(listener);
    }

    /**
     * 注册用户重命名回调
     *
     * @param listener 回调函数，参数为 (oldName, newName)
     */
    public void onUserRenamed(BiConsumer<String, String> listener) {
        onUserRenamedListeners.add(listener);
    }

    /** 获取鉴权对象 TabbedPane */
    public JTabbedPane getTabbedUsers() {
        return tabbedUsers;
    }

    /**
     * 获取所有鉴权对象配置面板
     *
     * @return 名称 -> AuthUserConfigPanel 的映射（只读视图）
     */
    public Map<String, AuthUserConfigPanel> getUserPanels() {
        return Map.copyOf(userPanels);
    }

    /**
     * 根据名称获取指定的鉴权对象配置面板
     *
     * @param name 鉴权对象名称
     * @return 对应的 AuthUserConfigPanel，不存在返回 null
     */
    public AuthUserConfigPanel getUserPanel(String name) {
        return userPanels.get(name);
    }

    /** 获取当前鉴权对象数量 */
    public int getUserCount() {
        return userPanels.size();
    }

    /**
     * 用户面板建造器
     */
    public static class Builder {

        private final JTabbedPane tabbedUsers;
        private final Map<String, AuthUserConfigPanel> userPanels;
        private int userCounter;

        public Builder() {
            this.tabbedUsers = new JTabbedPane();
            this.userPanels = new LinkedHashMap<>();
            this.userCounter = 0;
        }

        /** 构建用户面板 */
        public UserPanel build() {
            return new UserPanel(this);
        }
    }
}
