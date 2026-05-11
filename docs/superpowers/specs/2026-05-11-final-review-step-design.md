# Final Review Step - Design Spec

**Datum:** 2026-05-11
**Bezug:** Neues Feature nach Feature 47. Fuehrt einen expliziten finalen Wizard-Step ein, der fuer jede Projektkategorie als Review-Gate vor Spec-Erzeugung und Export dient.
**Status:** Design - Review ausstehend

## Kontext

Der Wizard hat aktuell keinen eigenen finalen Schritt. Stattdessen ist der letzte sichtbare Fach-Step je Kategorie terminal:

- `Library`: `MVP`
- `CLI Tool`: `ARCHITECTURE`
- `API`: `BACKEND`
- `SaaS`, `Mobile App`, `Desktop App`: `FRONTEND`

Beim Abschliessen dieses jeweils letzten Steps erzeugt `WizardStepCompletionService` die finale `spec.md` und oeffnet danach den Export. Das macht den Abschluss technisch korrekt, aber fuer Nutzer nicht explizit genug: Sie sehen keinen eigenen Review-Punkt, an dem sie alle Eingaben pruefen und bewusst final bestaetigen.

## Ziel

Ein neuer Wizard-Step `REVIEW` wird immer als letzter sichtbarer Step angezeigt, unabhaengig von der Projektkategorie. Er zeigt eine strukturierte Zusammenfassung aller bisher erfassten Wizard-Daten und dient als bewusstes Review-Gate.

Der Nutzer kann:

- alle relevanten Wizard-Inhalte gesammelt pruefen
- ueber die bestehende Step-Navigation in fruehere Steps zurueckgehen
- den Wizard final bestaetigen
- nach erfolgreicher Bestaetigung exportieren

Erst die Bestaetigung im `REVIEW`-Step erzeugt die finale `spec.md` und aktiviert den Export.

## Nicht-Ziele

- Kein Inline-Hinzufuegen oder Bearbeiten von Features im `REVIEW`-Step.
- Kein neuer Feature-Editor, keine Duplikation des bestehenden FEATURES-Graph-Editors.
- Keine Agent-generierte Review-Zusammenfassung vor der finalen Bestaetigung.
- Kein Preview-Export vor finaler Bestaetigung.
- Keine Aenderung an Auth, Projektanlage oder Handoff-Export-Format.

## Recherche-Notizen

Context7 bestaetigt fuer Next.js App Router und React 19 die bestehende Projektlinie: Interaktive Wizard-Oberflaechen gehoeren in Client Components; Listen und abgeleitete UIs sollten aus bestehendem State gerendert und mit unveraenderlichen Updates bearbeitet werden. Fuer dieses Feature bedeutet das: Die `ReviewForm` kann rein aus `WizardData` und bestehendem Store-State abgeleitet werden.

Context7 bestaetigt fuer Spring Boot 4 die bestehende REST-/JSON-Linie: `@RestController`-Antworten werden normal als JSON serialisiert; neue Enum-Werte in DTOs koennen ueber bestehende Controller-/Service-Tests abgesichert werden. Fuer dieses Feature ist kein neuer API-Typ zwingend noetig, aber `FlowStepType.REVIEW` wird ueber bestehende Progression-DTOs sichtbar.

## Produktlogik

`REVIEW` ist ein echter Wizard-Step, nicht nur ein Dialog:

```text
IDEA -> PROBLEM -> FEATURES -> MVP -> [kategorieabhaengige Fach-Steps] -> REVIEW -> Export
```

Vor `REVIEW` bleiben die fachlichen Steps unveraendert. Der bisherige letzte Fach-Step navigiert nur noch weiter zu `REVIEW`; er erzeugt keine finale Spec und oeffnet keinen Export mehr.

Der `REVIEW`-Step ist terminal. Beim Abschliessen:

1. `REVIEW` wird als abgeschlossen gespeichert.
2. Das Backend baut aus dem vollstaendigen Wizard-Kontext die finale `spec.md`.
3. Die Wizard-Progression wechselt auf `READY_FOR_EXPORT`.
4. Das Frontend bietet den Export an.

