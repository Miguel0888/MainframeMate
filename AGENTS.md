# AGENTS.md

## Project

MainframeMate — Java 8 Swing desktop app for mainframe data integration (Excel→fixed-format, FTP/JES upload, code editor with Natural/JCL/COBOL syntax highlighting, AI chat, plugin system).

## Build & Test

```bash
# Build fat JAR (requires Java 8 JDK + app.zip in project root)
./gradlew clean :app:shadowJar

# Run tests (JUnit 5)
./gradlew :app:test
./gradlew :app:test --tests "de.bund.zrb.files.codec.RecordStructureCodecTest"
./gradlew :app:test --tests "*JesFtp*"
```

**Prerequisite**: `JAVA_HOME` must point to a Java 8 JDK — the build hard-fails if `tools.jar` is missing. ANTLR grammar (`app/src/main/antlr/*.g4`) is auto-regenerated before `compileJava`. Icon theme (`app.zip`), `version.properties`, and `oss-licenses.json` are generated resources — don't edit them manually.

## Version Numbers

- **App version** (e.g. `5.4.0`): root `build.gradle` → `version = '5.4.0'`
- **Build number** (e.g. `376`): `gradle.properties` → `version=376` — increment per release

## Module Layout

| Module | Role |
|---|---|
| `core` | Plugin API + shared interfaces — **consumed by all other modules** |
| `app` | Main GUI app (entry: `de.bund.zrb.Main`) |
| `toolbar-kit` | Reusable Swing toolbar library |
| `plugins/excelimport`, `plugins/webSearch` | Runtime plugins (ServiceLoader) |
| `ndv`, `betaview-integration`, `wiki-integration`, `dosbox` | Integration modules |
| `wd4j`, `wd4j2cdp`, `wd4j-mcp-server` | WebDriver / MCP server |

## Critical Package Convention

The `core` module has **two package roots** — this is intentional, not a bug:
- `de.zrb.bund.api` / `de.zrb.bund.newApi` — plugin API interfaces (`MainframeMatePlugin`, `ChatManager`, `McpTool`, `MenuCommand`)
- `de.bund.zrb.files.*` — `FileService` interface and models (`FileNode`, `FilePayload`, `PathDialect`)

The `app` module uses `de.bund.zrb.*` consistently. When importing core APIs, expect imports from **both** `de.zrb.bund` and `de.bund.zrb.files`.

## Key Abstractions

- **`FileService`** (`core: de.bund.zrb.files.api`) — unified interface for local/FTP/NDV file ops. Implementations: `VfsLocalFileService`, `CommonsNetFtpFileService`, `JesFtpService`, `NdvFileService`. Created via `FileServiceFactory`.
- **`ChatManager`** (`core: de.zrb.bund.api`) — AI provider interface with streaming. Implementations: `CloudChatManager` (OpenAI/Claude REST), `OllamaChatManager`, `LlamaCppChatManager`.
- **`McpTool`** (`core: de.zrb.bund.newApi.mcp`) — MCP tool interface (`getSpec()` + `execute(JsonObject, String)`). Built-in tools in `app/…/mcp/`, plugins contribute tools via `getTools()`.
- **`MainframeMatePlugin`** (`core: de.zrb.bund.api`) — plugin entry point. Discovered via `java.util.ServiceLoader`. Must have `META-INF/services/de.zrb.bund.api.MainframeMatePlugin` file.

## Plugin Development Pattern

1. Create module under `plugins/`, depend on `:core`
2. Implement `MainframeMatePlugin` — provide `getPluginName()`, `initialize(MainframeContext)`, `getCommands()`, `getTools()`
3. Register in `src/main/resources/META-INF/services/de.zrb.bund.api.MainframeMatePlugin`
4. Add `runtimeOnly project(':plugins:yourPlugin')` to `app/build.gradle` for dev-time availability
5. Plugin JARs are installed to `~/.mainframemate/plugins/` via `installPlugin` task

Reference: `plugins/excelimport/` is the canonical example.

## Tech Constraints

- **Java 8 only** — no lambdas with `var`, no `List.of()`, no `HttpClient`. Use `Arrays.asList()`, `Collections.singletonList()`.
- **ANTLR 4.9.3** (last Java 8 version) — grammars in `app/src/main/antlr/`, output to `app/build/generated-src/antlr/main/de/bund/zrb/jcl/parser/`
- **Lucene 8.11.3** (last Java 8 version), **Tika 2.9.1** (flexmark excluded — requires Java 11+)
- GUI is **Swing** with `RSyntaxTextArea 3.3.3` for editors
- HTTP via **OkHttp3 4.9.3**, JSON via **Gson 2.10.1**

## Testing

Tests use **JUnit 5** (`org.junit.jupiter`). Test sources mirror main in `app/src/test/java/de/bund/zrb/`. Tests are organized by feature package (e.g. `files/`, `chat/`, `mcp/`, `workflow/`).

