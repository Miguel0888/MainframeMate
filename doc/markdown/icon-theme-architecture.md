# Icon-Theme / Branding-Architektur

## Übersicht

MainframeMate ersetzt alle Java-Standard-Icons (Coffee Cup) durch eigene Brand-Icons.
Die Icons werden als **ZIP-Datei** (`app.zip`) im Projekt-Root verwaltet.

## ZIP-Struktur

```
app.zip
├── app/                               ← App-/Fenster-Icons (MUSS)
│   ├── mainframemate_hub_transparent_16.png
│   ├── mainframemate_hub_transparent_24.png
│   ├── mainframemate_hub_transparent_32.png
│   ├── mainframemate_hub_transparent_48.png
│   ├── mainframemate_hub_transparent_64.png
│   ├── mainframemate_hub_transparent_128.png
│   ├── mainframemate_hub_transparent_256.png
│   ├── mainframemate_hub_transparent_512.png   (optional)
│   ├── mainframemate_hub_transparent_1024.png  (optional)
│   ├── mainframemate_hub_bg_16.png             (optional, Variante "bg")
│   ├── mainframemate_hub_bg_24.png             (optional)
│   ├── ...
│   └── mainframemate_hub.ico                   (optional, Windows)
│
└── ui/                                ← UI-Icons (OPTIONAL → FULL-Modus)
    ├── optionpane_information_32.png
    ├── optionpane_warning_32.png
    ├── optionpane_error_32.png
    ├── optionpane_question_32.png
    ├── filechooser_directory_16.png
    ├── filechooser_file_16.png
    ├── filechooser_computer_16.png
    ├── filechooser_harddrive_16.png
    ├── filechooser_floppy_16.png
    ├── tree_open_16.png
    ├── tree_closed_16.png
    ├── tree_leaf_16.png
    ├── internalframe_close_16.png
    ├── internalframe_minimize_16.png
    ├── internalframe_maximize_16.png
    └── internalframe_iconify_16.png
```

## Modi

| Modus    | Voraussetzung           | Wirkung                                      |
|----------|------------------------|----------------------------------------------|
| MINIMAL  | nur `app/` vorhanden    | Fenster-/Taskbar-Icons werden ersetzt         |
| FULL     | `app/` + `ui/` vorhanden| Zusätzlich alle Swing UIManager-Defaults      |

Der Modus wird **automatisch erkannt**: Wenn `/ui/optionpane_information_32.png` auf dem Classpath existiert, wird FULL aktiviert.

## Build-Integration

### Gradle-Task: `unpackBrandingIcons`

- Entpackt `app.zip` nach `build/generated-resources/icon-theme/`
- Wird als `sourceSets.main.resources.srcDir` registriert
- `processResources` hängt davon ab → automatische Ausführung
- **Validierung**: Prüft, ob alle Pflichtdateien vorhanden sind:
  - MINIMAL: `app/mainframemate_hub_transparent_{16,24,32,48,64,128,256}.png`
  - FULL (wenn `ui/` existiert): zusätzlich die vier OptionPane-Icons

### Build-Fehler bei fehlenden Icons

```
FAILURE: Build failed with an exception.
Icon Theme Validation FAILED – missing required files:
  .../app/mainframemate_hub_transparent_32.png
```

## Runtime-Architektur

### Klasse: `de.bund.zrb.ui.branding.IconThemeInstaller`

#### Öffentliche API

| Methode                              | Beschreibung                                          |
|--------------------------------------|------------------------------------------------------|
| `install()`                          | Auto-Detect Modus, Variante TRANSPARENT               |
| `install(Mode, Variant)`             | Expliziter Modus und Variante                         |
| `getAppIcons() → List<Image>`        | Multi-Size Icon-Liste für `setIconImages()`           |
| `getAppIcon(int size) → Image`       | Einzelnes Icon in nächstgelegener Größe               |

#### Initialisierung (in `Main.java`)

```java
// MUSS vor SwingUtilities.invokeLater() stehen!
IconThemeInstaller.install();
```

#### Caching

Alle geladenen Images werden in einer `ConcurrentHashMap<String, Image>` gecacht (Key = Classpath-Ressourcenpfad). Kein doppeltes I/O oder Image-Decoding.

#### Auflösungs-Strategie

- Die Icon-Liste wird **absteigend** sortiert (größte Auflösung zuerst: 1024 → 16px).
- Swing/AWT wählt aus `setIconImages(List)` die passende Größe für Titelleiste, Taskbar etc.
- Bei `getAppIcon(size)` wird bei Gleichstand immer die **höhere** Auflösung bevorzugt.
- Für das Taskbar-Icon wird immer die **maximal verfügbare** Auflösung verwendet.

### Unterstützte UIManager-Keys (FULL-Modus)

| UIManager Key                    | Ressource                           |
|---------------------------------|--------------------------------------|
| `OptionPane.informationIcon`    | `/ui/optionpane_information_32.png`  |
| `OptionPane.warningIcon`        | `/ui/optionpane_warning_32.png`      |
| `OptionPane.errorIcon`          | `/ui/optionpane_error_32.png`        |
| `OptionPane.questionIcon`       | `/ui/optionpane_question_32.png`     |
| `FileView.directoryIcon`       | `/ui/filechooser_directory_16.png`   |
| `FileView.fileIcon`            | `/ui/filechooser_file_16.png`        |
| `FileView.computerIcon`        | `/ui/filechooser_computer_16.png`    |
| `FileView.hardDriveIcon`       | `/ui/filechooser_harddrive_16.png`   |
| `FileView.floppyDriveIcon`     | `/ui/filechooser_floppy_16.png`      |
| `Tree.openIcon`                | `/ui/tree_open_16.png`               |
| `Tree.closedIcon`              | `/ui/tree_closed_16.png`             |
| `Tree.leafIcon`                | `/ui/tree_leaf_16.png`               |
| `InternalFrame.closeIcon`      | `/ui/internalframe_close_16.png`     |
| `InternalFrame.minimizeIcon`   | `/ui/internalframe_minimize_16.png`  |
| `InternalFrame.maximizeIcon`   | `/ui/internalframe_maximize_16.png`  |
| `InternalFrame.iconifyIcon`    | `/ui/internalframe_iconify_16.png`   |

### Fallback-Strategie

- Fehlende optionale Icons → Default bleibt bestehen (kein Crash)
- Fehlende Pflicht-Icons zur Buildzeit → Build schlägt fehl
- Runtime-Fehler (z. B. korrupte Datei) → Logging + Fallback auf Default

## Varianten

| Variante      | Suffix         | Beschreibung                |
|---------------|---------------|-----------------------------|
| TRANSPARENT   | `transparent`  | Transparenter Hintergrund   |
| BG            | `bg`           | Farbiger Hintergrund        |

Standard: `TRANSPARENT`

