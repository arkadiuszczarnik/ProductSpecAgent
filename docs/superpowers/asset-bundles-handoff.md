# Asset-Bundles — Stand & Resume-Anleitung

**Letzter Sync:** 2026-04-30

Asset-Bundles ist ein Feature in drei Sub-Features. Alle drei sind jetzt auf `main`. Dieses Dokument bleibt als Resume-Anleitung für offene Restarbeiten (Browser-Smoke).

## Übersicht

| Sub-Feature | Inhalt | Status |
|---|---|---|
| **A** — Storage-Foundation | Domain, `AssetBundleStorage` (read-only), REST-List/Detail, S3-Layout-Doku, ~17 Tests | ✅ Auf `main` (Commit `19a853f`) |
| **B** — Admin-UI | ZIP-Upload, Liste, File-Vorschau, Delete; Backend-Schreib-API + Frontend-Page `/asset-bundles`; ~44 neue Tests | ✅ Auf `main` (Tipp `797d70e`); Browser-Smoke ausstehend |
| **C** — Export-Integration | Match Wizard-Wahl → Bundle-Triple, Merge in Project-Export-ZIP unter `.claude/{skills,commands,agents}/<bundle-id>/...` | ✅ Auf `main` (Tipp `731e548`, FF-Merge am 2026-04-30, 10 Commits, ~16 Tests); Browser-Smoke ausstehend |

## Stand der Branches

**`main`:** Enthält A, B und C. Lokal weit vor `origin/main` (kein Push laut Handoff-Konvention).

**Gelöschte Feature-Branches** (nach jeweiligem Merge):
- `feat/asset-bundle-admin-ui` — B
- `feat/asset-bundle-export` — C

**Working-Tree (durchlaufend, unabhängig):** `M infra/workloads/Pulumi.dev.yaml` und `asset-bundles/` (untracked, manuelle Test-ZIPs).

## Verifikation des Ist-Zustands

```bash
cd backend && ./gradlew test
cd ../frontend && npm run build

# C-Code auf main prüfen
ls backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt
ls backend/src/test/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter*Test.kt

# Spec + Plan + Feature-Doc von C
ls docs/superpowers/specs/2026-04-30-asset-bundle-*
ls docs/superpowers/plans/2026-04-30-asset-bundle-*
ls docs/features/26-asset-bundle-export.md

# Feature-Docs für A und B (nachträglich erstellt)
ls docs/features/33-asset-bundle-storage-foundation*.md
ls docs/features/34-asset-bundle-admin-ui*.md
```

## Was offen ist

### Sub-Feature B — Browser-Smoke (Post-Merge)
- **Manueller Browser-Smoke:** Akzeptanzkriterien #11-15 aus
  `docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md` Task 14 Step 6:
  Upload → Liste → Detail → File-Vorschau (md/code/image) → Delete-Confirmation → Filter.
- Start: `./start.sh` (oder `./gradlew bootRun --quiet` + `cd frontend && npm run dev`).
- Test-ZIP: `cd my-bundle/ && zip -r ../my-bundle.zip . -x ".*"` (Layout: `manifest.json` + `skills/`/`commands/`/`agents/` direkt im Root). Im Repo-Root liegt `asset-bundles/stitch-frontend-bundle.zip` als Beispiel.

### Sub-Feature C — Browser-Smoke (Post-Merge)
- **End-to-End-Manualcheck:**
  1. Bundle hochladen via `/asset-bundles`-UI für Triple `(BACKEND, framework, spring-boot)` (oder analog).
  2. Projekt erstellen, Wizard-BACKEND-Step ausfüllen mit `framework=spring-boot`.
  3. Project-Export aus Project-Detail-View triggern.
  4. ZIP entpacken, prüfen:
     - `<prefix>/.claude/skills/backend.framework.spring-boot/...` Dateien vorhanden.
     - `<prefix>/README.md` enthält Sektion `## Included Asset Bundles` mit Bundle-Title.

### Optionale Folgearbeiten (Nicht im Scope von A/B/C)
- `INFO`-Log-Zeile in `writeToZip` falls ein Bundle 0 Files unter den drei Top-Dirs hat (Spec-Edge-Case-Tabelle nennt das, aber Implementation läuft schweigend leer durch — funktional korrekt, semantisch fehlt das Log).
- Doku-Refresh: `docs/features/10-project-scaffold-export.md` ggf. um Hinweis auf `.claude/`-Auslieferung erweitern.

## Resume-Prompt für die neue Session

> Asset-Bundles-Feature ist auf `main` (A, B, C). Lies
> `docs/superpowers/asset-bundles-handoff.md` und sag mir, was noch
> offen ist. Browser-Smoke von B und C steht aus.

Mögliche nächste Wünsche:
1. **„Browser-Smoke für B/C durchführen"** — Backend + Frontend hochfahren, Klick-Anleitung folgen.
2. **„INFO-Log für 0-File-Bundle nachziehen"** — kleine Spec-Compliance-Lücke aus dem Final-Review.
3. **„Doku in `10-project-scaffold-export.md` aktualisieren"** — Hinweis auf `.claude/`-Auslieferung.
