# Issue-Backlog: Ueberall Suche

Dieser Backlog fasst konkrete Arbeitspakete fuer die Weiterentwicklung und Absicherung der Ueberall Suche zusammen. Die Eintraege sind so formuliert, dass sie nahezu 1:1 als GitHub-Issues uebernommen werden koennen.

---

## 1. Suchquellen und Indexe inventarisieren und dokumentieren

### Ziel
Alle von der Ueberall Suche verwendeten Datenquellen, Adapter, Indexe und Aktualisierungspfade transparent machen.

### Problem
Aktuell ist nicht ausreichend sichtbar, welche Suchbereiche technisch angebunden sind, welche Indizes genutzt werden und unter welchen Bedingungen einzelne Bereiche Treffer liefern oder leer bleiben.

### Scope
- alle Search-Provider / Suchadapter identifizieren
- zugehoerige Datenquellen und Indizes erfassen
- Refresh-/Reindex-Strategien dokumentieren
- Owner, Fehlerfaelle und Abhaengigkeiten dokumentieren

### Akzeptanzkriterien
- fuer jede Suchquelle existiert eine kurze technische Dokumentation
- fuer jede Suchquelle ist klar, ob sie aktiv, deaktiviert oder nur bedingt verfuegbar ist
- fuer jeden Index ist klar, wie er aufgebaut und aktualisiert wird
- die Ueberall Suche besitzt eine zentrale Uebersicht der angebundenen Bereiche

### Nutzen
Grundlage fuer alle weiteren Verbesserungen, schnellere Fehlersuche, weniger Blackbox-Verhalten.

---

## 2. Indexierungsprozess robust machen

### Ziel
Sicherstellen, dass alle angebundenen Suchquellen konsistent, nachvollziehbar und fehlertolerant indexiert werden.

### Problem
Wenn einzelne Import- oder Indexierungsschritte fehlschlagen, entstehen leicht unvollstaendige Suchergebnisse, ohne dass fuer Nutzer oder Betrieb klar erkennbar ist, warum.

### Scope
- Fehlerbehandlung in allen Indexierungsstrecken vereinheitlichen
- Logging fuer fehlgeschlagene Dokumente verbessern
- Dublettenvermeidung definieren
- Reindexing-Strategie fuer Voll- und Teilneubauten dokumentieren
- Monitoring fuer fehlgeschlagene Indexlaeufe vorbereiten

### Akzeptanzkriterien
- fehlgeschlagene Indexierungen werden sauber geloggt
- problematische Datensaetze koennen nachvollzogen werden
- es gibt eine dokumentierte Reindex-Strategie pro Datenquelle
- doppelte Dokumente werden erkannt oder verhindert

### Nutzen
Mehr Stabilitaet, weniger stille Fehler, hoehere Datenqualitaet im Suchindex.

---

## 3. Suchverhalten ueber alle Bereiche fachlich vereinheitlichen

### Ziel
Ein konsistentes Sucherlebnis ueber alle Datenquellen hinweg schaffen.

### Problem
Mehrere Suchbereiche koennen dieselbe Eingabe heute unterschiedlich interpretieren. Dadurch entstehen inkonsistente Treffer, Sortierungen und Filterverhalten.

### Scope
- gemeinsame Query-Regeln definieren
- Unterschiede zwischen Bereichen sichtbar machen
- Feldgewichtung und Standardsortierung abstimmen
- einheitliches Verhalten fuer leere Suchbegriffe, Sonderzeichen und Mehrwortsuchen festlegen
- gemeinsame Regeln fuer Filter und Bereichsauswahl definieren

### Akzeptanzkriterien
- gleichartige Suchanfragen verhalten sich bereichsuebergreifend nachvollziehbar
- Standardsortierung ist dokumentiert und begruendet
- Sonderfaelle wie leerer Suchbegriff, nur Leerzeichen oder nur Sonderzeichen sind abgefangen
- Bereichsfilter verhalten sich in allen Suchquellen konsistent

### Nutzen
Vorhersehbares Suchverhalten, weniger Support-Rueckfragen, bessere fachliche Qualitaet.

---

## 4. Analyzer, Tokenisierung und deutsche Suche verbessern

### Ziel
Die Suchqualitaet fuer deutsche Begriffe, Wortformen, Tippfehler und Synonyme verbessern.

### Problem
Ohne abgestimmte Analyzer, Stopwords, Stemming und Synonymlogik werden Treffer unvollstaendig oder unerwartet priorisiert.

### Scope
- aktuelle Analyzer-Konfigurationen je Suchquelle pruefen
- deutsche Sprache gezielt unterstuetzen
- Stopword-Listen fachlich validieren
- Synonymlisten einfuehren oder verbessern
- Verhalten bei Tippfehlern und Komposita analysieren

### Akzeptanzkriterien
- relevante deutsche Suchbegriffe liefern reproduzierbar bessere Treffer
- Synonyme und Wortvarianten sind fuer definierte Begriffe abgedeckt
- Suchtests fuer typische deutsche Fachbegriffe existieren
- Analyzer-Entscheidungen sind dokumentiert

