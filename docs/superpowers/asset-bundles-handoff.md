# Asset-Bundles — Stand & Resume-Anleitung

**Letzter Sync:** 2026-04-30

Asset-Bundles ist ein Feature in drei Sub-Features. Dieses Dokument speichert den aktuellen Fortschritt und die exakten Anweisungen, um in einer neuen Claude-Session weiterzumachen.

## Übersicht

| Sub-Feature | Inhalt | Status |
|---|---|---|
| **A** — Storage-Foundation | Domain, `AssetBundleStorage` (read-only), REST-List/Detail, S3-Layout-Doku, ~17 Tests | ✅ Auf `main` (Commit `19a853f`) |
| **B** — Admin-UI | ZIP-Upload, Liste, File-Vorschau, Delete; Backend-Schreib-API + Frontend-Page `/asset-bundles`; ~44 neue Tests | ✅ Auf `main` (rebase+FF am 2026-04-30, Tipp `797d70e`); Browser-Smoke ausstehend |
| **C** — Export-Integration | Match Wizard-Wahl → Bundle-Triple, Merge in Project-Export-ZIP unter `.claude/{skills,commands,agents}/` | ⏸ Spec auf `main` (`d7b1a12`), Implementation-Plan und Code stehen aus |

## Stand der Branches

**`main`:**
- Enthält Sub-Feature A (`19a853f`) und Sub-Feature B (`748e933` … `797d70e`).
- Lokal weit vor `origin/main` (kein Push laut Handoff-Konvention).
- Branch `feat/asset-bundle-admin-ui` ist nach dem Merge gelöscht.

**Working-Tree (durchlaufend):** `M infra/workloads/Pulumi.dev.yaml` ist eigene Infra-Arbeit, unabhängig von Asset-Bundles. `asset-bundles/` (untracked) enthält manuelle Test-ZIPs für den Smoke.

## Verifikation des Ist-Zustands

```bash
cd backend && ./gradlew test
cd ../frontend && npm run build

# B-Code auf main prüfen
ls backend/src/main/kotlin/com/agentwork/productspecagent/service/AssetBundleZipExtractor.kt
ls frontend/src/app/asset-bundles/page.tsx

# Spec + Plan
ls docs/superpowers/specs/2026-04-29-asset-bundle-*
ls docs/superpowers/plans/2026-04-29-asset-bundle-*
```

## Was offen ist

### Sub-Feature B — Browser-Smoke (Post-Merge)
- **Manueller Browser-Smoke:** Akzeptanzkriterien #11-15 aus
  `docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md` Task 14 Step 6:
  Upload → Liste → Detail → File-Vorschau (md/code/image) → Delete-Confirmation → Filter
- `./start.sh` (oder `./gradlew bootRun --quiet` + `cd frontend && npm run dev`)
- Test-ZIP: `cd my-bundle/ && zip -r ../my-bundle.zip . -x ".*"` (Layout: `manifest.json` + `skills/`/`commands/`/`agents/` direkt im Root). Im Repo-Root liegt `asset-bundles/stitch-frontend-bundle.zip` als Beispiel.

### Sub-Feature C
- Spec auf `main`: `docs/superpowers/specs/2026-04-30-asset-bundle-export-design.md` (Commit `d7b1a12`).
- Feature-Doc: `docs/features/26-asset-bundle-export.md`.
- Implementation-Plan und Code stehen aus — als Nächstes via `superpowers:writing-plans` Skill.
- Kern-Entscheidungen aus dem Brainstorming:
  - Trigger: silent always-merge (kein Opt-out, kein UI-Flag); Transparenz via README-Sektion.
  - Path-Mapping: namespaced unter `<prefix>/.claude/{type}/<bundle-id>/...`, daher Konflikte konstruktiv ausgeschlossen.
  - Match: Bundle-driven (`storage.listAll()` → für jedes Manifest Wizard-Daten checken), slugify-tolerant.

## Resume-Prompt für die neue Session

> Ich möchte das Asset-Bundles-Feature weitermachen. Lies bitte zuerst
> `docs/superpowers/asset-bundles-handoff.md` und sag mir den Stand
> sowie die nächsten möglichen Schritte. Sub-Features A und B sind auf
> `main`, C ist als Spec auf `main`, Plan und Code offen.

Mögliche nächste Wünsche:
1. **„Browser-Smoke für Sub-Feature B durchführen"** — startet Backend + Frontend, gibt dir Klick-Anleitung.
2. **„Implementation-Plan für Sub-Feature C schreiben"** — `superpowers:writing-plans` über das Spec.
3. **„Sub-Feature C implementieren"** — falls Plan schon existiert, direkt in die Umsetzung.
4. **„Code-Review von Sub-Feature B"** — falls du die Implementierung nochmal durchgehen willst.
