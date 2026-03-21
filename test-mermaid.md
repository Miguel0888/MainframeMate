# Mermaid Diagrams Test

Dies ist ein Test-Dokument mit verschiedenen Mermaid-Diagrammen.

## Flowchart

Ein einfaches Flowchart:

```mermaid
graph TD
    A[Start] --> B{Entscheidung?}
    B -->|Ja| C[Aktion 1]
    B -->|Nein| D[Aktion 2]
    C --> E[Ende]
    D --> E
```

## Sequenzdiagramm

Kommunikation zwischen Client und Server:

```mermaid
sequenceDiagram
    participant Client
    participant Server
    participant DB as Datenbank
    Client->>Server: GET /api/data
    Server->>DB: SELECT * FROM data
    DB-->>Server: Ergebnis
    Server-->>Client: 200 OK (JSON)
```

## Normaler Code-Block

Zum Vergleich — ein normaler Code-Block (kein Mermaid):

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello!");
    }
}
```

## Klassendiagramm

```mermaid
classDiagram
    class MermaidRenderer {
        +renderToSvg(diagramCode) String
        +isAvailable() boolean
        +getInstance() MermaidRenderer
    }
    class GraalJsExecutor {
        +execute(script) JsExecutionResult
    }
    class JsExecutionResult {
        +isSuccessful() boolean
        +getOutput() String
        +getErrorMessage() String
    }
    MermaidRenderer --> GraalJsExecutor
    GraalJsExecutor --> JsExecutionResult
```

## Pie Chart

```mermaid
pie title Projektstruktur
    "app" : 60
    "core" : 10
    "mermaid-renderer" : 5
    "plugins" : 10
    "sonstige" : 15
```

## Text nach den Diagrammen

Alle Diagramme oben sollten als **gerenderte SVG-Bilder** statt als Code-Blöcke angezeigt werden.