## Backend-Design

### Domain

`FlowStepType` bekommt den neuen Wert:

```kotlin
enum class FlowStepType {
    IDEA, PROBLEM, FEATURES, MVP, DESIGN,
    ARCHITECTURE, BACKEND, FRONTEND, REVIEW
}
```

`createInitialFlowState()` nimmt den neuen Enum-Wert automatisch in die initiale Step-Liste auf, weil es `FlowStepType.entries` verwendet.

### Progression Policy

`WizardProgressionPolicy` haengt `FlowStepType.REVIEW` an jede sichtbare Step-Liste an:

- Default/full flow endet mit `REVIEW`
- `Library` endet mit `MVP, REVIEW`
- `CLI Tool` endet mit `ARCHITECTURE, REVIEW`
- `API` endet mit `BACKEND, REVIEW`
- `SaaS`, `Mobile App`, `Desktop App` enden mit `FRONTEND, REVIEW`

Wenn der Admin-Wizard-Options-Katalog `visibleSteps` liefert, wird `REVIEW` trotzdem garantiert angehaengt. Damit kann ein Admin fachliche Steps konfigurieren, aber den finalen Review-Step nicht versehentlich entfernen.

### Wizard Options Defaults

`WizardOptionCatalogDefaults` enthaelt `REVIEW` in jeder Kategorie am Ende von `visibleSteps`. Fuer `REVIEW` werden keine Feldoptionen benoetigt.

### Step Completion

`WizardStepCompletionService` behandelt nur noch `FlowStepType.REVIEW` als terminalen Export-Step.

Fachliche letzte Steps sind nicht mehr terminal, weil `WizardProgressionPolicy` danach `REVIEW` liefert. Dadurch greift die bestehende Nicht-Terminal-Logik:

- Step wird als `COMPLETED` markiert
- `REVIEW` wird `IN_PROGRESS`
- Docs-Scaffold wird regeneriert
- Client-Action ist `SHOW_STEP(REVIEW)`

Beim Abschliessen von `REVIEW`:

- es werden keine Decisions oder Clarifications erzeugt
- der Agent bekommt einen kurzen Abschluss-Prompt ohne Marker-Anforderung
- `spec.md` wird aus dem vollstaendigen Kontext erzeugt
- Client-Action ist `OPEN_EXPORT`

Der `REVIEW`-Step speichert minimale Felder, zum Beispiel:

```json
{
  "confirmed": true
}
```

Die sichtbare Zusammenfassung wird nicht als eigene Wahrheit persistiert.

## Frontend-Design

### Step-Konfiguration

`frontend/src/lib/category-step-config.ts` und `frontend/src/lib/stores/wizard-store.ts` bekommen `REVIEW` als neuen Step-Key.

Label:

```text
Review
```

Hilfetext:

```text
Pruefe die Zusammenfassung und bestaetige die Spezifikation fuer den Export.
```

Icon: ein vorhandenes `lucide-react` Icon wie `ClipboardCheck` oder `CheckCircle2`.

### ReviewForm

Neue Komponente:

```text
frontend/src/components/wizard/steps/ReviewForm.tsx
```

Sie rendert eine strukturierte Zusammenfassung aus `useWizardStore().data`:

- Idee: Produktname, Kategorie, Vision
- Problem & Zielgruppe: Kernproblem, Primaere Zielgruppe, Pain Points
- Features: Feature-Titel, Scope, Beschreibung, Akzeptanzkriterien falls vorhanden
- MVP: MVP-Beschreibung und Priorisierung
- Design: vorhandene Design-Zusammenfassung oder Hinweis, falls kein Design-Step sichtbar/ausgefuellt ist
- Architektur, Backend, Frontend: nur anzeigen, wenn Daten vorhanden oder der Step fuer die Kategorie sichtbar ist

Die Ansicht ist read-only. Nutzer koennen ueber die bestehende Step-Navigation zurueckgehen, wenn sie etwas aendern wollen.

### WizardForm Actions

