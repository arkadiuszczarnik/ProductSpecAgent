# Feature 50: Project Cockpit Feature Workbench

## Problem

Nach Abschluss des Wizards gibt es keinen fokussierten Arbeitsbereich fuer Product Owner, um den Projektstand weiterzufuehren. Die vorhandenen Informationen liegen verteilt in Wizard, Tasks, Checks, Living Sync, Design Workbench und Handoff. Dadurch fehlt ein Cockpit, das beantwortet:

- Welche Features sind done?
- Welche Tests liefen zuletzt?
- Welche offenen Punkte bleiben aus bereits abgeschlossenen Features?
- Welche weiteren Features sollen erfasst werden?
- Wo kann fuer ein Feature ein neues Design erfasst werden?

## Ziel

Ein Projekt-Cockpit als Feature Workbench bereitstellen. Der Screen soll nach dem Wizard als operative Arbeitsflaeche dienen: Feature-Landschaft sehen, Feature auswaehlen, Status und Evidence pruefen, offene Punkte bearbeiten, neue Features erfassen und Designs zum aktiven Feature erfassen.

## UX-Entscheidung

Die gewaehlte Struktur ist eine **Feature Workbench mit Review-Spalte**:

- Links: kompakte Feature-Liste mit Status, Testampel, offenen Punkten und Design-Anzahl.
- Hauptbereich: aktives Feature mit Feature-Graph, Done-Review, Handoff-Reife und Test/Evidence.
- Header: primaere Aktionen `Design erfassen` und `Feature erfassen`.
- Kein Kanban, keine Formularwand, keine Modal-first-Interaktion.

Der Hauptscreen zeigt zusaetzlich einen Rete.js Feature-Graph als Uebersicht der Features. Der aktive Graph-Node ist farblich markiert und per Doppelklick kann ein Feature ausgewaehlt werden.

## Kontext7-Erkenntnisse

- **Next.js App Router:** Dynamische Routen nutzen in aktuellen Versionen `params: Promise<{ id: string }>` und werden in async Pages per `await params` entpackt. Das passt zur Route `app/projects/[id]/cockpit/page.tsx`.
- **React:** Lokale Prototyp-Interaktionen wie Feature-Erfassung, Review-Item-Toggles und Design-Zaehler gehoeren in `useState`. Externe Systeme wie Rete.js werden ueber `useEffect`, `useRef`, `useMemo` und `useCallback` synchronisiert.
- **Rete.js:** Der bestehende Stack aus `AreaPlugin`, `ConnectionPlugin`, `ReactPlugin` und Custom Nodes kann fuer die Cockpit-Uebersicht wiederverwendet werden. Der Cockpit-Graph bleibt read-only und spiegelt Cockpit-Daten in WizardFeature-kompatible Nodes.

## Architektur

### Frontend

Neue Route:

- `frontend/src/app/projects/[id]/cockpit/page.tsx`

Neue Komponente:

- `frontend/src/components/cockpit/ProjectCockpitPrototype.tsx`

Anbindung im bestehenden Workspace:

- `frontend/src/app/projects/[id]/page.tsx` erhaelt einen `Cockpit`-Button im Header.

Wiederverwendete Graph-Komponenten:

- `frontend/src/components/wizard/steps/features/editor.ts`
- `frontend/src/components/wizard/steps/features/FeatureNode.tsx`

### Datenmodell im Prototyp

Der Prototyp nutzt lokale Mock-Daten mit:

- Feature-ID, Titel, Zusammenfassung
- Status `DONE`, `IN_PROGRESS`, `BLOCKED`, `PLANNED`
- Testkommando, Passed/Failed, Aktualisierungszeit
- Evidence-Liste
- offene Review-Items
- Design-Zaehler
- Handoff-Reife
- Graph-Position und Abhaengigkeiten

### Zukuenftige API-Anbindung

Spaetere produktive Anbindung sollte Daten aus diesen Quellen zusammenfuehren:

- Wizard FEATURES / Task-EPICs fuer Feature-Stammdaten
- Living Sync fuer Feature-Fortschritt, Testlaeufe, Code-Aenderungen und Notes
- Design Workbench fuer Designvarianten je Feature
- Consistency Checks fuer offene fachliche oder technische Hinweise

## Akzeptanzkriterien

1. Der Workspace bietet einen sichtbaren Einstieg ins Cockpit.
2. `/projects/{id}/cockpit` rendert eine Feature Workbench mit realistischer Cockpit-Struktur.
3. Nutzer koennen ein Feature aus der linken Liste auswaehlen.
4. Nutzer sehen Done-Review, offene Punkte, Testlaeufe und Evidence fuer das aktive Feature.
5. Nutzer koennen ein neues Feature kompakt erfassen.
6. Nutzer koennen ein Design fuer das aktive Feature erfassen.
7. Der Hauptscreen zeigt eine Rete.js-Uebersicht der Features.
8. Der aktive Graph-Node ist farblich markiert.
9. Doppelklick auf einen Graph-Node waehlt das Feature aus.
10. Der Screen baut erfolgreich und verletzt keine gezielten ESLint-Regeln.

## Abhaengigkeiten

- Feature 22: Intelligenter FEATURES-Step mit DAG
- Feature 45: Living-Sync via MCP
- Feature 47: Agentic Design Workbench
- Feature 48: Final Review Step

## Nicht-Ziele

- Keine vollstaendige Persistenz der Cockpit-Interaktionen.
- Keine echte Testausfuehrung.
- Keine echte Design-Agent-Anbindung aus dem Cockpit.
- Keine grosse Feature-Detailform.
- Keine zweite Wizard-Oberflaeche.

## Betroffene Dateien

- `frontend/src/app/projects/[id]/page.tsx`
- `frontend/src/app/projects/[id]/cockpit/page.tsx`
- `frontend/src/components/cockpit/ProjectCockpitPrototype.tsx`
- `frontend/src/components/wizard/steps/features/FeatureNode.tsx`
- `docs/superpowers/specs/2026-05-12-project-cockpit-feature-workbench-design.md`
