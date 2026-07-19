# IDOR Numeric-Path Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fourth IDOR scanning strategy that heuristically replaces numeric URL path segments (e.g., `/api/users/123`) with up to 6 mutations per segment to detect BOLA/IDOR vulnerabilities.

**Architecture:** One new private method `addNumericPathVariants()` added to `IdorPayloadService`, wired into the existing `generateVariants()` call sequence. Each pure-digit path segment produces independent variants using mutations: n+1, n-1 (if ≥ 0), 1, 0, 99999, value+"0" — deduplicated and skipping the original value.

**Tech Stack:** Java 17, JUnit 5, Mockito 5, Burp Montoya API (`HttpRequest.withPath()`)

## Global Constraints

- Java 17 — switch expressions, records, sealed classes are fine
- No Lombok, no annotation processors
- Package: `core` (no top-level prefix)
- All user-facing strings via `I18n` — this feature adds none, so no i18n changes needed
- Test runner: `mvn test`
- Existing constant `MAX_MUTATIONS_PER_PARAM = 3` must not be changed; path strategy uses its own constant

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/core/IdorPayloadService.java` | Modify | Add constant + new private method + wire into `generateVariants()` |
| `src/test/java/core/IdorPayloadServiceTest.java` | Create | Unit tests for the new numeric-path strategy |

---

### Task 1: Write failing tests for numeric-path strategy

**Files:**
- Create: `src/test/java/core/IdorPayloadServiceTest.java`

**Interfaces:**
- Consumes: `IdorPayloadService.generateVariants(HttpRequest, List<ProxyHttpRequestResponse>)`
- Consumes: `IdorScanVariant` fields: `technique()`, `paramName()`, `originalValue()`, `newValue()`, `request()`

- [ ] **Step 1: Create the test file**

```java
package core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdorPayloadServiceTest {

    private final IdorPayloadService service = new IdorPayloadService();

    /** 过滤出 technique == "numeric-path" 的变体 */
    private List<IdorScanVariant> pathVariants(List<IdorScanVariant> all) {
        return all.stream().filter(v -> "numeric-path".equals(v.technique())).collect(Collectors.toList());
    }

    private HttpRequest mockRequest(String path) {
        HttpRequest base = mock(HttpRequest.class);
        HttpRequest mutated = mock(HttpRequest.class);
        when(base.pathWithoutQuery()).thenReturn(path);
        when(base.withPath(anyString())).thenReturn(mutated);
        when(base.parameters()).thenReturn(List.of());
        when(base.httpService()).thenReturn(null);
        // removeAuthHeaders() calls headers() — return empty list so it's a no-op
        when(base.headers()).thenReturn(List.of());
        return base;
    }

    @Test
    @DisplayName("单个数字段应生成最多6个不重复且非原值的变体")
    void singleNumericSegment_generates6Variants() {
        HttpRequest base = mockRequest("/api/users/123");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        assertEquals(6, variants.size());
        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertTrue(newValues.contains("124"),  "n+1");
        assertTrue(newValues.contains("122"),  "n-1");
        assertTrue(newValues.contains("1"),    "first record");
        assertTrue(newValues.contains("0"),    "zero boundary");
        assertTrue(newValues.contains("99999"),"large number");
        assertTrue(newValues.contains("1230"), "append zero");

        // 所有变体的元数据一致
        variants.forEach(v -> {
            assertEquals("numeric-path", v.technique());
            assertEquals("path[3]", v.paramName());
            assertEquals("123", v.originalValue());
        });
    }

    @Test
    @DisplayName("多个数字段各自独立产生变体，互不干扰")
    void multipleNumericSegments_independentVariantsPerSegment() {
        HttpRequest base = mockRequest("/api/users/10/orders/20");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        // 每段最多6个，两段共12个
        assertEquals(12, variants.size());

        long seg10 = variants.stream().filter(v -> "path[3]".equals(v.paramName())).count();
        long seg20 = variants.stream().filter(v -> "path[5]".equals(v.paramName())).count();
        assertEquals(6, seg10);
        assertEquals(6, seg20);
    }

    @Test
    @DisplayName("无数字段的路径不产生 numeric-path 变体")
    void noNumericSegments_noPathVariants() {
        HttpRequest base = mockRequest("/api/users/profile");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        assertTrue(variants.isEmpty());
    }

    @Test
    @DisplayName("数字段为 0 时：n-1 为负数跳过，'0' 等于原值跳过")
    void segmentIsZero_skipsNegativeAndOriginal() {
        HttpRequest base = mockRequest("/api/items/0");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertFalse(newValues.contains("-1"),  "负数应跳过");
        assertFalse(newValues.contains("0"),   "原值应跳过");
        assertTrue(newValues.contains("1"),    "n+1");
        assertTrue(newValues.contains("99999"));
        assertTrue(newValues.contains("00"),   "append zero");
    }

    @Test
    @DisplayName("数字段为 1 时：'1' 等于原值跳过，'0' 由 n-1 产生不重复")
    void segmentIsOne_deduplicatesCorrectly() {
        HttpRequest base = mockRequest("/api/items/1");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertFalse(newValues.contains("1"), "原值应跳过");
        assertTrue(newValues.contains("2"),  "n+1");
        assertTrue(newValues.contains("0"),  "n-1");
        assertEquals(1, newValues.stream().filter("0"::equals).count(), "'0' 不应重复出现");
        assertTrue(newValues.contains("99999"));
        assertTrue(newValues.contains("10"), "append zero");
    }

    @Test
    @DisplayName("withPath 按正确的新路径被调用")
    void withPath_calledWithCorrectPath() {
        HttpRequest base = mockRequest("/api/users/5");

        service.generateVariants(base, List.of());

        // n+1 → /api/users/6
        verify(base).withPath("/api/users/6");
        // n-1 → /api/users/4
        verify(base).withPath("/api/users/4");
        // 1   → /api/users/1
        verify(base).withPath("/api/users/1");
        // 0   → /api/users/0
        verify(base).withPath("/api/users/0");
        // 99999 → /api/users/99999
        verify(base).withPath("/api/users/99999");
        // append zero → /api/users/50
        verify(base).withPath("/api/users/50");
    }
}
```

- [ ] **Step 2: Run the tests — confirm they all FAIL**

```
mvn test -Dtest=IdorPayloadServiceTest
```

Expected: compilation error or test failures — `addNumericPathVariants` does not exist yet.

---

### Task 2: Implement `addNumericPathVariants()` and wire it into `generateVariants()`

**Files:**
- Modify: `src/main/java/core/IdorPayloadService.java`

**Interfaces:**
- Produces: `addNumericPathVariants(HttpRequest baseRequest, List<IdorScanVariant> variants)` — private, void
- Constant added: `private static final int MAX_MUTATIONS_PER_PATH_SEGMENT = 6;`

- [ ] **Step 1: Add the constant at the top of the class (after existing `MAX_MUTATIONS_PER_PARAM`)**

In `IdorPayloadService.java`, find:
```java
private static final int MAX_MUTATIONS_PER_PARAM = 3;
```
Add immediately after:
```java
private static final int MAX_MUTATIONS_PER_PATH_SEGMENT = 6;
```

- [ ] **Step 2: Wire the new method into `generateVariants()`**

Find:
```java
    public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                                   List<ProxyHttpRequestResponse> proxyHistory) {
        if (baseRequest == null) return List.of();
        List<IdorScanVariant> variants = new ArrayList<>();
        addNoAuthVariant(baseRequest, variants);
        addNumericParamVariants(baseRequest, variants);
        addProxyHistoryVariants(baseRequest, proxyHistory, variants);
        return variants;
    }
