# AI 分析模块

AI 分析让模型直接阅读 Burp 捕获的数据包，并在对话中**实际发包**迭代验证越权结论。本文说明该模块的结构、流程与边界；界面使用方式见[用户使用说明](user-guide.md#ai-辅助分析)。

## 1. 模块结构

| 类 | 层 | 职责 |
| --- | --- | --- |
| `AiConfigModel` | model | 纯数据 POJO：API Key、Base URL、模型、请求格式、单次最大包数；通过 `java.util.prefs.Preferences` 本地持久化 |
| `AiChatService` | core | 组装提示词并调用 LLM，返回 `Reply(reasoning, content)` |
| `AiToolService` | core | 解析模型回复中的工具调用标记块（数据表数据包 / Proxy 历史 / 站点地图 / 发送请求），执行发包并返回响应文本 |
| `PacketSourceService` | core | 读取 Proxy 历史 / 站点地图候选并评分排序，供弹窗选择；数据来源可注入便于单测 |
| `AiSessionService` | core | 对话历史与 JSON 文件互转 |
| `AiChatPanel` | view.component | 对话界面：气泡渲染、发送、保存/加载、工具编排（取数 + 发包批准）、对话循环 |
| `ToolApprovalCard` | view.component | 对话流内联的发包批准卡片 |
| `AskUserCard` | view.component | 对话流内联的 AI 询问卡片（黄色；最多 3 个建议选项 + 自定义输入 + 取消） |
| `PacketSelectDialog` | view.dialog | 数据包选择弹窗（后台异步读取 + 候选表格 + 域名下拉筛选 + 建议预选 + 条数上限） |
| `AiAuthScanDialog` | view.dialog | AI 越权扫描弹窗（可编辑提示词 + AI 发送的数据包表格 + 请求/响应查看器，非模态） |
| `AiConfigBinder` | view.binding | `ConfigurationPanel` 的 AI 区块 ↔ `AiConfigModel`，含初始回填与持久化 |

装配在 `MainPanel` 中完成：创建并加载 `AiConfigModel`，绑定配置面板，构造 `AiChatPanel` 挂到右侧 `AI` 页签，并注入数据包上下文提供者（`setContextPacketsSupplier`）与数据包来源服务（`setPacketSourceService`）；`ContextMenuController` 负责 `AI 越权扫描` 菜单项与扫描弹窗。

## 2. 对话流程

```text
用户提问（或右键"发送到 AuthKit AI"主动送包 / "AI 越权扫描"）
  → AiChatPanel 追加用户气泡
  → 首次提问时注入 system 提示词（按可用工具附加工具说明）
  → 后台线程调用 AiChatService
  → 解析回复中的工具调用标记块
      ├── 拉取数据包   → 读取数据表选中记录 → 回填历史与摘要气泡 → 继续下一轮
      ├── 读取 Proxy/站点地图
      │     ├── 已指定关键词 → 选择弹窗（预填关键词）→ 回填选中报文
      │     └── 未指定范围   → 询问卡片（选择数据包 / 重新描述 / 自定义 / 取消）
      │            ├── 选择数据包 或 自定义关键词 → 选择弹窗 → 回填选中报文
      │            ├── 重新描述 → 中性反馈 + 聚焦输入框 → 等用户下一轮
      │            └── 取消     → 中性反馈（不提供数据）
      ├── 询问用户     → 黄色询问卡片（选项 / 自定义回答 / 取消）→ 回填用户回答
      ├── 发送请求     → 插入 ToolApprovalCard 等待用户决定
      │                   ├── 允许 → AiToolService 发包 → 响应回填 → 继续下一轮
      │                   └── 拒绝 → 回传中性反馈 → 继续下一轮
      └── 无工具调用   → 渲染最终回答，结束
```

要点：

- **数据包按需拉取**：数据包**不会**随提问自动附带。模型通过 `<<<GET_PACKETS>>>` 工具主动拉取数据表当前选中记录的 `Original / Unauthorized / 各用户` 报文（`buildPacketPrompt`，响应体截断至 8000 字符），是否拉取由模型按用户需求决定；右键"发送到 AuthKit AI"仍是用户主动送包。
- **Burp 数据不默认全量读取**：`<<<GET_PROXY_HISTORY>>>` / `<<<GET_SITEMAP>>>` 只表达"想读"，实际取数与裁剪由用户在弹窗中完成（见第 3 节）。
- **提示词注入**：`AiChatPanel` 持有 system 提示词，按当前可用工具拼接——有数据包提供者时附加数据表工具说明，注入了数据包来源服务时附加 Proxy/站点地图工具说明，启用发包开关时附加发包工具说明，并要求模型只在确有必要时才调用工具。
- **工具可用性**：未启用发包开关时不识别发包标记，无数据包提供者时不识别数据表标记，无来源服务时不识别 Proxy/站点地图标记（此类回复按最终回答处理）。
- **轮数上限**：单轮提问的工具调用轮数上限为 `MAX_TOOL_ROUNDS = 10`。触顶后不再执行，改为要求模型直接给结论，避免模型循环调用失控。
- **思考过程**：`Reply.reasoning` 可能为 `null`；兼容 `reasoning_content`（DeepSeek 风格）、Anthropic `thinking`、Ollama `thinking` 字段。

## 3. 内置工具

### 工具协议

模型通过在回复中输出标记块调用工具，五类工具共用 `<<<END>>>` 作为结束标记（询问工具正文由 `parseAskRequest` 解析为问题与选项，`-`/`*` 开头的行是选项，最多 3 个）：

```text
<<<GET_PACKETS>>>
<<<END>>>

<<<GET_PROXY_HISTORY>>>optional keyword<<<END>>>

<<<GET_SITEMAP>>>optional keyword<<<END>>>

<<<SEND_HTTP_REQUEST>>>
GET /api/users/2 HTTP/1.1
Host: example.com
<<<END>>>

<<<ASK_USER>>>
question: <what you need to know>
- <option 1>
- <option 2>
- <option 3>
<<<END>>>
```

`AiToolService.parseToolCall` 定位最先出现且带结束标记的一处块，返回 `ToolCall(type, payload)`：

| 工具 | 类型 | 处理方式 |
| --- | --- | --- |
| 数据表数据包 | `GET_PACKETS` | 面板经 `contextPacketsSupplier` 读取数据表选中记录（切到 EDT 读取 Swing 状态），以 `[tool] HTTP packets ...` 消息回填历史并渲染摘要气泡；**只读本地数据，无需批准** |
| Proxy 历史 | `PROXY_HISTORY` | 打开 `PacketSelectDialog` 由用户挑选，仅回填选中报文；用户取消则回传中性拒绝反馈 |
| 站点地图 | `SITEMAP` | 同上，候选来源为 `siteMap().requestResponses()` |
| 发送请求 | `SEND_REQUEST` | 经批准卡片由用户决定后，`requestParser` 解析为 `HttpRequest` 交 Montoya 发送，响应原文（截断至 8000 字符）作为 `[tool] HTTP Response ...` 回填历史 |
| 询问用户 | `ASK_USER` | 插入黄色 `AskUserCard`（问题 + 最多 3 个建议选项 + 自定义输入 + 取消），把用户回答作为 `[tool] The user chose/replied ...` 回填；取消则回传 `ASK_CANCEL_FEEDBACK`（本轮不得再问） |

`parseToolCall` 为静态纯字符串处理，不依赖 Montoya，可在任意线程调用。

`AiToolService` 支持注入 `requestParser`（包内可见构造）以便单元测试，避免依赖真实 Montoya `ObjectFactoryLocator`。当 Montoya API 不可用（如单测环境）时，`AiChatPanel` 构造失败降级，`toolService` 为 `null`，界面上发包开关置灰。

### 数据访问门控（Proxy 历史 / 站点地图）

参考 portswigger/mcp-server 的数据访问门控思路：Burp 历史数据可能包含敏感会话内容，因此**不默认全量读取**，每次都由用户在弹窗中选择。

- `PacketSourceService` 封装读取：`read(source, keyword)` 按来源取全量后过滤静态资源、可选关键词，并按**越权测试价值评分**降序排列（路径数字段或 `id=` 类参数 +40、带查询参数 +20、JSON 响应 +15、可写方法 +10；`OPTIONS/HEAD/TRACE` 与 5xx 降权），候选上限 300 条。读取分两遍：第一遍只取廉价字段（方法 / URL / 状态码 / Content-Type）用于过滤与排序，第二遍仅对最终展示的 ≤ 300 条组装报文并读取响应长度——避免上万条 Proxy 历史下逐条解码响应体。
- `PacketSelectDialog` 展示候选表格（方法 / Host / 路径 / 状态码 / 长度 / 评分），**预选评分 ≥ 40 的高价值项**作为建议，并提供"仅含对象 ID / 仅带参数 / 仅 2xx"快捷筛选；用户可增删选择。
- **域名（Host）二次筛选**：候选读取完成后用其中的 Host 重建下拉框（首项"全部域名"，尽量保留用户当前选择），切换下拉框只重新填表、不重新读取数据源；Host / 路径列对非法 URL 做容错解析（`URI` 失败时手工按 `://`、`/?#` 切分），保证域名列不为空。
- **异步读取与兜底**：读取在后台线程完成，EDT 上先显示"正在读取…"并禁用按钮，避免弹窗长时间空白或卡死；后台结果只允许最后一次（`loadToken`）在弹窗未关闭时生效。关键词未命中时依次退化为：`sanitizeKeyword` 取出的首个 ASCII 词元 → 不过滤展示全部候选，并在弹窗中以醒目提示说明"关键词未匹配，已改为显示全部候选"，因此**弹窗不会因为模型给了整句描述或错误关键词而空白**。
- **未指定范围时先询问**：模型没在标记块里给出 URL / 域名 / 关键词时，不直接弹窗，而是用通用询问卡片 `AskUserCard` 问用户怎么继续（问题与选项由插件给出）：
  - `选择数据包`：继续打开 `PacketSelectDialog`，由用户自己挑
  - `重新描述`：放弃本次读取，回传 `PACKET_REDESCRIBE_FEEDBACK`（要求模型本轮不得再调该工具）并把焦点交回输入框，等用户补充范围
  - `自定义输入`：把用户输入的文本当作关键词预筛候选后打开选择弹窗
  - `取消`：不提供任何数据，回传 `PACKET_ACCESS_DENIED_FEEDBACK`
  - 指定了关键词则跳过建议卡片，直接弹窗并把关键词预填到筛选框；system 提示词因此要求模型尽量带上关键词
- 单次最多提供 20 条（`MAX_SEND`）；"全部发送"需二次确认且同样受上限约束——对应"不能读取全部数据包，除非用户要求"。
- 用户取消弹窗时回传 `PACKET_ACCESS_DENIED_FEEDBACK`（中性措辞，禁止重试）。
- 数据来源与报文组装函数均可注入（`PacketSourceService` 包内构造），单测无需真实 Burp 环境。

### AI 越权扫描入口

右键 `AI 越权扫描` → `AiAuthScanDialog`（布局与其他专项扫描弹窗一致）：

- **配置区**：待分析数据包（初始为右键选中项，可从 Proxy 历史 / 站点地图挑选）+ **内置提示词（可编辑，默认 `prompt.authScan`）**
- **结果区**：`AI 发送的数据包` 表格 + 请求 / 响应查看器，选中行即可查看 AI 实际发出的报文
- 点「开始 AI 越权扫描」后切到 `AI` 选项卡，`AiChatPanel.startAuthScan(packets, prompt)` 注入报文与（用户编辑后的）提示词启动对话
- 弹窗为**非模态**：扫描由对话驱动、发包仍需用户逐次批准，用户必须能回到 AI 选项卡操作批准卡片
- 发包回填链路：`AiToolService.executeToolCallWithResponse` 返回完整报文 → `AiChatPanel.notifyRequestSent` → `ToolSendListener.onRequestSent`（EDT）→ 弹窗追加表格行并联动查看器；弹窗关闭时注销监听

### 批准流程

工具默认开启（`Allow AI to send requests` 初始勾选），但**每次发包都必须经用户确认**：

| 决定 | 行为 |
| --- | --- |
| `Deny` | 不发送，并向模型回传 `TOOL_DENIAL_FEEDBACK`（"未授权，请勿重试"），措辞刻意中性，不给模型可利用的上下文 |
| `Approve once` | 仅放行本次 |
| `Approve for session` | 本会话后续发包免确认（`toolSessionApproved`） |

批准卡片由 `ToolApprovalCard` 内联插入对话流（非弹窗），点击后按钮行**原位替换**为决定回执（✔/✖ + 时间），卡片作为完整记录留在对话中。清空对话或加载会话会重置会话级允许状态。

## 4. 线程模型

- 每次提问启动一个守护线程 `authkit-ai-chat` 运行对话循环；循环中不触碰 Swing 组件。线程引用存在 `chatThread`，供暂停时打断。
- **暂停**：生成中发送按钮变为"暂停"，`doPause()` 置 `cancelRequested` 并 `interrupt()` 循环线程（打断 HTTP 请求或等待用户操作的锁）；循环在每轮开始与模型返回后检查标志，命中即 `finishCancelled()` 收尾，不再渲染最终回答。
- 拉取数据包时循环线程通过 `SwingUtilities.invokeAndWait` 切到 EDT 读取表格选中状态（`getSelectedSample` 依赖 Swing 表格）。
- 需要用户批准时，后台线程在 `awaitApprovalOnEdt` 中 `lock.wait()` 挂起；EDT 上的按钮回调写入决定、`notifyAll()` 唤醒后循环继续。
- 需要用户回答时，`awaitUserAnswerOnEdt` 在 EDT 上插入询问卡片并阻塞等待（选项 / 自定义 / 取消三种结果），AI 提问与数据包范围确认共用这一条路径。
- 读取 Proxy 历史 / 站点地图时同理：未指定范围先经询问卡片确认，再经 `selectPacketsOnEdt` 在 EDT 上打开模态选择弹窗，循环线程都在锁上等待用户操作。
- 所有界面变更（气泡、卡片、thinking 占位）通过 `SwingUtilities.invokeLater` 切回 EDT。

这是本模块最容易出错的地方：改动工具流程时不要破坏 `resolve → notify` / 弹窗 `notifyAll` 这两条链，否则后台线程会永久挂起。

## 5. 会话持久化

`AiSessionService` 把历史序列化为 JSON（手工拼装 + 转义感知解析，与 `AiChatService` 的 JSON 处理风格一致）：

- 保存时**跳过 `system` 消息**，恢复时由面板重新注入，避免提示词随文件漂移。
- 恢复后重建气泡：数据包消息（含数据包工具的 `[tool] HTTP packets ...` 回填）只展示摘要，其余工具响应消息以记录样式还原，不重放批准交互。

## 6. 配置持久化

`AiConfigModel` 使用 `java.util.prefs.Preferences` 保存五项：`ai.apiKey`、`ai.baseUrl`、`ai.model`、`ai.format`、`ai.maxPackets`。请求格式限定为 `OpenAI` / `Anthropic` / `Ollama`（`REQUEST_FORMATS`），单次最大包数区间为 1–20。

`AiConfigBinder` 负责双向同步：启动时回填 UI，控件变化写回模型并落盘，`Test Connection` 触发一次最小请求验证连通性。

## 7. 界面渲染约定

- 每条消息是一张卡片：彩色角色标签 + 时间戳，复制按钮悬停才显示。
- 气泡宽度**收缩贴字**（短文本不铺满），用户侧上限 60%、AI 侧 85% 视口宽。
- AI 回复的 Markdown 经 `markdownToHtml` 渲染；思考过程默认折叠，点击 `▶/▼` 展开。
- thinking 占位也是气泡（白灰），与消息气泡共用尺寸测量逻辑。
- 用户气泡嵌在内部滚动宿主里：高度上限 `MAX_USER_BUBBLE_HEIGHT = 240`，超出则气泡内出现滚动条（插入时视口复位到顶部，视口变化时经 aligner 的 `KEY_SCROLL_HOST` 重算上限）。

改动这些渲染逻辑前，先看 [`CLAUDE.md`](../CLAUDE.md) 与代码内注释中记录的 Swing 测量陷阱（显式 `preferredSize` 会短路 `getPreferredSize`、BoxLayout 会摊薄间距等）。

## 8. 测试

已覆盖：

- `core/AiSessionServiceTest`：JSON 往返、转义、异常输入
- `core/AiToolServiceTest`：`parseToolCall` 的标记块解析（多类工具、优先级、未闭合块）与注入 `requestParser` 的发送路径
- `core/PacketSourceServiceTest`：候选过滤（静态资源 / 关键词）、评分排序、来源选择、数据源异常降级

未覆盖：`AiChatPanel` / `ToolApprovalCard` / `AskUserCard` / `PacketSelectDialog` / `AiAuthScanDialog` 等 Swing 组件。改动对话循环或工具流程后，需要手工验证：数据表数据包按需拉取（有选中/无选中）、询问卡片四条路径（选项 / 自定义 / 重新描述 / 取消）、Proxy 历史与站点地图弹窗选择（确认 / 取消 / 全部发送二次确认）、AI 越权扫描弹窗（编辑提示词、结果表格回填、请求响应查看器）、发包批准三种决定、轮数上限、会话保存与恢复。
