# AuthKit — 首个多维度辅助检查鉴权安全的burpsuite插件

AuthKit 通过多身份重放与多维度响应对比，辅助发现**未授权访问、水平越权、垂直越权及 BOLA / IDOR 风险**，减少重复发包和手工比对。
![AuthKit Burp Suite 越权检测插件功能概览](img/Authkit.png)
> AuthKit 2.0：品质升级，优化响应归一化与越权评分算法，增强 AI 辅助分析，提升检测效率与使用体验。

[下载 JAR](https://github.com/youmulijiang/AuthKit/releases) · [使用指南](docs/user-guide.md) · [开发指南](docs/development.md)

## 核心功能

- **多身份对比**：以 `Original / Unauthorized / 多用户` 重放请求，支持认证头与参数替换。
- **响应评分**：默认展示 `Rank`，归一化处理动态噪声，结合响应差异筛选可疑接口。
- **AI 辅助分析**：按需读取数据包、对话分析与批准后发包验证，支持会话保存。
- **专项扫描**：403 Bypass、IDOR、JWT 与 AI 越权扫描。
- **JWT 工具**：解码、编码、签名校验与弱密钥测试，集成请求和响应编辑器。
- **日常操作**：右键送测、范围过滤、鉴权字段处理、CSV / HTML 导出及中英文切换。

## 快速开始

需要支持 Montoya API 的 Burp Suite 和 Java 17+。

1. 从 [Releases](https://github.com/youmulijiang/AuthKit/releases) 下载与 Burp Java 环境匹配的 JAR，在 `Extensions → Installed → Add` 中以 `Java` 类型加载。
2. 在 `Configuration` 中设置测试域名与捕获范围，并启用插件。
3. 在 `Auth Headers` 中填写未授权测试要移除的认证头，如 `Cookie`、`Authorization`；在 `User` 中添加其他测试身份。
4. 通过 `Proxy / Repeater` 捕获请求，或右键选择 `发送到 AuthKit`，查看各身份的 `Rank` 与响应差异。

AI 为可选功能，在 `Configuration → AI Analysis` 中配置模型服务即可使用。详细操作与常见问题见[使用指南](docs/user-guide.md)。

## 结果判读

优先检查未登录或其他用户身份下的高 `Rank` 结果，再通过 `Response Diff` 确认是否返回了无权访问的数据或执行了受限操作。**评分是风险提示，漏洞结论需结合业务权限人工确认。**

## 界面预览

<details>
<summary>展开查看配置、响应对比、AI 与 JWT 界面</summary>

| 配置与捕获过滤 | 多身份响应对比 |
| --- | --- |
| ![AuthKit 捕获过滤与鉴权配置](img/image.png) | ![多用户响应对比与 Rank 评分](img/image-1.png) |
| 用户与角色管理 | AI 辅助分析 |
| ![鉴权测试用户管理](img/image-2.png) | ![AI 越权分析对话](img/image-3.png) |
| JWT 工具 | Burp 右键菜单 |
| ![JWT 解码与签名校验](img/image-4.png) | ![AuthKit 右键测试菜单](img/image-5.png) |

</details>

## 源码构建

使用 JDK 17 和 Maven 3.8+：

```bash
mvn clean package
```

产物：`target/AuthKit-2.0.0.jar`。测试、版本与发布流程见[开发指南](docs/development.md)。

## 文档

| 文档 | 简介 |
| --- | --- |
| [使用指南](docs/user-guide.md) | 安装配置、操作流程、结果判读与常见问题。 |
| [AI 分析模块](docs/ai-analysis.md) | AI 对话、数据包工具、发包批准与会话管理。 |
| [开发指南](docs/development.md) | 构建测试、国际化、版本管理与发布流程。 |
| [架构说明](docs/architecture.md) | 代码分层、核心数据流与模块职责。 |
| [文档导航](docs/README.md) | 文档索引与推荐阅读顺序。 |

## 开发者
youmulijiang
作者是一个普通的安全开发成员,如果你对本工具感兴趣,请点击右上角的星星⭐ヾ(≧▽≦*)o

梨酱最喜欢星星啦
## License
本项目基于 MIT License 开源发布，可自由使用、修改与分发，详见 LICENSE 文件。 使用本项目代码时，请遵守对应开源协议，保留原始版权及声明；项目中参考与借鉴的第三方开源项目，均遵循其各自开源许可协议。