# 开发指南

## 1. 环境

- JDK 17
- Maven 3.8 或更高版本
- 支持 Montoya API 的 Burp Suite

项目没有 `.env`、启动脚本或必须配置的环境变量。依赖与编译版本以 [`pom.xml`](../pom.xml) 为准。

## 2. 构建与测试

```bash
mvn clean package
mvn test
mvn test -Dtest=ClassName
mvn test -Dtest=ClassName#methodName
```

`mvn clean package` 通过 Maven Shade Plugin 生成包含运行时依赖的 JAR，文件位于 `target/`。将该 JAR 加载到 Burp：`Extensions > Installed > Add`。

项目暂未配置独立的格式化或静态质量检查插件。提交前至少运行受影响测试；涉及装配、依赖或跨模块行为时运行 `mvn test` 和 `mvn clean package`。

## 3. 测试位置

测试位于 `src/test/java/`，使用 JUnit 5 和 Mockito，目录与生产代码分层基本对应：

```text
src/test/java/
├── controller/
├── core/
│   ├── normalize/
│   ├── processor/
│   ├── scan/
│   │   ├── bypass403/
│   │   ├── idor/
│   │   └── jwt/
│   └── service/
├── model/
├── utils/
└── view/
```

新增或修改过滤、处理器、评分、归一化、Payload 和模型逻辑时，应在对应目录补充单元测试。Swing 测试应避免依赖真实 Burp 会话或不确定的线程时序。

单测环境没有 Burp 运行时，Montoya 的工厂不可用。涉及 Montoya 类型的逻辑应通过注入解析函数解耦后再测，例如 `AiToolService` 的包内构造允许传入 `requestParser`，避免直接触碰 `ObjectFactoryLocator`。

未覆盖的组件：`AiChatPanel`、`ToolApprovalCard` 等 AI 对话界面。改动对话循环或工具流程后需手工验证：数据包工具按需拉取（有选中/无选中）、三种批准决定、轮数上限、拒绝后的中性反馈、会话保存与恢复。

## 4. 国际化

用户可见文本统一通过：

```java
I18n.getInstance().text("area", "key")
```

资源文件位于：

```text
src/main/resources/i18n/
├── en/
└── zh/
```

`area` 与 `.properties` 文件名同名，现有分区：`ai`、`auth_context_menu`、`common`、`compare`、`configuration`、`data_table`、`jwt`、`main`、`message`、`metadata_table`、`toolbar`、`user`。

新增或修改文案时，两种语言的同名 `.properties` 文件和键必须同步。运行时语言切换由 `I18n` 监听器刷新界面。

## 5. 变更检查

按变更类型检查以下事项：

| 变更 | 最小检查 |
| --- | --- |
| 请求过滤 | 覆盖允许与拒绝路径，确认 Burp 工具范围 |
| 认证重放 | 覆盖认证头移除、头替换和参数替换 |
| 评分/元数据 | 覆盖边界值，并确认表格与详情展示一致 |
| 归一化规则 | 覆盖各级别（L0/L1/L2）命中与不命中，确认不影响真实差异判定 |
| 专项扫描 Payload | 覆盖各策略分支，确认结果模型字段一致 |
| AI 对话/发包 | 三种批准决定、轮数上限、拒绝后中性反馈、会话保存与恢复（无单测，需手工验证） |
| Swing 界面 | 确认更新在 EDT，用户增删改联动正常 |
| i18n 文案 | 同步 `en/`、`zh/`，确认运行时刷新 |
| 版本号 | 只改 `pom.xml` 的 `<version>`，确认产物名与插件内显示一致（见第 6 节） |
| 新依赖/打包 | 运行 `mvn clean package`，确认目标类进入 Shade JAR |

## 6. 版本号

版本号**单一来源是 `pom.xml` 的 `<version>`**：

- 构建时资源过滤把版本写入 `src/main/resources/version.properties`（该文件参与过滤，其余资源不参与，避免误改二进制或文案）。
- `AuthKit.AUTHKIT_VERSION` 运行时读取该文件（IDE 直接运行 classes 时回退到 JAR manifest 的 `Implementation-Version`），欢迎横幅与日志都用它。
- JAR manifest 同时写入 `Implementation-Version`，便于外部工具查询。

因此改版本只需改 `pom.xml` 一处，插件内显示、产物文件名、manifest 自动一致。

## 7. CI 与发布

推送 `v*` 标签会触发 `.github/workflows/release.yml`：

1. 校验标签版本与 `pom.xml` 的 `<version>` 一致（不一致直接失败，避免包与代码版本号不符）。
2. 分别使用 Java 17 和 Java 21 构建。
3. 检查打包 JAR 是否包含关键依赖，并校验 JAR 内 `version.properties` 与 pom 版本一致。
4. 生成 `AuthKit-<version>-java17.jar` 和 `AuthKit-<version>-java21.jar`。
5. 创建 GitHub Release 并上传两个产物。

发布流程会跳过测试，因此发布前应在本地或其他 CI 阶段完整运行测试。
