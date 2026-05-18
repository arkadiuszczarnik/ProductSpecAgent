# Feature 52: Cockpit Launch Gate nach Wizard-Abschluss - Done

## Summary

Das Cockpit ist jetzt produktiv in den Projektfluss eingebunden. Ein Klick auf ein unvollstaendiges Projekt in der Uebersicht fuehrt weiterhin in den Wizard-Workspace. Ein Klick auf ein vollstaendiges Projekt oeffnet direkt das Cockpit.

Die Cockpit-Route selbst ist zusaetzlich abgesichert. Wenn ein Projekt noch nicht wizard-fertig ist, erscheint dort eine klare Hinweisseite mit Rueckweg zum Workspace statt einer halbfertigen Cockpit-Ansicht.

## Implementierung

- Gemeinsame Readiness-Helfer: `frontend/src/lib/project-cockpit.ts`
- Projektuebersicht nutzt cockpit-spezifische Zielroute: `frontend/src/app/projects/page.tsx`
- Workspace zeigt den `Cockpit`-Button nur fuer fertige Projekte: `frontend/src/app/projects/[id]/page.tsx`
- Cockpit-Route mit Guard und Hinweiszustand: `frontend/src/app/projects/[id]/cockpit/page.tsx`
- Neue Hinweiskomponente: `frontend/src/components/cockpit/CockpitLockedNotice.tsx`
- Feature-Dokument: `docs/features/52-cockpit-launch-gate-after-wizard.md`
- Design-Spec: `docs/superpowers/specs/2026-05-18-cockpit-launch-gate-design.md`

## Validierung

- `cd frontend && npx eslint src/app/projects/page.tsx`
- `cd frontend && npx eslint 'src/app/projects/[id]/page.tsx'`
- `cd frontend && npx eslint 'src/app/projects/[id]/cockpit/page.tsx' 'src/components/cockpit/CockpitLockedNotice.tsx'`
- `cd frontend && npm run build`

## Abweichungen

- Es wurde keine neue Backend-API eingefuehrt. Die Freigabelogik basiert komplett auf dem bestehenden `flowState`.
- Es wurden keine neuen Frontend-Komponententests hinzugefuegt, weil im Repo dafuer weiterhin kein dedizierter Unit-Test-Runner existiert.

## Follow-Up

- E2E-Abdeckung fuer beide Navigationspfade ergaenzen: unfertiges Projekt -> Workspace, fertiges Projekt -> Cockpit.
- Falls `flowState.steps` spaeter nicht mehr exakt der sichtbaren Wizard-Progression entspricht, die Readiness zentral im Backend ableiten.