```

Replace with:
```java
    public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                                   List<ProxyHttpRequestResponse> proxyHistory) {
        if (baseRequest == null) return List.of();
        List<IdorScanVariant> variants = new ArrayList<>();
        addNoAuthVariant(baseRequest, variants);
        addNumericParamVariants(baseRequest, variants);
        addNumericPathVariants(baseRequest, variants);
        addProxyHistoryVariants(baseRequest, proxyHistory, variants);
        return variants;
    }
```

- [ ] **Step 3: Add the `addNumericPathVariants()` method**

Add this private method after `addNumericParamVariants()` and before `addProxyHistoryVariants()`:

```java
    /** 策略 4：对 URL path 中的纯数字路径段启发式替换，每段独立生成最多 6 个变体 */
    private void addNumericPathVariants(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        String path = baseRequest.pathWithoutQuery();
        if (path == null || path.isEmpty()) return;

        String[] segments = path.split("/", -1);

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (!isAllDigits(segment)) continue;

            long numValue;
            try {
                numValue = Long.parseLong(segment);
            } catch (NumberFormatException e) {
                continue;
            }

            // 按优先级构建去重变体集，跳过原值和负数
            LinkedHashSet<String> mutations = new LinkedHashSet<>();
            mutations.add(String.valueOf(numValue + 1));
            if (numValue - 1 >= 0) mutations.add(String.valueOf(numValue - 1));
            mutations.add("1");
            mutations.add("0");
            mutations.add("99999");
            mutations.add(segment + "0");

            int count = 0;
            for (String newValue : mutations) {
                if (count >= MAX_MUTATIONS_PER_PATH_SEGMENT) break;
                if (newValue.equals(segment)) continue;

                segments[i] = newValue;
                String newPath = String.join("/", segments);
                segments[i] = segment; // restore

                HttpRequest mutated;
                try {
                    mutated = baseRequest.withPath(newPath);
                } catch (Exception e) {
                    continue;
                }
                variants.add(new IdorScanVariant("numeric-path", "path[" + i + "]",
                        segment, newValue, mutated));
                count++;
            }
        }
    }
```

- [ ] **Step 4: Run the tests — confirm they all PASS**

```
mvn test -Dtest=IdorPayloadServiceTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full test suite to confirm no regressions**

```
mvn test
```

Expected: `BUILD SUCCESS` with zero failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/core/IdorPayloadService.java src/test/java/core/IdorPayloadServiceTest.java
git commit -m "feat: IDOR扫描添加numeric-path策略，启发式替换URL数字路径段"
```
