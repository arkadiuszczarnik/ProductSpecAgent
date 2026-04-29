# Asset-Bundles — Stand & Resume-Anleitung

**Letzter Sync:** 2026-04-29

Asset-Bundles ist ein Feature in drei Sub-Features. Dieses Dokument speichert den aktuellen Fortschritt und die exakten Anweisungen, um in einer neuen Claude-Session weiterzumachen.

## Übersicht

| Sub-Feature | Inhalt | Status |
|---|---|---|
| **A** — Storage-Foundation | Domain, `AssetBundleStorage` (read-only), REST-List/Detail, S3-Layout-Doku, ~17 Tests | ✅ **Auf `main` gemerged** (Commit `19a853f`) |
| **B** — Admin-UI | ZIP-Upload, Liste, File-Vorschau, Delete; Backend-Schreib-API + Frontend-Page `/asset-bundles`; ~44 neue Tests | ⏸ **Auf `feat/asset-bundle-admin-ui`, ungemerged**, Browser-Smoke ausstehend |
| **C** — Export-Integration | Match Wizard-Wahl → Bundle-Triple, Merge in Project-Export-ZIP unter `.claude/{skills,commands,agents}/` | ⏸ Noch nicht angefangen — Brainstorming offen |

## Stand der Branches

**`main`:**
- Sub-Feature A komplett enthalten (gemerged via Fast-Forward bei `19a853f`)
- Tipp ist `main` selbst (du bist `48 commits ahead of origin/main`, lokal — kein Push)

**`feat/asset-bundle-admin-ui`:**
- 20 Commits voraus von `main` (ab `748e933` „add design spec and implementation plan" bis `42a54eb` „close acceptance-criteria gaps from final review")
- Alle Backend-Tests grün (`./gradlew test` BUILD SUCCESSFUL)
- Frontend `npm run build` clean, lint-clean (außer einer ignorierten `<img>`-Warning für Bilder im Asset-Viewer)
- **Browser-Smoke nicht durchgeführt** — Akzeptanzkriterien #11–15 aus dem Plan stehen aus
- Spec: `docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md`
- Plan: `docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md`

**Working-Tree:** `M infra/workloads/Pulumi.dev.yaml` ist Eigene-Infra-Arbeit, unabhängig von Asset-Bundles, durchgehend uncommitted.

## Verifikation des Ist-Zustands

```bash
# Branches sehen
git branch -a

# Auf Sub-Feature B umschalten
git checkout feat/asset-bundle-admin-ui

# Backend-Tests grün?
cd backend && ./gradlew test --quiet
cd ..

# Frontend baut?
cd frontend && npm run build
cd ..

# Spec + Plan vorhanden?
ls docs/superpowers/specs/2026-04-29-asset-bundle-*
ls docs/superpowers/plans/2026-04-29-asset-bundle-*
```

## Was offen ist

### Sub-Feature B vor dem Merge
- **Manueller Browser-Smoke:** Akzeptanzkriterien #11-15 aus
  `docs/superpowers/plans/2026-04-29-asset-bundle-admin-ui.md` Task 14 Step 6:
  Upload → Liste → Detail → File-Vorschau (md/code/image) → Delete-Confirmation → Filter
- Empfohlen: `./start.sh` (oder `./gradlew bootRun --quiet` + `cd frontend && npm run dev` parallel) und durchklicken
- Test-ZIP zum Hochladen kann lokal mit `cd my-bundle/ && zip -r ../my-bundle.zip . -x ".*"` gepackt werden (Layout: `manifest.json` + `skills/`/`commands/`/`agents/` direkt im Root)

### Sub-Feature C
- Nicht angefangen
- Spec/Plan existieren noch nicht
- Voraussetzungen aus B sind erfüllt: `AssetBundleStorage.find(triple)`, `loadFileBytes(...)`, `GET /files/**`-Endpoint sind alle nutzbar
- Brainstorming-Themen (für Reference):
  - Match-Algorithmus: aus `wizard.json`-Werten alle relevanten Triples ableiten und gegen `storage.find(...)` checken
  - Mapping in das Export-ZIP: Bundle-Files unter `.claude/skills/...`, `.claude/commands/...`, `.claude/agents/...` einfügen
  - Konflikt-Behandlung wenn zwei Bundles gleichnamige Dateien beitragen (unwahrscheinlich, aber dokumentationswürdig)
  - Optional: UI-Vorschau vor Export, welche Bundles drinhängen

## Resume-Prompt für die neue Session

In der neuen Claude-Session diesen Prompt verwenden:

> Ich möchte das Asset-Bundles-Feature weitermachen. Lies bitte zuerst
> `docs/superpowers/asset-bundles-handoff.md` und sag mir den Stand
> sowie die nächsten möglichen Schritte. Sub-Feature A ist auf `main`,
> Sub-Feature B liegt auf Branch `feat/asset-bundle-admin-ui` ungemerged.

Mögliche nächste Wünsche, die du dann äußern kannst:
1. **„Browser-Smoke für Sub-Feature B durchführen"** — startet Backend + Frontend, gibt dir Klick-Anleitung
2. **„Sub-Feature B nach `main` mergen"** — Fast-Forward-Merge (kein Push)
3. **„Sub-Feature C brainstormen"** — startet das Brainstorming für die Export-Integration
4. **„Code-Review von Sub-Feature B"** — falls du die Implementierung nochmal durchgehen willst
