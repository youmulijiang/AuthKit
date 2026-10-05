# AuthKit 文档

本文档目录保存面向用户与开发者的稳定说明；需求设计与实施记录不单独归档，以代码和提交历史为准。

| 文档 | 内容 |
| --- | --- |
| [架构说明](architecture.md) | 分层、核心数据流、主要模型、界面和并发边界 |
| [AI 分析模块](ai-analysis.md) | AI 对话、内置发包工具、批准流程、会话持久化与配置 |
| [开发指南](development.md) | 环境、构建、测试、i18n、变更检查和 CI |
| [用户使用说明](user-guide.md) | 安装、配置、操作流程和结果解读 |

## 阅读路径

| 你的任务 | 建议顺序 |
| --- | --- |
| 第一次接触项目 | [`AGENTS.md`](../AGENTS.md) → [架构说明](architecture.md) → [开发指南](development.md) |
| 改请求捕获、重放或评分 | [架构说明](architecture.md) 第 2、3、6 节 |
| 改 AI 对话或内置发包工具 | [AI 分析模块](ai-analysis.md) |
| 新增界面或文案 | [开发指南](development.md) 第 3、4 节 |
| 打包与发版 | [开发指南](development.md) 第 2、6 节 |

> 仓库根目录的 [`AGENTS.md`](../AGENTS.md) 与 [`CLAUDE.md`](../CLAUDE.md) 记录了强制约定（语言、注释、提交信息等），动手前先读。
