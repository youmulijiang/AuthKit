package controller;

import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.requests.HttpRequest;
import core.AuthResultExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import view.MainPanel;
import view.component.ComparePanel;
import view.component.DataTablePanel;
import view.component.MetadataTablePanel;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DataTableController 单元测试
 * 聚焦去重与清空逻辑（不依赖 Swing EDT 的路径）
 */
class DataTableControllerTest {

    private MainPanel mainPanel;
    private AuthController controller;
    private AuthResultExportService exportService;
    private ExecutorService executor;
    private DataTableController dataTableController;

    @BeforeEach
    void setUp() {
        mainPanel = mock(MainPanel.class);
        controller = mock(AuthController.class);
        exportService = mock(AuthResultExportService.class);
        executor = mock(ExecutorService.class);
        dataTableController = new DataTableController(mainPanel, controller, exportService, executor);
    }

    @Test
    @DisplayName("handleCapturedRequest 重复请求应直接返回不提交 executor")
    void handleCapturedRequest_duplicate_shouldNotSubmit() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponseReceived response = mock(HttpResponseReceived.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(controller.isNewRequest("GET", "http://example.com/api")).thenReturn(false);

        dataTableController.handleCapturedRequest(request, response);

        verify(executor, never()).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("handleCapturedRequest 新请求应提交 executor 执行")
    void handleCapturedRequest_newRequest_shouldSubmit() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponseReceived response = mock(HttpResponseReceived.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(controller.isNewRequest("GET", "http://example.com/api")).thenReturn(true);

        dataTableController.handleCapturedRequest(request, response);

        verify(executor).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("clearAll 应清空控制器记录与所有 UI 面板")
    void clearAll_shouldClearControllerAndAllPanels() {
        DataTablePanel dataTable = mock(DataTablePanel.class);
        MetadataTablePanel metadataTable = mock(MetadataTablePanel.class);
        ComparePanel comparePanel = mock(ComparePanel.class);
        when(mainPanel.getPanelDataTable()).thenReturn(dataTable);
        when(mainPanel.getPanelMetadataTable()).thenReturn(metadataTable);
        when(mainPanel.getPanelCompare()).thenReturn(comparePanel);

        dataTableController.clearAll();

        verify(controller).clearAll();
        verify(dataTable).clearAll();
        verify(metadataTable).clearAll();
        verify(comparePanel).clearAll();
    }
}
