# IDOR 扫描：URL Path 数字段启发式替换策略设计

Date: 2026-07-16

## 背景

现有 `IdorPayloadService` 已有三种策略：
1. `no-auth` — 删除鉴权字段
2. `numeric-param` — 对 query/body 数字参数启发式变形（×10、×20、+1）
3. `history-param` — 从代理历史中取同参数名的不同值

本次新增第四种策略 **`numeric-path`**，专门针对 URL Path 中的纯数字路径段（如 `/api/users/123/orders/456` 中的 `123` 和 `456`）进行启发式替换。

## 目标

对每个纯数字 Path 段，独立生成至多 6 个变体请求，用于检测基于资源 ID 的越权（IDOR/BOLA）。

## 设计

### 实现位置

`src/main/java/core/IdorPayloadService.java`

新增私有方法 `addNumericPathVariants(HttpRequest baseRequest, List<IdorScanVariant> variants)`，在 `generateVariants()` 中作为第 4 个策略调用：

```java
public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                               List<ProxyHttpRequestResponse> proxyHistory) {
    ...
    addNumericPathVariants(baseRequest, variants);   // 新增
    addProxyHistoryVariants(baseRequest, proxyHistory, variants);
    return variants;
}
```

### 逻辑

1. 取 `baseRequest.pathWithoutQuery()`，按 `/` 分割，跳过空段
2. 遍历每个段，若 `isAllDigits(segment)` 为 true：
   - 解析为 `long numValue`
   - 按以下顺序构建去重的变体值集合（跳过与原值相同及负数结果）：
     1. `numValue + 1`
     2. `numValue - 1`（若 `< 0` 则跳过）
     3. `"1"`
     4. `"0"`
     5. `"99999"`
     6. `value + "0"`（追加零，即 ×10）
   - 每个变体：重建 Path（仅替换该段）→ `request.withPath(newPath)` → 生成 `IdorScanVariant`
   - 每段上限：**6 个**（即允许全部 6 种变体通过）

### IdorScanVariant 字段映射

| 字段 | 值 |
|---|---|
| `technique` | `"numeric-path"` |
| `paramName` | `"path[N]"`（N 为 0-based 段索引） |
| `originalValue` | 原始数字字符串 |
| `newValue` | 变体数字字符串 |
| `request` | 替换后的请求 |

### Path 重建

```
segments = path.split("/", -1)
segments[N] = newValue
newPath = String.join("/", segments)
// 保留原始前导 "/"
```

### 常量变更

现有 `MAX_MUTATIONS_PER_PARAM = 3` 仅用于 `numeric-param` 和 `history-param`。
Path 策略使用独立常量 `MAX_MUTATIONS_PER_PATH_SEGMENT = 6`。

## 不变更的部分

- `IdorScanVariant` — 无需修改，现有字段足够
- `IdorScanResult` — 无需修改
- `IdorScanDialog` — 无需修改（`technique` 列直接显示 `"numeric-path"`）
- `AuthKit.java` — 无需修改（`generateVariants()` 已被调用）

## 测试要点

- Path 无数字段时不产生变体
- 多个数字段时各自独立产生变体（不交叉组合）
- `numValue - 1 < 0` 时跳过
- 变体值等于原值时跳过
- Path 重建保留前导 `/` 及其他非数字段不变
