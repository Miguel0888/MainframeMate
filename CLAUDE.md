# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MainframeMate is a Java Swing desktop application for mainframe data integration. It converts structured data (e.g., from Excel) into fixed-format text files and supports uploading to Mainframe hosts via FTP/JES. It also provides a code editor with syntax highlighting for Natural/JCL/COBOL, AI-powered assistance, and a plugin system.

## Build Commands

**Build requirements**: Java 8 JDK (mandatory — `tools.jar` must exist at `$JAVA_HOME/../lib/tools.jar`). A branding icon archive `app.zip` must be present in the project root.

```bash
# Full build (shadow JAR + plugins)
./gradlew clean :app:shadowJar

# Build with release mode (no debug info)
./gradlew clean :app:shadowJar -Prelease=true

# Assemble app (also installs plugins to ~/.mainframemate/plugins/)
./gradlew :app:assemble

# Run all tests
./gradlew :app:test

# Run a specific test class
./gradlew :app:test --tests "de.bund.zrb.files.codec.RecordStructureCodecTest"

# Run tests matching a pattern
./gradlew :app:test --tests "*JesFtp*"

# Run with detailed output
./gradlew :app:test --info

# Generate OSS license report (also runs automatically before processResources)
./gradlew generateOssLicenseReport
```

## Version Management

There are two version numbers:
- **App version** (`5.4.0`): set in root `build.gradle` as `group = 'de.bund.zrb'` / `version = '5.4.0'`
- **Build number** (`367`): in `gradle.properties` as `version=367` — increment this for each release

## Module Structure

| Module | Purpose |
|---|---|
| `core` | Plugin API and shared interfaces (`FileService`, `ChatManager`, `PathDialect`, `MenuCommand`) |
| `app` | Main Swing GUI application — entry point is `de.bund.zrb.Main` |
| `toolbar-kit` | Reusable Swing toolbar library with drag-and-drop and config dialog |
| `plugins/excelimport` | Excel-to-fixed-format conversion plugin |
| `plugins/webSearch` | Web search plugin |
| `ndv` | Natural Development Server (NDV) integration |
| `betaview-integration` | BetaView terminal emulation |
| `wiki-integration` | MediaWiki integration via JWBF |
| `dosbox` | Java port of em-dosbox DOS emulator |
| `wd4j` | WebDriver 4 Java bindings |
| `wd4j2cdp` | WebDriver BiDi to Chrome DevTools Protocol adapter |
| `wd4j-mcp-server` | MCP (Model Context Protocol) server for AI tool integration |

## Architecture

### Core Abstraction Layer (`core` module)
- `FileService` — abstract interface for all file system backends
- `FileNode` — tree node representation for file system entries
- `ChatManager` / `ChatHistory` — interface for AI chat providers
- `MainframeMatePlugin` / `MenuCommand` / `ToolManager` — plugin API
- `PathDialect` / `MvsPathDialect` — path handling abstraction per host type

### File Service Implementations (`app` module)
- `VfsLocalFileService` — local filesystem via Apache Commons VFS
- `CommonsNetFtpFileService` — standard FTP (uses Apache Commons Net)
- `JesFtpService` — JES (Job Entry Subsystem) FTP for spool files
- `NdvFileService` — Natural Development Server
- `MvsBrowserController` — MVS dataset browser

### AI / Chat System
- `CloudChatManager` — cloud AI via REST (OkHttp3)
- `LlamaCppChatManager` — local LLM via bundled llama.cpp executable
- `OllamaChatManager` — local LLM via OLLAMA
- `CustomChatManager` — custom provider
- `AttachmentContextBuilder` — builds RAG context for AI queries

### Archive & Search System
- H2 embedded database for document caching/archiving
- Apache Lucene 8.11.x for full-text indexing (Java 8-compatible version)
- `ArchiveService` orchestrates `WebSnapshotPipeline` and `CatalogPipeline`
- `IndexingService` manages Lucene indices for RAG

### JCL Parsing
- ANTLR4 grammar files in `app/src/main/antlr/`
- Generated parser output: `app/build/generated-src/antlr/main/de/bund/zrb/jcl/parser/`
- Parser is regenerated automatically before `compileJava`

### Plugin System
- Plugins are loaded at runtime from `~/.mainframemate/plugins/`
- `runtimeOnly` dependencies in `app/build.gradle` make built-in plugins available during development
- Each plugin implements `MainframeMatePlugin` from the `core` module

### Generated Resources
The build generates several resource sets before compilation:
- `app.zip` → unpacked icon theme via `unpackBrandingIcons` task
- `version.properties` → build metadata via `generateBuildInfo` task
- `oss-licenses.json` → dependency license report via `generateOssLicenseReport` task

## Key Packages

All code lives under `de.bund.zrb.*`:

- `de.bund.zrb` — `Main`, `MainFrame`, top-level UI
- `de.bund.zrb.files` — file services and codec implementations
- `de.bund.zrb.files.impl.ftp` — FTP and JES file service implementations
- `de.bund.zrb.chat` — chat/AI manager implementations
- `de.bund.zrb.archive` — document archive, pipelines, H2 repository
- `de.bund.zrb.ingestion` — document ingestion and Lucene indexing
- `de.bund.zrb.jcl` — JCL parser (ANTLR-generated)
- `de.bund.zrb.mcp` — MCP server tools

## Key Technologies

- **Java 8** (required — hard dependency on `tools.jar`)
- **Swing** for GUI
- **ANTLR 4.9.3** — last Java 8-compatible version, used for JCL grammar
- **Apache Lucene 8.11.3** — last Java 8-compatible version
- **Apache Tika 2.9.1** — document type detection (flexmark excluded due to Java 11+ requirement)
- **H2 2.2.224** — embedded database for archive
- **RSyntaxTextArea 3.3.3** — syntax-highlighted code editor component
- **Apache Commons Net 3.9.0** — FTP connectivity
- **Apache POI 5.2.3** — Excel file handling
- **OkHttp3 4.9.3** — HTTP client for AI REST APIs