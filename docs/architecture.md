# 架构说明

## 1. 总体结构

AuthKit 是单模块 Maven 项目。`AuthKit.java` 实现 Montoya `BurpExtension`，负责创建对象、连接回调并向 Burp 注册界面和处理器；项目不使用依赖注入框架。

```text
src/main/java/
├── AuthKit.java          # 扩展入口与对象装配
├── controller/           # 流程编排、界面事件协调
├── core/                 # 重放、评分、Diff、Payload 等核心能力
│   ├── processor/        # 请求修改责任链
│   └── service/          # 过滤、历史记录和专项扫描
├── model/                # 配置、用户和比较结果模型
├── view/                 # Burp/Swing 界面
│   ├── binding/          # UI 与模型绑定
│   ├── component/        # 可复用面板和编辑器
│   └── dialog/           # 专项扫描与选择对话框
└── utils/                # API、日志、i18n、HTTP 与路径工具
```

## 2. 核心数据流

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
- `RankService` 比较状态码、长度、哈希和属性数，输出 0–100 的相似度风险提示；分数不是漏洞结论。

## 3. 主要模型

| 模型 | 职责 |
| --- | --- |
| `ConfigModel` | 插件开关、请求过滤、工具范围和需移除的认证头 |
| `AuthUserModel` | 单个测试身份的认证头、参数替换规则和启用状态 |
| `CompareSampleModel` | 一条业务请求及各身份对应的消息数据 |
| `MessageDataModel` | 单次请求/响应及状态码、长度、哈希、属性数、评分等元数据 |

界面中的 **用户** 对应代码中的 `AuthUserModel`，**鉴权对象** 指 `Original`、`Unauthorized` 或某个用户身份，**样本** 对应 `CompareSampleModel`。

## 4. 控制器与界面

- `DataTableController` 处理捕获、表格刷新、清空和导出。
- `DiffController` 对响应差异计算做防抖并更新对比面板。
- `ContextMenuController` 编排右键送测、认证提取、Fake IP 和专项扫描。
- `MainPanel` 是 Burp 页签根组件。左侧为工具栏、结果表和元数据表；右侧为对比、配置、用户和 JWT 页签。
- 用户新增、删除或重命名时，`MainPanel` 同步更新结果列、元数据行和对比对象。

## 5. 扩展能力

| 能力 | 主要实现 |
| --- | --- |
| CSV/HTML 导出 | `AuthResultExportService` |
| Fake IP 注入与 Intruder Payload | `FakeIpService`、`FakeIpIntruderHttpHandler`、`FakeIpPayloadGeneratorProvider` |
| 403 绕过扫描 | `Bypass403PayloadService`、`Bypass403ScanService`、`Bypass403ScanDialog` |
| IDOR 扫描 | `IdorPayloadService`、`IdorScanService`、`IdorScanDialog` |
| JWT 查看与扫描 | `JwtEditorTab`、`JwtPanel`、`JwtPayloadService`、`JwtScanService` |
| 文本差异 | `TextDiffService`、`ComparePanel` |

## 6. 并发边界

- 入口创建 3 线程执行器，用于请求重放和右键菜单任务。
- Diff 使用单独的守护线程，并由 `DiffController` 做 180 ms 防抖。
- 专项扫描服务按任务创建有界线程池。
- 所有 Swing 状态变更必须切回 EDT；扩展卸载时会停止入口创建的线程池。
