## AuthKit

> 面向渗透测试人员和安全工程师的 Burp Suite 越权检测辅助插件。

AuthKit 用于把一条业务请求快速扩展为 `Original / Unauthorized / 多角色` 对比样本，帮助你更高效地发现：

- 未授权访问
- 水平越权
- 垂直越权
- 对象级授权缺失（BOLA）

它支持**被动捕获流量**和**右键主动送测**，适合放在 Burp 的日常测试流程里使用。

---
## 界面


## 核心能力

- **多身份自动重放**：自动生成 `Original`、`Unauthorized`、`UserA/UserB/...` 对比结果
- **多维指标展示**：支持 `Length`、`Status Code`、`Hash`、`AttributeNum`、`Rank`
- **快速定位异常**：表格差异染色、元数据面板、`Response Diff`
- **右键菜单联动**：支持 `Send to AuthKit`、`Extract Auth to User`
- **灵活范围控制**：支持 `Domain Scope`、`Request Filter`、`Tool Type Scope`

---

## 适用场景

- 用 `Unauthorized` 快速排查未授权访问
- 用 `UserA / UserB` 对比水平越权
- 用 `User / Admin` 对比垂直越权
- 用参数替换验证资源 ID、租户 ID、用户 ID 等对象访问控制
- 在 `Proxy` / `Repeater` 中批量巡检高风险接口

---

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Burp Suite（支持 Montoya API）

### 本地构建

```bash
mvn clean package
```

产物示例：

```text
target/AuthKit-1.0-SNAPSHOT.jar
```

### 加载到 Burp

`Extensions` -> `Installed` -> `Add`

### 最小配置

1. 在 `Configuration` 中勾选 `Enable Plugin`
2. 在 `Auth Headers` 中填写要在未授权场景移除的头，例如：`Cookie`、`Authorization`、`Token`
3. 保持 `Tool Type Scope` 默认值：`Proxy`、`Repeater`
4. 在 `User` 中添加测试角色，并配置认证头或参数替换规则

---

## 使用流程

1. 在 `Proxy` / `Repeater` 中捕获请求，或右键选择 `Send to AuthKit`
2. 先看 DataTable 中的 `Length / Hash / AttributeNum / Rank`
3. 点击可疑鉴权对象列，联动查看 `Target` 的 `Response`
4. 在 `View` 中对比 `Source / Target / Diff`
5. 结合业务语义确认是否存在越权

---

## 怎么看结果

优先关注：

- `Unauthorized` 的 `Rank` 较高
- 不同角色的 `Hash`、`Length`、`AttributeNum` 很接近
- 状态码不同，但响应内容差异很小
- 本应被拒绝的请求仍返回业务字段或对象数据

说明：

- `Rank` 是风险提示，不是漏洞结论
- 高分优先看，低分不代表绝对安全
- 最终仍需结合业务逻辑人工确认

---

## 当前支持

- `Original / Unauthorized / 多用户` 对比
- `Auth Headers` 替换、`Param Replacement`
- `Response Diff` 与元数据透视
- `Tool Type Scope`（`Proxy` / `Repeater` / `Intruder` / `Extensions`）
- Burp 右键送测与认证头提取

---

## 说明

AuthKit 的定位是**越权检测辅助工具**：减少重复发包和手工比对成本，帮助你更快筛出高风险接口。