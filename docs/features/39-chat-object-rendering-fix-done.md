# Feature 39 — Chat-Anzeige-Fix für strukturierte Wizard-Felder — Done

**Datum:** 2026-05-03
**Branch:** `fix/chat-object-rendering` (gemerged via Merge-Commit `fe20cb1`)
**Feature-Doc:** `docs/features/39-chat-object-rendering-fix.md`
**Plan:** keiner (kleiner Bugfix, kein `superpowers:writing-plans`-Lauf)

## Was umgesetzt wurde

**Frontend** (`frontend/src/lib/step-field-labels.ts`)

- `formatStepFields` bekommt einen `FEATURES`-Sonderpfad: `key === "edges"` wird übersprungen, `key === "features"` wird über die neue Helper-Funktion `formatFeaturesList(features, edges)` als Bullet-Liste mit Title und optionalem `— depends on: <Title1>, <Title2>` gerendert. Dependencies werden aus den `WizardFeatureEdge`-Einträgen rekonstruiert (Edge `from → to` heißt „`to` hängt von `from` ab"). Unbekannte Edges (IDs, die in der Feature-Liste fehlen) werden ignoriert.
- Generischer Fallback `formatFieldValue(value)` ersetzt das alte `Array.isArray(value) ? value.join(", ") : String(value)`. Object-Arrays werden via `JSON.stringify` pro Element serialisiert, plain Objects via `JSON.stringify`. Primitiv-Arrays bleiben unverändert mit Komma-Join.
- Type-Imports erweitert: `WizardFeature`, `WizardFeatureEdge` aus `./api`.

Keine Backend-Änderungen, keine neuen Tests (Frontend hat keinen Test-Runner).

## Bewusste Abweichungen

- Der Fix wurde **nicht** über den vollen `implement-feature`-Workflow (Brainstorming → writing-plans → subagent-driven-development) gefahren, sondern als reiner Direkt-Bugfix umgesetzt. Daher kein eigener Plan unter `docs/superpowers/plans/`.
- Die Code-Änderung steckt im Commit `6df5308` mit irreführender Message (`feat(sync): remove orphan docs ...`) — der gleiche Commit enthält auch zwei unrelated Backend-/Sidebar-Änderungen. Feature-Doc kam einen Commit später (`0b5fe54`) als Nachdokumentation.

## Akzeptanzkriterien-Status

| # | Kriterium | Status |
|---|---|---|
| 1 | FEATURES-Step zeigt Bullet-Liste der Feature-Titles statt `[object Object]` | ✅ via `formatFeaturesList` |
| 2 | Dependencies mit Titles statt UUIDs | ✅ über `titleById`-Map |
| 3 | `edges` taucht im Chat nicht mehr auf | ✅ `if (key === "edges") continue` |
| 4 | Andere Steps unverändert | ✅ Sonderpfad nur bei `step === "FEATURES"` |
| 5 | `npm run lint` / `npm run build` grün | ✅ (Lint-Baseline unverändert) |
| 6 | Manuelle Browser-Verifikation | ⚠️ vom User durchgeführt, vor Merge bestätigt |

## Follow-up-Kandidaten

- Markdown-Rendering im `ChatMessage`-Component, damit Bullet-Listen und Bold tatsächlich als Markdown statt Plain Text erscheinen (explizit out-of-scope der Feature-Doc, aber jetzt deutlicher sichtbar weil der Output strukturierter ist).
- Symmetrische Object-Array-Behandlung für künftige neue Object-Felder ohne Sonderpfad — aktuell rettet der `JSON.stringify`-Fallback nur vor `[object Object]`, ist aber für komplexe Objekte (Scope, Architektur-Entscheidungen) keine schöne Darstellung.
