一、总体约束

仅使用 Java 17+

仅使用：

Java Swing

Burp Montoya API

指定第三方库（如 java-diff-utils）

禁止：

使用 JavaFX

使用外部 UI 框架

使用 Lombok

使用反射

使用 Spring / 任何容器框架

所有代码必须可编译运行

所有类必须有明确职责

不允许生成伪代码

不允许随意增加目录。

三、UI 开发规范
1. Swing 约束

所有 UI 必须基于 JPanel

不允许使用 JFrame 作为主界面

使用 JSplitPane 控制布局

所有 JTextArea：

等宽字体

不可编辑

所有 JTable：

使用 DefaultTableModel

禁止匿名内部类过度嵌套

2. 命名规范
   类型	规范
   JTable	tableXxx
   JTextArea	textAreaXxx
   JButton	btnXxx
   JPanel	panelXxx
   Service	XxxService

禁止拼音命名。

3. 事件处理规范

所有事件绑定必须在 bindEvents() 方法中完成

禁止在构造函数中直接写监听逻辑

禁止 lambda 内部超过 10 行代码

四、数据模型规范
1. CompareSample

必须包含：

private int id;
private String method;
private String url;
private MessageData original;
private MessageData lowPrivilege;
private MessageData unauth;

禁止把 HTTP 原始字符串直接放在 UI 类中。

2. MessageData

必须包含：

private String request;
private String response;
private int statusCode;
private int length;
private String hash;
五、Diff 规范

普通文本 diff 必须使用：

java-diff-utils

JSON diff 必须使用：

zjsonpatch

DiffService 结构：

public interface DiffService {
String diff(String a, String b);
}

禁止在 UI 层直接写 diff 逻辑。

六、风险控制规则

如果响应长度完全相同：

不直接判断为安全

如果 JSON 字段增加：

必须标记风险

必须预留忽略规则接口：

boolean shouldIgnore(String line);
七、Montoya API 规范

插件入口类必须：

public class AuthCompareExtension implements BurpExtension

必须在：

initialize(MontoyaApi api)

中：

保存 api 引用

注册 Suite Tab

注册必要的 HttpHandler（如需）

禁止在 initialize 中写 UI 逻辑。

八、线程安全规范

所有 Swing 更新必须：

SwingUtilities.invokeLater()

禁止在 UI 线程执行耗时 diff 运算

必须使用简单 ExecutorService 处理后台任务

九、代码质量限制

单个类不得超过 500 行

单个方法不得超过 50 行

禁止复制粘贴代码块

必须写中文注释说明模块用途

所有 public 方法必须有注释

十、LLM 输出限制

当 LLM 生成代码时必须：

按模块分文件输出

每个文件单独完整

不输出解释

不输出 Markdown

不输出多余文字

核心原则总结

UI 只负责展示

Service 负责逻辑

Model 负责数据

Diff 独立抽象

不允许逻辑污染 UI

不允许大一统类