### Nutzen
Hoehere Trefferquote, bessere Relevanz, bessere Nutzerakzeptanz.

---

## 5. Nicht funktionierende oder bedingt funktionierende Suchbereiche sichtbar machen

### Ziel
Verhindern, dass defekte oder nur teilweise aktive Suchbereiche stillschweigend als voll funktionsfaehig erscheinen.

### Problem
Fuer Nutzer ist oft nicht erkennbar, ob es wirklich keine Treffer gibt oder ob ein Bereich gerade nicht indiziert, nicht erreichbar oder fachlich deaktiviert ist.

### Scope
- pro Suchbereich Statusmodell einfuehren
- technische Teilfehler in der Aggregation sichtbar machen
- UI-Hinweise fuer eingeschraenkte Suchbereiche ergaenzen
- Fallback-Verhalten definieren, wenn einzelne Quellen ausfallen

### Akzeptanzkriterien
- Nutzer sehen, wenn ein Bereich temporaer nicht verfuegbar ist
- Teilfehler fuehren nicht mehr zu irrefuehrenden 0-Treffer-Zustaenden
- Aggregation kann mit Teilausfaellen kontrolliert umgehen
- Suchbereiche koennen aktiv als deaktiviert/experimentell markiert werden

### Nutzen
Bessere Transparenz, weniger falsche Interpretation von Suchergebnissen, besseres Fehlerverhalten.

---

## 6. Relevanzranking und Ergebnisdarstellung verbessern

### Ziel
Treffer besser priorisieren und Ergebnisse fuer Nutzer leichter bewertbar machen.

### Problem
Selbst wenn Treffer gefunden werden, ist der fachlich wichtigste Treffer nicht zwingend vorne. Ausserdem fehlt oft Kontext, warum ein Treffer relevant ist.

### Scope
- Boosting fuer Titel, Metadaten und exakte Treffer pruefen
- Highlighting verbessern
- Ergebnis-Snippets fachlich optimieren
- Sortierlogik fuer bereichsuebergreifende Treffer absichern
- Dubletten oder nahezu gleiche Treffer behandeln

### Akzeptanzkriterien
- exakte und hoch relevante Treffer werden sichtbar bevorzugt
- Snippets zeigen den Trefferkontext nachvollziehbar an
- Highlighting produziert keine kaputten oder irrefuehrenden Ausgaben
- doppelte oder nahezu identische Treffer werden reduziert oder gekennzeichnet

### Nutzen
Schnellere Informationsfindung, bessere UX, weniger Frust bei grossen Treffermengen.

---

## 7. UX fuer Keine-Treffer-, Fehler- und Grenzfaelle verbessern

### Ziel
Die Suche soll auch in unklaren oder fehlerhaften Situationen nutzerfreundlich reagieren.

### Problem
Leere Ergebnisseiten, unklare Fehlermeldungen oder fehlende Hinweise auf alternative Suchen verschlechtern die Nutzbarkeit deutlich.

### Scope
- klare Zustandsbilder fuer keine Treffer, Teilfehler und technische Fehler definieren
- alternative Suchhinweise einbauen
- Suchbegriffe, Filter und aktive Bereiche in der UI klarer darstellen
- Reset-/Clear-Verhalten verbessern

### Akzeptanzkriterien
- es gibt getrennte UX-Zustaende fuer keine Treffer, Teilfehler und technische Fehler
- Nutzer erhalten konkrete Hinweise fuer alternative Suchen
- aktive Filter und Suchbereiche sind klar erkennbar
- Suche laesst sich schnell auf einen neutralen Zustand zuruecksetzen

### Nutzen
Mehr Nutzerfreundlichkeit, bessere Fehlertoleranz, weniger Abbrueche.

---

## 8. Automatisierte Suchtests fuer alle relevanten Bereiche einfuehren

### Ziel
Die Ueberall Suche gegen Regressionen absichern.

### Problem
Aenderungen an Adaptern, Datenquellen oder Suchlogik koennen bestehende Suchpfade unbemerkt verschlechtern oder komplett brechen.

### Scope
- Testfaelle fuer Kernsuchpfade definieren
- Integrationstests fuer relevante Suchbereiche einfuehren
- Regressionstests fuer bekannte Problembegriffe aufbauen
- Testdaten fuer Suchqualitaet pflegen

### Akzeptanzkriterien
- fuer jede wichtige Suchquelle existiert mindestens ein Integrationstest
- definierte Referenzanfragen werden automatisiert geprueft
- bekannte Problemfaelle sind als Regressionstest hinterlegt
- Build oder CI signalisiert kaputte Suchpfade fruehzeitig

### Nutzen
Mehr Aenderungssicherheit, fruehes Erkennen defekter Suchbereiche, bessere Wartbarkeit.

---

## 9. Observability fuer Suche und Indexierung aufbauen

### Ziel
Laufzeitverhalten, Fehler und Qualitaetsprobleme der Suche messbar machen.

### Problem
Ohne passende Metriken und Logs bleiben schlechte Trefferqualitaet, langsame Suchbereiche oder defekte Indexlaeufe oft lange unbemerkt.

