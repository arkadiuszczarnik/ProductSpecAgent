# Feature 52: Cockpit Launch Gate nach Wizard-Abschluss

## Problem

Das Projekt-Cockpit existiert bereits als eigener Screen, ist aktuell aber immer erreichbar:

- In der Workspace-Ansicht wird der `Cockpit`-Button immer angezeigt.
- In der Projektübersicht oeffnet ein Klick auf ein Projekt immer den normalen Workspace.
- Die Cockpit-Route `/projects/{id}/cockpit` ist nicht daran gekoppelt, ob der Wizard fuer das Projekt wirklich abgeschlossen ist.

Damit fehlt die produktive Einbettung des Cockpits in den Projektfluss. Das Cockpit soll erst dann als naechster Arbeitsbereich erscheinen, wenn das Projekt im Wizard wirklich auf 100% steht.

## Ziel

Das Cockpit wird als Folge-Arbeitsflaeche **erst nach abgeschlossenem Wizard** angeboten:

1. Ein Projekt gilt als cockpit-bereit, wenn alle sichtbaren Wizard-Schritte abgeschlossen sind und der Projektfortschritt auf 100% steht.
2. In der Projektuebersicht oeffnet ein Klick auf ein vollstaendig abgeschlossenes Projekt direkt das Cockpit.
3. In der Projektuebersicht oeffnet ein Klick auf ein unvollstaendiges Projekt weiterhin den normalen Workspace.
4. In der Workspace-Ansicht wird der `Cockpit`-Button nur fuer abgeschlossene Projekte angeboten.
5. Die Cockpit-Route selbst schuetzt sich gegen direkten Zugriff bei unvollstaendigen Projekten.

## Bestehender Zustand

- `frontend/src/app/projects/page.tsx` berechnet bereits `progress` aus `flowState.steps` ueber `completedSteps / totalSteps * 100`.
- `frontend/src/app/projects/[id]/page.tsx` zeigt den `Cockpit`-Button aktuell immer an.
- `frontend/src/app/projects/[id]/cockpit/page.tsx` rendert das Cockpit aktuell ohne Verfuegbarkeitspruefung.
- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardProgressionModels.kt` und `service/WizardProgressionPolicy.kt` modellieren sichtbare Wizard-Schritte und einen finalen Review-Step.
- Feature 50 liefert das Cockpit-Layout und den Workbench-Prototyp, aber noch keine produktive Launch-Logik.

## Architektur

### Frontend

Die Launch-Entscheidung soll zunaechst **frontend-seitig auf Basis bestehender REST-Daten** erfolgen:

- In der Projektuebersicht wird fuer jedes Projekt weiterhin `getProject(projectId)` geladen.
- Aus `flowState.steps` wird ein boolescher Zustand `isCockpitReady` abgeleitet.
- Der Karten-Link in der Uebersicht zeigt auf:
  - `/projects/{id}/cockpit`, wenn `isCockpitReady == true`
  - `/projects/{id}`, wenn `isCockpitReady == false`
- Die Workspace-Seite zeigt den `Cockpit`-Button nur im `ready`-Fall.
- Die Cockpit-Page validiert beim Rendern ebenfalls den Fortschritt und leitet sonst zur Workspace-Seite zurueck oder zeigt einen klaren Hinweis.

### Readiness-Regel

Die erste produktive Regel ist bewusst simpel und deckungsgleich mit der Uebersicht:

- `isCockpitReady == true`, wenn
  - `flowState.steps.length > 0`
  - alle sichtbaren Schritte den Status `COMPLETED` haben
  - daraus effektiv 100% Fortschritt resultiert

Unsichtbare oder nicht relevante Schritte duerfen die Freigabe nicht blockieren.

### API / Datenmodell

Das Feature soll wenn moeglich **ohne neues Backend-API** auskommen und auf vorhandene Modelle aufsetzen:

- `GET /api/v1/projects/{id}` liefert `ProjectResponse` mit `flowState`
- `FlowState.steps[].status` ist die massgebliche Quelle fuer den Fortschritt

Falls sich im Code zeigt, dass sichtbare und unsichtbare Schritte in der Projektuebersicht nicht sauber unterscheidbar sind, darf ein kleiner API-Zusatz eingefuehrt werden, z. B. ein abgeleitetes Feld `isCockpitReady` oder eine Wiederverwendung der Wizard-Progression fuer das Frontend.

## UX-Verhalten

- Nicht fertige Projekte bleiben im bestehenden Workspace-Fluss.
- Fertige Projekte fuehlen sich in der Uebersicht wie „uebergeben an das Cockpit“ an.
- Das Cockpit ist kein alternativer Wizard-Einstieg, sondern die Folgephase nach dem Wizard.
- Direkter Deep-Link auf das Cockpit eines unvollstaendigen Projekts soll nicht in einer halbfertigen Ansicht landen.

## Akzeptanzkriterien

1. In der Projektuebersicht wird fuer jedes Projekt weiterhin der Wizard-Fortschritt angezeigt.
2. Ein Projekt mit 100% Fortschritt oeffnet bei Klick direkt `/projects/{id}/cockpit`.
3. Ein Projekt mit weniger als 100% Fortschritt oeffnet bei Klick weiterhin `/projects/{id}`.
4. Der `Cockpit`-Button im Workspace ist nur sichtbar, wenn das Projekt wizard-seitig abgeschlossen ist.
5. Die Route `/projects/{id}/cockpit` ist fuer unvollstaendige Projekte nicht normal nutzbar.
6. Die Freigabelogik basiert auf denselben Fortschrittsdaten in Uebersicht, Workspace und Cockpit-Route.
7. Die Implementierung baut erfolgreich und verletzt keine gezielten ESLint-Regeln.

## Abhaengigkeiten

- Feature 11: Guided Wizard Forms
- Feature 12: Dynamische Wizard-Steps
- Feature 13: Wizard-Chat Integration
- Feature 48: Final Review Step
- Feature 50: Project Cockpit Feature Workbench

## Nicht-Ziele

- Keine inhaltliche Erweiterung des Cockpit-Screens selbst.
- Keine neue persistente Cockpit-Domainlogik.
- Kein komplettes Re-Design der Projektuebersicht.
- Keine neue Rechte- oder Rollenlogik.

## Betroffene Dateien

- `frontend/src/app/projects/page.tsx`
- `frontend/src/app/projects/[id]/page.tsx`
- `frontend/src/app/projects/[id]/cockpit/page.tsx`
- `frontend/src/components/cockpit/ProjectCockpitPrototype.tsx` (nur falls Readiness-Hinweise im Screen selbst noetig sind)
- optional `frontend/src/lib/api.ts`
- optional Backend-Dateien rund um `ProjectResponse` / Wizard-Progression, falls die bestehende `flowState`-Sicht fuer eine saubere Freigabe nicht ausreicht
