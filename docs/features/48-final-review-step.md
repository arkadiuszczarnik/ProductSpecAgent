# Feature 48: Final Review Step

## Zusammenfassung

Der Wizard bekommt einen expliziten finalen `REVIEW`-Step fuer alle Projektarten. Dieser Step zeigt eine read-only Zusammenfassung aller Wizard-Daten, dient als bewusstes Review-Gate und aktiviert den Export erst nach finaler Bestaetigung.

## Problem

Aktuell erzeugt der jeweils letzte sichtbare Fach-Step direkt die finale `spec.md` und oeffnet den Export. Je nach Kategorie ist das `MVP`, `ARCHITECTURE`, `BACKEND` oder `FRONTEND`. Dadurch fehlt ein expliziter Abschlussmoment, an dem Nutzer die gesamte Spezifikation pruefen und bewusst freigeben.

## Ziel

- `REVIEW` ist immer der letzte sichtbare Wizard-Step.
- Kein fachlicher Step erzeugt direkt den Export.
- Der Review-Step zeigt eine strukturierte Zusammenfassung aus bestehenden Wizard-Daten.
- Erst `Final bestaetigen` erzeugt `spec.md` und macht Export verfuegbar.
- Feature-Hinzufuegen im Review-Step ist nicht enthalten.

## Betroffene Dateien

- `backend/src/main/kotlin/com/agentwork/productspecagent/domain/FlowState.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardProgressionPolicy.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardOptionCatalogDefaults.kt`
- `backend/src/main/kotlin/com/agentwork/productspecagent/service/WizardStepCompletion.kt`
- `frontend/src/lib/api.ts`
- `frontend/src/lib/category-step-config.ts`
- `frontend/src/lib/stores/wizard-store.ts`
- `frontend/src/components/wizard/WizardForm.tsx`
- `frontend/src/components/wizard/steps/ReviewForm.tsx`

## Akzeptanzkriterien

- Jede Kategorie zeigt `Review` als letzten Wizard-Step.
- Der bisherige letzte Fach-Step navigiert zu `Review` statt Export zu oeffnen.
- `Review` rendert zentrale Wizard-Daten read-only.
- `Final bestaetigen` schliesst `REVIEW` ab und erzeugt `spec.md`.
- Erst danach ist Export verfuegbar.
