# toolbar-kit

Reusable Swing toolbar with:
- configurable buttons (icon text + colors)
- left/right alignment
- drag & drop reordering
- configuration dialog
- optional shortcut persistence + installer

## Minimal usage

```java
ToolbarCommandRegistry registry = new InMemoryToolbarCommandRegistry();
// register commands ...

Path configFile = Paths.get(System.getProperty("user.home"), ".myapp", "toolbar.json");
ToolbarConfigRepository repo = new JsonToolbarConfigRepository(configFile);

JToolBar toolbar = new ConfigurableCommandToolbar(registry, repo);
frame.add(toolbar, BorderLayout.NORTH);
```
