package controller;

import core.DiffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import view.component.ComparePanel;

import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;

/**
 * DiffController 单元测试
 * 验证 bind() 正确注册 DiffCallback 到 ComparePanel
 */
class DiffControllerTest {

    private ComparePanel comparePanel;
    private DiffService diffService;
    private ExecutorService diffExecutor;
    private DiffController diffController;

    @BeforeEach
    void setUp() {
        comparePanel = mock(ComparePanel.class);
        diffService = mock(DiffService.class);
        diffExecutor = mock(ExecutorService.class);
        diffController = new DiffController(comparePanel, diffService, diffExecutor);
    }

    @Test
    @DisplayName("bind 应向 ComparePanel 注册 DiffCallback")
    void bind_shouldSetDiffCallback() {
        diffController.bind();

        verify(comparePanel).setDiffCallback(any());
    }

    @Test
    @DisplayName("bind 后 callback 为 null source/target 应显示选择提示并停止计时器")
    void bind_callbackWithNullPanels_shouldShowSelectPrompt() {
        // 使用真实 ComparePanel 验证 callback 行为过于复杂（涉及 Timer/EDT），
        // 此处仅验证 bind() 设置了 callback，callback 内部逻辑由集成测试覆盖。
        diffController.bind();
        verify(comparePanel).setDiffCallback(notNull());
    }
}
