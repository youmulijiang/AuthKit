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
│   ├── processor/
│   └── service/
├── model/
└── view/
```

新增或修改过滤、处理器、评分、Payload 和模型逻辑时，应在对应目录补充单元测试。Swing 测试应避免依赖真实 Burp 会话或不确定的线程时序。

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

新增或修改文案时，两种语言的同名 `.properties` 文件和键必须同步。运行时语言切换由 `I18n` 监听器刷新界面。

## 5. 变更检查

按变更类型检查以下事项：

| 变更 | 最小检查 |
| --- | --- |
| 请求过滤 | 覆盖允许与拒绝路径，确认 Burp 工具范围 |
| 认证重放 | 覆盖认证头移除、头替换和参数替换 |
| 评分/元数据 | 覆盖边界值，并确认表格与详情展示一致 |
| Swing 界面 | 确认更新在 EDT，用户增删改联动正常 |
| i18n 文案 | 同步 `en/`、`zh/`，确认运行时刷新 |
| 新依赖/打包 | 运行 `mvn clean package`，确认目标类进入 Shade JAR |

## 6. CI 与发布

推送 `v*` 标签会触发 `.github/workflows/release.yml`：

1. 分别使用 Java 17 和 Java 21 构建。
2. 检查打包 JAR 是否包含关键依赖。
3. 生成 `AuthKit-java17.jar` 和 `AuthKit-java21.jar`。
4. 创建 GitHub Release 并上传两个产物。

发布流程会跳过测试，因此发布前应在本地或其他 CI 阶段完整运行测试。