### Scope
- strukturierte Logs fuer Suchanfragen und Suchfehler definieren
- Metriken fuer Latenz, Trefferanzahl und Fehlerquoten einfuehren
- Metriken fuer Indexierungslaeufe bereitstellen
- Basis-Dashboard fuer Suche und Indexierung anlegen

### Akzeptanzkriterien
- Suchlatenz und Fehlerquote sind messbar
- Indexierungsfehler koennen pro Quelle ausgewertet werden
- es gibt mindestens ein Dashboard fuer den operativen Blick auf die Suche
- kritische Fehler koennen spaeter alarmiert werden

### Nutzen
Bessere Betriebsfaehigkeit, fundierte Optimierung, schnellere Fehleranalyse.

---

## 10. Performance der Ueberall Suche gezielt analysieren und optimieren

### Ziel
Antwortzeiten und Skalierbarkeit der Suche verbessern.

### Problem
Eine bereichsuebergreifende Suche wird schnell teuer, wenn mehrere Quellen, Indizes und Ergebnislisten gleichzeitig abgefragt und zusammengefuehrt werden.

### Scope
- Hotspots in Query-Ausfuehrung und Aggregation messen
- langsame Suchbereiche identifizieren
- Timeouts, Parallelisierung und Paging pruefen
- Caching-Optionen bewerten
- tiefe Pagination und Ergebnisaggregation optimieren

### Akzeptanzkriterien
- die Hauptlatenztreiber sind bekannt und dokumentiert
- konkrete Optimierungsmassnahmen mit Messwerten belegt
- unnoetige Vollabfragen oder teure Folgeabfragen sind reduziert
- Performance-Baseline fuer kuenftige Vergleiche ist definiert

### Nutzen
Schnellere Suche, bessere Skalierbarkeit, stabileres Nutzererlebnis.

---

## 11. Berechtigungen und Sichtbarkeit der Suchergebnisse absichern

### Ziel
Sicherstellen, dass Nutzer nur Treffer sehen, fuer die sie fachlich und technisch berechtigt sind.

### Problem
Gerade bei aggregierten Suchsystemen ist das Risiko hoch, dass Berechtigungslogik zwischen Quelle, Index und Ergebnisanzeige auseinanderlaeuft.

### Scope
- Berechtigungskonzept fuer alle Suchquellen pruefen
- klaeren, ob Security beim Indexieren oder beim Ausliefern erzwungen wird
- Sichtbarkeitsluecken und potenzielle Datenlecks identifizieren
- sensible Felder und Metadaten bewerten

### Akzeptanzkriterien
- fuer jede Suchquelle ist das Berechtigungsmodell dokumentiert
- keine Treffer aus nicht berechtigten Quellen werden ausgeliefert
- sensible Metadaten sind bewertet und abgesichert
- kritische Sicherheitsrisiken sind als separate Folgetickets dokumentiert

### Nutzen
Mehr Sicherheit, weniger Compliance-Risiko, belastbare fachliche Freigabe.

---

## 12. Architektur der Ueberall Suche konsolidieren

### Ziel
Die technische Struktur der Suche langfristig wartbarer und erweiterbarer machen.

### Problem
Mit jeder neuen Datenquelle steigt sonst die Gefahr von Sonderlogik, harter Kopplung und inkonsistentem Verhalten in der Aggregation.

### Scope
- Search-Provider-Schnittstellen pruefen und vereinheitlichen
- klare Trennung zwischen UI, Use Case, Provider und Infrastruktur staerken
- Erweiterungspunkte fuer neue Suchquellen definieren
- technische Schulden in der Suchorchestrierung abbauen

### Akzeptanzkriterien
- neue Suchquellen koennen ueber eine klar definierte Schnittstelle angebunden werden
- provider-spezifische Sonderlogik ist lokal gekapselt
- Suchorchestrierung ist nachvollziehbar und testbar
- Architekturentscheidungen sind dokumentiert

### Nutzen
Weniger Kopplung, bessere Testbarkeit, leichteres Onboarding neuer Suchquellen.

---

## Priorisierungsvorschlag

### Sofort starten
1. Suchquellen und Indexe inventarisieren und dokumentieren
2. Nicht funktionierende oder bedingt funktionierende Suchbereiche sichtbar machen
3. Indexierungsprozess robust machen
4. Automatisierte Suchtests fuer alle relevanten Bereiche einfuehren

### Danach
5. Suchverhalten ueber alle Bereiche fachlich vereinheitlichen
6. Analyzer, Tokenisierung und deutsche Suche verbessern
7. Relevanzranking und Ergebnisdarstellung verbessern
8. Observability fuer Suche und Indexierung aufbauen

### Anschliessend
9. Performance der Ueberall Suche gezielt analysieren und optimieren
10. Berechtigungen und Sichtbarkeit der Suchergebnisse absichern
11. UX fuer Keine-Treffer-, Fehler- und Grenzfaelle verbessern
12. Architektur der Ueberall Suche konsolidieren
