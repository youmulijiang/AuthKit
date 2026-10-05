# 架构说明

## 1. 总体结构

AuthKit 是单模块 Maven 项目。`AuthKit.java` 实现 Montoya `BurpExtension`，负责创建对象、连接回调并向 Burp 注册界面和处理器；项目不使用依赖注入框架。

```text
src/main/java/
├── AuthKit.java          # 扩展入口与对象装配
├── controller/           # 流程编排、界面事件协调
├── core/                 # 重放、评分、Diff、AI 等核心能力
│   ├── AiChatService.java     # LLM 调用（OpenAI 兼容 / Anthropic / Ollama）
│   ├── AiToolService.java     # 内置工具调用解析（数据包 / 发包）
│   ├── AiSessionService.java  # 对话历史 JSON 持久化
│   ├── PacketSourceService.java # Proxy 历史 / 站点地图候选读取与评分
│   ├── normalize/        # 响应体递进归一化（L0/L1/L2）
│   ├── processor/        # 请求修改责任链
│   ├── scan/             # 专项扫描（按能力分包）
│   │   ├── idor/         # IDOR payload / 执行 / 结果模型
│   │   ├── bypass403/    # 403 绕过 payload / 执行 / 结果模型
│   │   └── jwt/          # JWT payload / 执行 / 结果模型
│   └── service/          # 公共领域服务（过滤、鉴权历史）
├── model/                # 配置、用户、AI 配置和比较结果模型
├── view/                 # Burp/Swing 界面
│   ├── binding/          # UI 与模型绑定
│   ├── component/        # 可复用面板和编辑器（含 AI 对话面板）
│   └── dialog/           # 专项扫描与选择对话框
└── utils/                # API、日志、i18n、JWT/HTTP 与路径工具
```

## 2. 核心数据流

### 2.1 捕获、重放与评分

```text
Burp HTTP 响应
  → HttpRequestHandler
  → ConfigRequestFilter
  → DataTableController
  → AuthController（去重和编排）
  → RequestReplayService
      ├── Unauthorized：移除认证头
      └── 用户身份：ProcessorChain 修改头和参数
  → RankService（与 Original 比较）
  → CompareSampleModel
  → DataTable / Metadata / Compare
```

- `ConfigRequestFilter` 根据启用状态、域名、方法、路径、状态码、扩展名和 Burp 工具来源筛选流量。
- `AuthController` 以“方法 + URL”去重，为每条请求生成 Original、Unauthorized 和已启用用户的样本。
- `RequestReplayService` 负责发包并把响应转换为 `MessageDataModel`。
- `ProcessorChain` 依次执行 `HeaderReplaceProcessor` 和 `ParamReplaceProcessor`；未授权重放单独移除配置的认证头。
- `RankService` 先把响应体经 `BodyNormalizer` 按 `L0 原文 → L1 标准 → L2 激进` 递进归一化，再比较状态码、哈希与长度：`StatusCode 30% / Hash 50% / Length 20%`，`Original` 固定 100 分。命中 L1/L2 时哈希分打折，避免过度归一化掩盖真实差异。分数是风险提示，不是漏洞结论。

### 2.2 AI 对话

```text
用户提问 / 右键发送到 AuthKit AI / AI 越权扫描
  → AiChatPanel（数据包按需拉取，不自动附带）
  → AiChatService 调用 LLM（OpenAI 兼容 / Anthropic / Ollama）
  → 回复含工具调用标记块？
      ├── GET_PACKETS      → 读数据表选中记录 → 回填
      ├── GET_PROXY_HISTORY / GET_SITEMAP → 弹窗由用户挑选候选 → 回填选中报文
      ├── SEND_HTTP_REQUEST → ToolApprovalCard 等用户决定 → AiToolService 发包 → 回填
      └── 无工具调用 → 渲染最终回答
```

