import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import controller.AuthController;
import controller.ContextMenuController;
import controller.DataTableController;
import controller.DiffController;
import core.AuthResultExportService;
import core.FakeIpIntruderHttpHandler;
import core.FakeIpPayloadGeneratorProvider;
import core.FakeIpService;
import core.HttpRequestHandler;
import core.RequestReplayService;
import core.TextDiffService;
import core.processor.HeaderReplaceProcessor;
import core.processor.ParamReplaceProcessor;
import core.processor.ProcessorChain;
import core.processor.RequestProcessor;
import core.service.ConfigRequestFilter;
import model.ConfigModel;
import utils.ApiUtils;
import utils.LogUtils;
import view.MainPanel;
import view.binding.ConfigBinder;
import view.component.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AuthKit Burp Suite 扩展入口（装配根）。
 * <p>
 * 仅负责对象创建与 Montoya API 注册，所有 UI 事件编排与业务逻辑
 * 分别下沉至 Controller / Service / View 各层。
 */
public class AuthKit implements BurpExtension {

    /**
     * 插件版本：唯一来源是 pom.xml 的 {@code <version>}，打包时经资源过滤写入
     * {@code version.properties}，此处读取后用于欢迎横幅与日志，避免两处各写一份。
     */
    private static final String AUTHKIT_VERSION = loadVersion();
    private static final AtomicBoolean WELCOME_BANNER_PRINTED = new AtomicBoolean(false);

    /**
     * 读取打包时写入的版本号；直接从 classes 目录运行（如 IDE 调试）时回退到 JAR manifest。
     *
     * @return 版本号，均不可用时返回 "unknown"
     */
    private static String loadVersion() {
        try (java.io.InputStream in = AuthKit.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                String version = props.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (Exception ex) {
            LogUtils.INSTANCE.error("读取版本号失败，回退到 manifest", ex);
        }
        Package pkg = AuthKit.class.getPackage();
        String manifestVersion = pkg != null ? pkg.getImplementationVersion() : null;
        return manifestVersion != null && !manifestVersion.isBlank() ? manifestVersion : "unknown";
    }

    private ExecutorService executor;
    private ExecutorService diffExecutor;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 初始化全局 API 访问点
        ApiUtils.INSTANCE.init(montoyaApi);
        montoyaApi.extension().setName("AuthKit");

        printWelcomeBanner();

        // 创建线程池
        executor = Executors.newFixedThreadPool(3);
        diffExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "authkit-diff-worker");
            thread.setDaemon(true);
            return thread;
        });

        // 创建 UI（传入 MontoyaApi 以创建 Burp 原生编辑器）
        MainPanel mainPanel = new MainPanel(montoyaApi);
        montoyaApi.userInterface().registerSuiteTab("AuthKit", mainPanel);

        // 创建配置模型与处理器链
        ConfigModel configModel = new ConfigModel();
        List<RequestProcessor> processors = List.of(
                new HeaderReplaceProcessor(),
                new ParamReplaceProcessor()
        );
        ProcessorChain processorChain = new ProcessorChain(processors);

        // 创建核心服务
        RequestReplayService replayService = new RequestReplayService(
                montoyaApi.http(), processorChain);
        TextDiffService diffService = new TextDiffService();
        AuthResultExportService exportService = new AuthResultExportService();
        FakeIpService fakeIpService = new FakeIpService();

        // 创建控制器
        AuthController controller = new AuthController(configModel, replayService, diffService);
        DataTableController dataTableController = new DataTableController(
                mainPanel, controller, exportService, executor);
        DiffController diffController = new DiffController(
                mainPanel.getPanelCompare(), diffService, diffExecutor);
        ContextMenuController contextMenuController = new ContextMenuController(
                montoyaApi, mainPanel, controller, replayService, fakeIpService, executor,
                dataTableController::refresh);

        // 绑定 UI ↔ ConfigModel 同步 + 各 Controller 自绑定事件
        ConfigBinder.bind(mainPanel.getPanelConfiguration(), configModel);
        dataTableController.bindAll();
        diffController.bind();
        contextMenuController.register();
        // AI 对话自动附带上下文：提供数据表当前选中样本
        mainPanel.setSelectedSampleProvider(dataTableController::getSelectedSample);

        // 注册 Intruder 随机 IP / XFF 头 payload 生成器
        montoyaApi.intruder().registerPayloadGeneratorProvider(
                new FakeIpPayloadGeneratorProvider(fakeIpService, FakeIpPayloadGeneratorProvider.Mode.IP));
        montoyaApi.intruder().registerPayloadGeneratorProvider(
                new FakeIpPayloadGeneratorProvider(fakeIpService, FakeIpPayloadGeneratorProvider.Mode.XFF_HEADER));

        // 注册 JWT 编辑器 Provider（在 Burp 请求编辑器中添加 JWT 选项卡）
        montoyaApi.userInterface().registerHttpRequestEditorProvider(new JwtRequestEditorProvider(montoyaApi));

        // 注册 JWT 响应分析 Provider（在 Burp 响应编辑器中添加 JWT 分析选项卡）
        montoyaApi.userInterface().registerHttpResponseEditorProvider(new JwtResponseEditorProvider());

        // 创建并注册 HttpRequestHandler（回调委托给 DataTableController）
        ConfigRequestFilter requestFilter = new ConfigRequestFilter(configModel);
        HttpRequestHandler httpHandler = new HttpRequestHandler(requestFilter,
                dataTableController::handleCapturedRequest);
        montoyaApi.http().registerHttpHandler(httpHandler);
        // 伪造 IP 处理器后注册，确保在捕获处理器之后改写每个 Intruder 数据包的 XFF
        montoyaApi.http().registerHttpHandler(new FakeIpIntruderHttpHandler(fakeIpService));

        // 注册插件卸载时清理线程池
        montoyaApi.extension().registerUnloadingHandler(() -> {
            executor.shutdownNow();
            if (diffExecutor != null) {
                diffExecutor.shutdownNow();
            }
            LogUtils.INSTANCE.info("AuthKit 插件已卸载");
        });

        LogUtils.INSTANCE.info("AuthKit 插件加载成功");
    }

    private void printWelcomeBanner() {
        if (!WELCOME_BANNER_PRINTED.compareAndSet(false, true)) {
            return;
        }
        ApiUtils.INSTANCE.api().logging().logToOutput(String.format(
                "[   Pwn The Planet, One HTTP at a Time  ]\n" +
                        "[#] Author: youmulijiang\n" +
                        "[#] Github: https://github.com/youmulijiang\n" +
                        "[#] Version: %s\n", AUTHKIT_VERSION
        ));
    }
}
