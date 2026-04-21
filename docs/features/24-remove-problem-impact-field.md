# Feature 24: PROBLEM-Step — Feld „Auswirkung" entfernen

## Zusammenfassung
Im Wizard-Step `PROBLEM` wird das Chip-Select-Feld „Auswirkung" (Optionen: Gering / Mittel / Hoch / Kritisch) entfernt. Der Key `impact` verschwindet aus dem Formular und aus den Anzeige-Labels. Bestehende Projekte mit einem bereits gesetzten `impact`-Wert in `wizard.json` behalten diesen Wert auf der Platte (keine Migration), er wird nur nicht mehr im UI gerendert.

## Acceptance Criteria
- [ ] `ProblemForm.tsx` zeigt kein „Auswirkung"-Feld mehr
- [ ] `step-field-labels.ts` enthaelt keinen Eintrag `impact: "…"` mehr im `PROBLEM`-Block
- [ ] Frontend-Lint-Baseline unveraendert
- [ ] Manuell im Browser verifiziert: PROBLEM-Step zeigt nur noch Kernproblem, Wer ist betroffen?, Aktuelle Workarounds

## Technische Details
- Datei `frontend/src/components/wizard/steps/ProblemForm.tsx`: den `<FormField label="Auswirkung">`-Block (inkl. `ChipSelect`) entfernen
- Datei `frontend/src/lib/step-field-labels.ts`: Zeile `impact: "Auswirkung (Impact)",` im `PROBLEM`-Block entfernen
- Kein Backend-Change (das Feld war nur wizard-state-intern, keine Serialisierungs-/Marker-Abhaengigkeit)
- Kein Test-Change (keine Unit-Tests decken dieses Feld ab)

## Nicht im Scope
- Migration bestehender `wizard.json`-Dateien (die dortigen `impact`-Werte bleiben auf der Platte liegen, werden aber nicht mehr angezeigt; wenn der User den PROBLEM-Step neu speichert, bleibt das Feld ebenfalls unberuehrt, da `updateField` nur schreibt wenn das UI etwas an `impact` aendert)
- Anpassung des PROBLEM-Step-Prompts im Agent (der Agent liest alle Felder generisch)
- Aktualisierung von `docs/features/11-guided-wizard-forms.md` (historische Feature-Doc, bleibt unveraendert)

## Aufwand
XS