该流程涉及后台线程与 EDT 的协作、用户选择/批准状态机，细节见 [AI 分析模块](ai-analysis.md)。

## 3. 主要模型

| 模型 | 职责 |
| --- | --- |
| `ConfigModel` | 插件开关、请求过滤、工具范围和需移除的认证头 |
| `AuthUserModel` | 单个测试身份的认证头、参数替换规则和启用状态 |
| `CompareSampleModel` | 一条业务请求及各身份对应的消息数据 |
| `MessageDataModel` | 单次请求/响应及状态码、长度、哈希、属性数、评分等元数据 |
| `AiConfigModel` | AI 对话配置（API Key、Base URL、模型、请求格式、单次最大包数），经 `Preferences` 持久化 |

界面中的 **用户** 对应代码中的 `AuthUserModel`，**鉴权对象** 指 `Original`、`Unauthorized` 或某个用户身份，**样本** 对应 `CompareSampleModel`。

## 4. 控制器与界面

- `DataTableController` 处理捕获、表格刷新、清空和导出。
- `DiffController` 对响应差异计算做防抖并更新对比面板。
- `ContextMenuController` 编排右键送测、鉴权字段处理、Fake IP 和专项扫描。
- `MainPanel` 是 Burp 页签根组件。左侧为工具栏、结果表和元数据表；右侧为对比、配置、用户、JWT 和 AI 页签。
- 用户新增、删除或重命名时，`MainPanel` 同步更新结果列、元数据行和对比对象。
- `MainPanel` 同时负责装配 AI 模块：创建并加载 `AiConfigModel`、`AiConfigBinder` 绑定配置面板，并把数据表选中记录作为上下文提供者注入 `AiChatPanel`。

## 5. 扩展能力

| 能力 | 主要实现 |
| --- | --- |
| CSV/HTML 导出 | `AuthResultExportService` |
| Fake IP 注入与 Intruder 爆破 | `FakeIpService` 标记请求；`FakeIpIntruderHttpHandler` 在每个 Intruder 数据包发送前注入不同随机 IP（含 XFF）；`FakeIpPayloadGeneratorProvider` 通过 Montoya `PayloadGeneratorProvider` 提供随机 IP 与 `X-Forwarded-For` 头 payload |
| 403 绕过扫描 | `core.scan.bypass403.*`、`Bypass403ScanDialog` |
| IDOR 扫描 | `core.scan.idor.*`、`IdorScanDialog` |
| JWT 工具与扫描 | `JwtPanel`（解码/编码/校验/爆破）、`JwtEditorTab`（请求编辑器内多 JWT 编辑与回写）、`JwtResponseEditorTab`（响应编辑器内查看）、`core.scan.jwt.*`、`JwtScanDialog` |
| 鉴权历史 | `AuthHistoryService`、`AuthHistorySelectDialog` |
| AI 对话分析 | `AiChatPanel`、`ToolApprovalCard`、`AiChatService`、`AiToolService`、`AiSessionService`（详见 [AI 分析模块](ai-analysis.md)） |
| AI 数据包来源与越权扫描 | `PacketSourceService`（Proxy 历史 / 站点地图候选读取与评分）、`PacketSelectDialog`（用户挑选，不默认全量读取）、`AiAuthScanDialog`（右键 `AI 越权扫描`） |
| 文本差异 | `TextDiffService`、`ComparePanel` |

## 6. 并发边界

- 入口创建 3 线程执行器，用于请求重放和右键菜单任务。
- Diff 使用单独的守护线程，并由 `DiffController` 做 180 ms 防抖。
- 专项扫描服务按任务创建有界线程池。
- AI 每次提问启动一个守护线程运行对话循环；需要发包批准时该线程在锁上挂起，由 EDT 上的按钮回调唤醒（见 [AI 分析模块](ai-analysis.md) 第 4 节）。
- 所有 Swing 状态变更必须切回 EDT；扩展卸载时会停止入口创建的线程池。
