# Factory-Klassen Uebersicht (pal.external)
Alle Factory-Klassen im Package pal.external folgen dem gleichen Muster:
## Allgemeines Muster
`java
public class PalTypeXxxFactory {
    public static IPalTypeXxx createInstance() { ... }
}
`
## Vorhandene Factories
| Factory | Erzeugt | Beschreibung |
|---|---|---|
| PalTypeLibIdFactory | IPalTypeLibId | Bibliotheks-Identifikator |
| PalTypeSystemFileFactory | IPalTypeSystemFile | System-Datei-Schluessel |
| PalTypeDbgSpyFactory | IPalTypeDbgSpy | Debug-Spy-Punkt |
| PalTypeDbgStackFrameFactory | IPalTypeDbgStackFrame | Debug-Stack-Rahmen |
| PalTypeDbgSytFactory | IPalTypeDbgSyt | Debug-Symboltabelle |
| PalTypeDbgVarContainerFactory | IPalTypeDbgVarContainer | Debug-Variablen-Container |
| PalTypeDbgVarDescFactory | IPalTypeDbgVarDesc | Debug-Variablen-Beschreibung |
| PalTypeDbgVarValueFactory | IPalTypeDbgVarValue | Debug-Variablen-Wert |
| PalTypeSQLAuthentificationFactory | IPalTypeSQLAuthentification | SQL-Authentifizierung |
| PalIndicesFactory | IPalIndices | Index-Resolver |
## Verhalten
- Jede Factory hat mindestens eine statische createInstance()-Methode.
- Manche Factories haben ueberladene Varianten mit Parametern (z.B. PalTypeLibIdFactory.createInstance(String name, String dbid, String fnr)).
- Die zurueckgegebenen Objekte sind Instanzen der internen PalTypeXxx-Klassen, die das jeweilige Interface implementieren.
- Die Factories selbst haben einen privaten Konstruktor (nicht instanziierbar).
