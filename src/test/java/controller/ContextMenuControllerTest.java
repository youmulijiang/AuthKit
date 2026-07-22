package controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.UserInterface;
import core.FakeIpService;
import core.RequestReplayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import view.MainPanel;

import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.*;

/**
 * ContextMenuController 单元测试
 * 验证 register() 正确注册右键菜单 Provider 到 Montoya UI
 */
class ContextMenuControllerTest {

    @Test
    @DisplayName("register 应调用 Montoya UI 注册 ContextMenuItemsProvider")
    void register_shouldRegisterContextMenuProvider() {
        MontoyaApi montoyaApi = mock(MontoyaApi.class);
        UserInterface userInterface = mock(UserInterface.class);
        when(montoyaApi.userInterface()).thenReturn(userInterface);

        MainPanel mainPanel = mock(MainPanel.class);
        AuthController controller = mock(AuthController.class);
        RequestReplayService replayService = mock(RequestReplayService.class);
        FakeIpService fakeIpService = mock(FakeIpService.class);
        ExecutorService executor = mock(ExecutorService.class);

        ContextMenuController contextMenuController = new ContextMenuController(
                montoyaApi, mainPanel, controller, replayService, fakeIpService, executor, () -> {});

        contextMenuController.register();

        verify(userInterface).registerContextMenuItemsProvider(any());
    }
}
