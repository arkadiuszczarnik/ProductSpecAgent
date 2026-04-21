# Feature 25: TARGET_AUDIENCE-Step — Feld „Technisches Level" entfernen

## Zusammenfassung
Im Wizard-Step `TARGET_AUDIENCE` wird das Chip-Select-Feld „Technisches Level" (Optionen: Nicht-technisch / Technisch / Entwickler) entfernt. Der Key `techLevel` verschwindet aus dem Formular und aus den Anzeige-Labels. Bestehende Projekte mit einem bereits gesetzten `techLevel`-Wert behalten diesen Wert in `wizard.json` auf der Platte (keine Migration), er wird nur nicht mehr im UI gerendert.

## Acceptance Criteria
- [ ] `TargetAudienceForm.tsx` zeigt kein „Technisches Level"-Feld mehr
- [ ] `step-field-labels.ts` enthaelt keinen Eintrag `techLevel: "…"` mehr im `TARGET_AUDIENCE`-Block
- [ ] Ungenutzter `ChipSelect`-Import aus `TargetAudienceForm.tsx` entfernt
- [ ] Frontend-Lint-Baseline unveraendert
- [ ] Manuell im Browser verifiziert: TARGET_AUDIENCE-Step zeigt nur noch Primaere Zielgruppe, Pain Points, Sekundaere Zielgruppe

## Technische Details
- Datei `frontend/src/components/wizard/steps/TargetAudienceForm.tsx`: den `<FormField label="Technisches Level">`-Block (inkl. `ChipSelect`) entfernen; nicht mehr genutzten `ChipSelect`-Import weg
- Datei `frontend/src/lib/step-field-labels.ts`: Zeile `techLevel: "Technisches Level",` im `TARGET_AUDIENCE`-Block entfernen
- Kein Backend-Change (das Feld war nur wizard-state-intern, generisch vom Agent gelesen)
- Kein Test-Change (keine Unit-Tests decken dieses Feld ab)

## Nicht im Scope
- Migration bestehender `wizard.json`-Dateien (existierende `techLevel`-Werte bleiben auf der Platte liegen)
- Bereinigung von `scripts/test-agent-flow.sh` (historisches manuelles Debug-Script, sendet `techLevel` generisch an den Agent; harmlose Redundanz)
- Aktualisierung von `docs/features/11-guided-wizard-forms.md` (historische Feature-Doc, bleibt unveraendert)

## Aufwand
XS