Vor Abschluss von `REVIEW` zeigt der Primary Button:

```text
Final bestaetigen
```

Nach erfolgreichem Abschluss und `OPEN_EXPORT` zeigt der Button:

```text
Exportieren
```

Die bestehende Export-Dialog-Integration bleibt erhalten. Das Frontend darf Export erst anbieten, wenn die Progression `READY_FOR_EXPORT` oder `primaryAction.type === "OPEN_EXPORT"` meldet.

### Chat-Verhalten

Beim Abschluss von `REVIEW` kann das Frontend eine kurze User-Message in den Chat schreiben, zum Beispiel:

```markdown
**Review**

Finale Zusammenfassung geprueft und bestaetigt.
```

Die Agent-Antwort ist eine kurze Abschlussmeldung. Sie soll keine Rueckfragen und keine neuen Entscheidungen erzeugen.

## Datenfluss

```text
WizardData + WizardProgression
        |
        v
ReviewForm rendert abgeleitete Zusammenfassung
        |
        v
User klickt "Final bestaetigen"
        |
        v
POST /api/v1/projects/{id}/agent/wizard-step-complete
step = REVIEW, fields = { confirmed: true }
        |
        v
WizardStepCompletionService erzeugt spec.md
        |
        v
Progression READY_FOR_EXPORT + Client-Action OPEN_EXPORT
        |
        v
Frontend oeffnet Export
```

## Fehlerfaelle

- Wenn `REVIEW` direkt aufgerufen wird, bevor es aktueller sichtbarer Step ist, greift die bestehende `WizardStepNotCurrentException`.
- Wenn alte Projekte ohne `REVIEW`-Step geladen werden, existiert `REVIEW` durch den neuen Enum-Wert im Flow-State bei neuen Projekten automatisch. Fuer bestehende Flow-State-Dateien muss die Progression tolerant bleiben: fehlende Statuswerte fuer sichtbare Steps werden als `OPEN` behandelt, wie es `snapshotFor()` bereits fuer fehlende Statuswerte tut.
- Wenn der Nutzer nach finaler Bestaetigung zu frueheren Steps zurueckgeht und Aenderungen macht, bleibt der Review-Step der einzige Export-Gate. Ein veraenderter Fach-Step darf nicht direkt Export oeffnen; die finale `spec.md` wird erst bei der naechsten `REVIEW`-Bestaetigung erneut erzeugt.

## Tests

Backend:

- `WizardProgressionPolicyTest`: jede Kategorie endet mit `REVIEW`.
- `WizardProgressionPolicyTest`: Katalog-Overrides koennen `REVIEW` nicht entfernen.
- `WizardStepCompletionServiceTest`: bisheriger letzter Fach-Step navigiert zu `REVIEW` und erzeugt keinen Export.
- `WizardStepCompletionServiceTest`: `REVIEW`-Completion erzeugt `spec.md`, markiert Progression als `READY_FOR_EXPORT` und liefert `OPEN_EXPORT`.

Frontend:

- `visibleSteps()` enthaelt `REVIEW` als letzten Step fuer alle Kategorien.
- `WizardForm` rendert `ReviewForm` fuer `activeStep === "REVIEW"`.
- `ReviewForm` rendert zentrale Wizard-Daten read-only.
- Primary Button zeigt im Review vor Abschluss `Final bestaetigen` und nach `OPEN_EXPORT` `Exportieren`.

## Akzeptanzkriterien

- Jede Projektkategorie zeigt einen finalen `Review`-Step.
- Kein fachlicher Step erzeugt direkt den finalen Export.
- Der Review-Step zeigt eine nachvollziehbare Zusammenfassung aller vorhandenen Wizard-Inhalte.
- Der Nutzer kann vor der finalen Bestaetigung ueber die Step-Navigation zurueckgehen.
- Erst nach `Final bestaetigen` wird die finale `spec.md` erzeugt.
- Erst nach erfolgreicher Review-Bestaetigung ist Export verfuegbar.
- Feature-Hinzufuegen im Review-Step ist nicht enthalten.
