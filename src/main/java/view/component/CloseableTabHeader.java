package view.component;

import javax.swing.*;
import java.awt.*;

/**
 * 可关闭的选项卡头部组件
 * 显示标题文本和一个 × 关闭按钮，用于 JTabbedPane 的 setTabComponentAt。
 * 关闭按钮点击后通过回调通知外部执行移除逻辑。
 */
public class CloseableTabHeader extends JPanel {

    private final JLabel labelTitle;
    private final JButton btnClose;

    /**
     * 构造可关闭选项卡头部
     *
     * @param title   选项卡标题
     * @param onClose 关闭按钮点击时的回调
     */
    public CloseableTabHeader(String title, Runnable onClose) {
        this.labelTitle = new JLabel(title);
        this.btnClose = createCloseButton();
        initLayout();
        bindEvents(onClose);
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
        setOpaque(false);
        add(labelTitle);
        add(btnClose);
    }

    /** 绑定事件 */
    private void bindEvents(Runnable onClose) {
        btnClose.addActionListener(e -> onClose.run());
    }

    /** 创建关闭按钮 */
    private JButton createCloseButton() {
        JButton btn = new JButton("×");
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setMargin(new Insets(0, 2, 0, 2));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** 获取标题文本 */
    public String getTitle() {
        return labelTitle.getText();
    }

    /** 设置标题文本 */
    public void setTitle(String title) {
        labelTitle.setText(title);
    }
}

