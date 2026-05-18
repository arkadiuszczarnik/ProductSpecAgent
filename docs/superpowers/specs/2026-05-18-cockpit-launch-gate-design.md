# Cockpit Launch Gate Design

## Ziel

Das bestehende Projekt-Cockpit soll als Folge-Arbeitsbereich nach dem Wizard genutzt werden, ohne den laufenden Wizard-Flow fuer unfertige Projekte zu stoeren.

## Produktregel

- Klick in der Projektuebersicht auf ein Projekt mit weniger als 100% Fortschritt oeffnet weiterhin den normalen Workspace unter `/projects/{id}`.
- Klick in der Projektuebersicht auf ein Projekt mit 100% Fortschritt oeffnet direkt das Cockpit unter `/projects/{id}/cockpit`.
- Der `Cockpit`-Button im Workspace bleibt bestehen, damit das Cockpit waehrend des Wizard-/Design-Flows manuell geprueft werden kann.
- Ein direkter Aufruf von `/projects/{id}/cockpit` bei unvollstaendigen Projekten zeigt eine Hinweisseite statt eines Redirects.

## Bestehender Zustand

- `frontend/src/app/projects/page.tsx` laedt pro Projekt `getProject(id)` und berechnet daraus bereits den Fortschritt anhand von `flowState.steps`.
- `frontend/src/app/projects/[id]/page.tsx` zeigt den `Cockpit`-Button aktuell immer an.
- `frontend/src/app/projects/[id]/cockpit/page.tsx` rendert das Cockpit aktuell bedingungslos.
- Feature 50 liefert den Cockpit-Screen als Workbench-Prototyp, aber keine produktive Zugriffsfuehrung.

## Readiness-Regel

Die Cockpit-Freigabe wird im ersten Schritt bewusst einfach und konsistent gehalten:

- `isCockpitReady == true`, wenn `flowState.steps.length > 0` und alle sichtbaren Wizard-Schritte den Status `COMPLETED` haben.
- Dieselbe Readiness-Regel wird in Projektuebersicht, Workspace und Cockpit-Route verwendet.
- Unsichtbare Schritte duerfen die Freigabe nicht blockieren.

Falls sich bei der Implementierung zeigt, dass `FlowState` die Sichtbarkeit nicht sauber genug transportiert, darf die Freigabe ueber einen kleinen API-Zusatz oder ueber die bestehende Wizard-Progression zentralisiert werden.

## Architektur

### Frontend

- `frontend/src/app/projects/page.tsx`
  - berechnet zusaetzlich `isCockpitReady`
  - nutzt fuer den Projektkarten-Link bedingt `/projects/{id}` oder `/projects/{id}/cockpit`
- `frontend/src/app/projects/[id]/page.tsx`
  - behaelt den `Cockpit`-Button
  - zeigt ihn nur, wenn das Projekt cockpit-bereit ist
- `frontend/src/app/projects/[id]/cockpit/page.tsx`
  - prueft Readiness vor dem Rendern des Cockpit-Prototyps
  - zeigt bei nicht fertigen Projekten eine dedizierte Hinweisseite mit Rueckweg zum Workspace

### Backend / Daten

Bevorzugt keine neue API:

- `GET /api/v1/projects/{id}` liefert bereits `ProjectResponse` mit `flowState`
- diese Datenbasis reicht voraussichtlich fuer die erste Version

Nur wenn noetig:

- kleiner API-Zusatz fuer `isCockpitReady`
- oder Wiederverwendung einer Wizard-Progression im Frontend

## UX

- Unfertige Projekte fuehlen sich weiterhin wie „im Wizard“ an.
- Fertige Projekte springen aus der Projektuebersicht direkt in die naechste Phase.
- Der Workspace bleibt fuer alle Projekte erreichbar und veraendert sein Verhalten nicht grundsaetzlich.
- Die Cockpit-Hinweisseite soll klar sagen, warum das Cockpit noch nicht verfuegbar ist, und einen direkten Link zur Workspace-Seite anbieten.

## Betroffene Dateien

- `docs/features/52-cockpit-launch-gate-after-wizard.md`
- `frontend/src/app/projects/page.tsx`
- `frontend/src/app/projects/[id]/page.tsx`
- `frontend/src/app/projects/[id]/cockpit/page.tsx`
- optional `frontend/src/lib/api.ts`
- optional Backend-Dateien rund um `ProjectResponse` oder Wizard-Progression, falls fuer Readiness doch eine zentrale Ableitung noetig wird

## Tests

- Frontend-Verhalten fuer Projektkarten-Link bei `<100%` und `100%`
- Sichtbarkeit des `Cockpit`-Buttons im Workspace
- Cockpit-Route zeigt bei unvollstaendigem Projekt die Hinweisseite
- Build/Lint fuer die geaenderten Frontend-Dateien

## Risiken / Offene Punkte

- `flowState` und „sichtbare Schritte“ koennten nicht exakt dieselbe Sicht transportieren wie die Wizard-Progression.
- Context7 war waehrend der Recherche nicht erreichbar; die Implementierung wird deshalb am bestehenden Next.js-/React-Code des Repos ausgerichtet und spaeter bei Bedarf gegen aktuelle Doku gegengeprueft.
