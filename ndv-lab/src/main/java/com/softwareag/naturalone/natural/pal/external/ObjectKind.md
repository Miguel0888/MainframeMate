# ObjectKind
## Signatur
`java
public enum ObjectKind
`
## Beschreibung
Aufzaehlung der Natural-Objektarten, die in einer Bibliothek vorkommen koennen.
## Werte
| Wert | Bedeutung |
|---|---|
| PROGRAM | Natural-Programm |
| SUBPROGRAM | Natural-Subprogramm |
| SUBROUTINE | Natural-Subroutine |
| HELPROUTINE | Hilfsroutine |
| MAP | Eingabeformat / Map |
| GDA | Globaler Datenbereich (Global Data Area) |
| LDA | Lokaler Datenbereich (Local Data Area) |
| PDA | Parameter-Datenbereich (Parameter Data Area) |
| DDM | Datendefinitionsmodul |
| COPYCODE | Kopierbaustein |
| TEXT | Textmodul |
| CLASS | Natural-Klasse |
| ADAPTER | Adapter-Objekt |
| RESOURCE | Ressourcen-Objekt |
| DIALOG | Dialog-Objekt |
| FUNCTION | Funktionsobjekt |
| UNKNOWN | Unbekannter Objekttyp |
## Verhalten
- Die Werte-Reihenfolge bestimmt das Ordinal (PROGRAM = 0 ... UNKNOWN = 16).
- alueOf(String) wirft IllegalArgumentException bei unbekannten Namen.
- alueOf(null) wirft NullPointerException.
- alues() gibt bei jedem Aufruf ein neues Array zurueck.
