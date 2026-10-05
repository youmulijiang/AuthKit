# AGENTS.md

## 1. 项目概述

AuthKit 是基于 Java 17、Swing 和 Burp Montoya API 的 Burp Suite 鉴权测试扩展。
它捕获 HTTP 流量，以 `Original / Unauthorized / 多用户` 身份重放请求，并比较响应以辅助发现 BOLA、水平越权和垂直越权。
`AuthKit.java` 是装配入口；业务代码按 `controller / core / model / view / utils` 分层，测试位于 `src/test/java`。
核心链路为：请求过滤 → 身份重放 → 响应评分 → Swing 界面展示。

详细结构、数据流和术语见 [架构说明](docs/architecture.md)。

## 2. 快速命令

```bash
mvn clean package                         # 测试并构建含依赖的 JAR
mvn test                                  # 运行全部测试
mvn test -Dtest=ClassName                 # 运行单个测试类
mvn test -Dtest=ClassName#methodName      # 运行单个测试方法
```

项目当前没有独立启动脚本、格式化命令或环境变量文件。构建产物位于 `target/`，在 Burp 的 `Extensions > Installed > Add` 中加载。

更多构建、CI 和验证说明见 [开发指南](docs/development.md)。

## 3. 强制约束

- 使用 Java 17；不引入 Lombok 或注解处理器，模型保持普通 Java POJO。
- 保持现有包结构，不新增仓库级统一包名前缀。
- 注释和提交信息使用中文。
- 所有用户可见文本必须通过 `I18n.getInstance().text(area, key)` 获取，并同步维护 `src/main/resources/i18n/en/` 与 `zh/`。
- Swing 组件更新必须在 EDT 中执行；后台重放、扫描和 Diff 不得阻塞 EDT。
- 网络安全测试能力仅用于合法授权环境。

## 4. 文档索引

- [文档导航](docs/README.md)
- [架构说明](docs/architecture.md)
- [AI 分析模块](docs/ai-analysis.md)
- [开发指南](docs/development.md)
- [用户使用说明](README.md)
