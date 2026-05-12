# Project Cockpit Feature Workbench Design

## Summary

Nach dem Wizard braucht Product-Spec-Agent ein Projekt-Cockpit, in dem Product Owner weiter an Features arbeiten koennen. Der Screen zeigt abgeschlossene Features, laufende oder neue Features, letzte Testlaeufe, offene Punkte aus Done-Features und eine Aktion, um fuer ein ausgewaehltes Feature neue Designs zu erzeugen.

## Direction

Die gewaehlte Struktur ist eine Feature Workbench mit Review-Spalte. Links steht eine kompakte Feature-Liste mit Status, Testampel und Zaehlern. Rechts zeigt der aktive Feature-Kontext Done-Review, Test-Evidence, offene Punkte und Design-Aktionen.

Der Prototyp wird als neue Frontend-Route mit realistischen Mock-Daten umgesetzt. Er nutzt bestehende UI-Konventionen und bleibt bewusst noch ohne vollstaendige API-Anbindung.

## In Scope

- Feature hinzufuegen.
- Feature auswaehlen.
- Status, Tests, offene Punkte und Design-Stand sehen.
- Done-Review fuer das ausgewaehlte Feature pruefen.
- Offene Punkte im Prototyp lokal abhaken.
- Design-Erzeugung fuer das ausgewaehlte Feature als Working State simulieren.

## Out of Scope

- Vollstaendige Feature-Detailform.
- Bearbeitung aller Akzeptanzkriterien und Scope-Felder.
- Persistenz der Cockpit-Interaktionen.
- Echte Testausfuehrung.
- Echte Design-Agent-Anbindung.

## Layout

- Topbar: Projektname, kurzer Status, globale Primaeraktion `Feature erfassen`.
- Linke Spalte: Feature-Liste mit Done, In Arbeit, Blockiert und Geplant.
- Hauptbereich: aktives Feature mit Review, Testlaeufen und offenen Punkten.
- Rechte Review-Spalte: Design-Erzeugung, Handoff-Reife und naechste konkrete Aktion.

Die Oberflaeche darf dicht sein, aber nicht ueberladen. Keine Kanban-Wand, keine Formularwand, keine Modals als Standardinteraktion.

## States

- Default: Mehrere Features, eines aktiv.
- Empty: Erstes Feature erfassen.
- Feature mit offenen Punkten: kompakte Warnung und klare Review-Items.
- Done Feature: Test-Evidence und Abnahmezustand sichtbar.
- Design Working: Button und Panel zeigen aktive Generierung.
- Error: Kurze kontextuelle Meldung, keine globale Fehlerwand.

## Accessibility

Der Screen folgt WCAG AA, nutzt sichtbare Fokuszustaende, konkrete Button-Labels und respektiert `prefers-reduced-motion`.
