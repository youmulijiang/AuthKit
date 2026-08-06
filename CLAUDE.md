# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AuthKit is a Burp Suite extension for authorization testing (越权检测). It intercepts HTTP traffic, replays requests with different auth contexts (unauthorized, different user roles), and compares responses to detect broken access control (BOLA, horizontal/vertical privilege escalation). Built on the Burp Montoya API.

## Build & Test

```bash
mvn clean package          # Build fat JAR (includes shaded dependencies)
mvn test                   # Run all tests (JUnit 5 + Mockito)
mvn test -Dtest=ClassName  # Run a single test class
mvn test -Dtest=ClassName#methodName  # Run a single test method
```

Output JAR: `target/AuthKit-1.0-SNAPSHOT.jar` — load via Burp Extensions > Add.

CI builds two JARs per release tag (`v*`): Java 17 and Java 21.

## Language & Conventions

- Java 17 (switch expressions, records, sealed classes are fine)
- No Lombok, no annotation processing — plain Java POJOs
- All code is in the default package hierarchy under `src/main/java/` (no top-level package prefix like `com.example`)
- Comments and commit messages are in Chinese
- i18n: all user-facing strings go through `I18n.getInstance().text(area, key)` with `.properties` files under `src/main/resources/i18n/{en,zh}/`. Language auto-detected from system timezone/locale.

## Architecture

### Entry Point & Wiring

`AuthKit.java` implements `BurpExtension` and acts as the composition root — it wires all services, models, and UI panels together in `initialize()`. There is no DI framework; all dependencies are manually constructed and connected via callbacks/listeners.

### Core Flow

1. **HttpRequestHandler** — Montoya `HttpHandler` that intercepts responses, applies `ConfigModel` filters (domain, method, path, status code, extension, tool scope), and forwards matching requests to a callback
2. **AuthController** — Central coordinator. Receives captured requests, deduplicates by method+URL, builds `CompareSampleModel` with Original/Unauthorized/per-user replayed responses
3. **RequestReplayService** — Replays requests: strips auth headers for Unauthorized, runs through `ProcessorChain` for user-role replays
4. **ProcessorChain** — Chain-of-responsibility over `RequestProcessor` implementations:
   - `HeaderReplaceProcessor` — replaces/adds auth headers per user config
   - `ParamReplaceProcessor` — replaces URL/body parameters per user config
5. **RankService** — Weighted scoring (StatusCode 30%, Length 30%, Hash 25%, Attributes 15%) comparing each replayed response against Original. Score 0-100; higher = more likely authorization bypass

### Models

- `ConfigModel` — Plugin-wide settings (enabled, domain/method/path/status filters, tool scope, auth headers, extension blacklist)
- `AuthUserModel` — Per-user auth config (headers to inject, params to replace)
- `CompareSampleModel` — One request's full comparison: maps auth-object names ("Original", "Unauthorized", user names) to `MessageDataModel`
- `MessageDataModel` — Single request-response pair with metadata (status, length, hash, attributeCount, rank, contentType)

### UI Layer (Swing)

`MainPanel` is the Burp Suite tab root, split left/right:
- **Left**: `ToolbarPanel` (filter controls) + `DataTablePanel` (results grid) + `MetadataTablePanel` (detail pivot)
- **Right tabs**: `ComparePanel` (source/target message viewers + diff), `ConfigurationPanel`, `UserPanel`, `JwtPanel`

User add/remove/rename in `UserPanel` propagates to DataTable columns, MetadataTable rows, and ComparePanel tabs via event callbacks in `MainPanel.bindEvents()`.

### Additional Features

- **FakeIpService** — Generates random IP headers (X-Forwarded-For, etc.) for Intruder payloads and request injection
- **core.scan.bypass403** — 403 bypass payload generation and scan execution; results shown in `Bypass403ScanDialog`
- **core.scan.idor** — IDOR payload generation and scan execution; results shown in `IdorScanDialog`
- **core.scan.jwt** — JWT active-scan payload generation and execution; UI in `JwtEditorTab` / `JwtPanel` / `JwtScanDialog`
- **AuthContextMenuProvider** — Right-click menu: "Send to AuthKit", "Extract Auth to User", "Fake IP" actions, "403 Bypass Scan"
- **AuthResultExportService** — Export results to CSV/HTML

### Threading

- `executor` (3 threads) — request replay and context menu processing
- `diffExecutor` (1 daemon thread) — debounced diff computation (180ms debounce)
- All UI updates go through `SwingUtilities.invokeLater()`

### Utilities

- `ApiUtils` — Singleton accessor for Montoya API instance
- `LogUtils` — Logging wrapper around Montoya logging API
- `I18n` — Singleton i18n with runtime language switching and listener support
