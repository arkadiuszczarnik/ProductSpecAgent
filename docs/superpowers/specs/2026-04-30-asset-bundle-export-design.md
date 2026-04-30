# Sub-Feature C — Asset-Bundle-Export-Integration

**Datum:** 2026-04-30
**Status:** Design — Review ausstehend
**Übergeordnetes Feature:** Asset-Bundles beim Project-Export aus S3 hinzufügen
**Voraussetzungen:**
- Sub-Feature A (`docs/superpowers/specs/2026-04-29-asset-bundle-storage-design.md`) auf `main` (Commit `19a853f`)
- Sub-Feature B (`docs/superpowers/specs/2026-04-29-asset-bundle-admin-ui-design.md`) auf `main` (Tipp `797d70e`, gemerged 2026-04-30)

## Kontext

Sub-Features A und B haben Storage und Admin-UI für kuratierte Asset-Bundles geliefert. Bundles enthalten Claude-Code-Artefakte (Skills, Commands, Agents) und sind über das Triple `(step, field, value)` adressiert — z. B. `(BACKEND, "framework", "spring-boot")`.

Sub-Feature C ist die fehlende Klammer: beim Project-Export wird automatisch geschaut, welche Bundles zu den Wizard-Wahlen des Projekts passen, und deren Files werden in das Export-ZIP unter `.claude/{skills,commands,agents}/` gemerged. Damit erhält der User ein „Claude-Code-ready" Projekt — die durch den Wizard angezeigten Tech-Entscheidungen kommen mit den passenden Skills geliefert.

Das Feature ist bewusst silent: keine UI-Interaktion, kein Opt-out, keine Preview. Die Wizard-Choices sind die explizite User-Entscheidung; ein zusätzlicher Bestätigungs-Klick wäre Reibung. Transparenz wird über eine generierte „Included Asset Bundles"-Sektion in der Export-`README.md` hergestellt.

## Scope

**In-Scope:**

Backend:
- Eine neue Service-Klasse `AssetBundleExporter` mit drei klar getrennten Methoden (Match, Zip-Write, README-Render).
- Erweiterung von `ExportService.exportProject()` um drei kleine Hooks (Konstruktor-Dependency, ZIP-Schritt, README-Sektion).
- Slugify-tolerantes Matching: Wizard-Display-Werte wie `"Spring Boot"` matchen Bundle-Triple-Values wie `"spring-boot"`.
- JsonArray- und Number-Coercion für Wizard-Werte.
- Defensive Fehlerbehandlung: Bundle-Probleme dürfen den Project-Export nie brechen.
- Tests: drei neue Test-Klassen (`AssetBundleExporterMatchTest`, `AssetBundleExporterZipTest`, `AssetBundleExporterReadmeTest`) plus zwei zusätzliche Cases im bestehenden `ExportServiceTest`.

Frontend:
- Keine Änderungen.

**Explizit Out-of-Scope:**
- Opt-in/Opt-out-Flag im Export-Dialog (siehe Entscheidung 1).
- UI-Preview, welche Bundles eingeschlossen werden (siehe Entscheidung 1).
- Versionierung in der Match-Logik (Triple ist eindeutig; ein Bundle pro Triple per A-Design).
- Performance-Optimierung über bulk-S3-Reads (für aktuell zu erwartende Bundle-Counts irrelevant).
- Konflikt-Auflösung bei gleichnamigen Files (durch Namespace-Strategie obsolet, siehe Entscheidung 3).
- Anpassung der Frontend-Asset-Bundle-Admin-UI (z. B. „diese Bundle wird beim Export aktiv für N Projekte verwendet").

## Entwurfs-Entscheidungen

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | **Trigger:** Silent always-merge | Wizard-Choices sind die explizite User-Entscheidung; zusätzlicher Klick wäre Reibung. Transparenz via README-Sektion ist günstiger und reicht aus. |
| 2 | **Match-Direction:** Bundle-driven (`storage.listAll()` → für jeden Manifest gegen `wizardData` checken) | Bundle-Author hat via Manifest schon entschieden, welcher Triple matcht. System respektiert nur. Vermeidet Whitelist-Pflege. |
| 3 | **Path-Mapping:** Namespaced unter `<bundle-id>/` | Konflikte zwischen Bundles physisch unmöglich. Claude-Code findet Skills rekursiv via `SKILL.md` — ein zusätzliches Verzeichnis-Level ist funktional egal, dafür wird das mitgelieferte Verzeichnis bei Bedarf nachvollziehbar (welcher Bundle stammt der Skill-Eintrag aus?). |
| 4 | **Slugify-Tolerant Match** | Wizard zeigt Display-Strings (`"Spring Boot"`), Bundle-IDs nutzen Slugs (`"spring-boot"`). Ohne Slugify-Tolerance müsste der Bundle-Author den Display-String exakt treffen, das ist fragil. Beide Seiten werden über `assetBundleSlug()` normalisiert verglichen. |
| 5 | **JsonArray-Verhalten:** über String-Elemente iterieren | Wizard-Felder können Multi-Selects sein (z. B. mehrere Frameworks). Pro String-Element wird gegen den Bundle-Triple-Value verglichen. |
| 6 | **Number-Coercion:** `toString()` | Macht numerische Wizard-Werte (z. B. `replicas: 3`) im Match nutzbar. Praktisch selten relevant, aber semantisch konsistent und billig. |
| 7 | **Defensive Fehlerbehandlung** | Bundle-Export ist „nice to have", Spec-Export ist Kerngeschäft. Storage-Outages, korrupte Manifests, race conditions führen zum Skippen einzelner Bundles, nicht zum Export-Abbruch. |
| 8 | **Pfad-Whitelist beim Export-Schreiben** | Files ausserhalb der drei Top-Dirs (`skills/commands/agents`) werden im Export ignoriert (defensiv gegen Bundles mit unerwartetem Top-Level-Content). Für Path-Traversal-Versuche zusätzlich `Path.normalize()`-Check, obwohl die ZipExtractor-Validation in B das schon im Upload-Pfad abfängt. |

## Architektur & Komponenten

```
┌─────────────────┐           ┌──────────────────────┐
│  ExportService  │──reads──→ │  AssetBundleExporter │
│  (existing)     │           │  (new)               │
└─────────────────┘           └──────────┬───────────┘
       │                                 │
       │ writes ZIP entries              │ matches & loads
       ▼                                 ▼
┌─────────────────┐           ┌──────────────────────┐
│  ZipOutputStream│           │  AssetBundleStorage  │
└─────────────────┘           │  (from B)            │
                              └──────────────────────┘
```

### Neue Klasse: `AssetBundleExporter`

Pfad: `backend/src/main/kotlin/com/agentwork/productspecagent/export/AssetBundleExporter.kt`

```kotlin
@Service
class AssetBundleExporter(
    private val storage: AssetBundleStorage
) {
    /** Findet alle Bundles, deren Triple in wizardData vorkommt. */
    fun matchedBundles(wizardData: WizardData): List<MatchedBundle>

    /** Schreibt Bundle-Files unter <prefix>/.claude/{type}/<bundle-id>/... */
    fun writeToZip(zip: ZipOutputStream, prefix: String, bundles: List<MatchedBundle>)

    /** Rendert README-Sektion „## Included Asset Bundles" als Markdown. Leerer String wenn keine Bundles. */
    fun renderReadmeSection(bundles: List<MatchedBundle>): String
}

data class MatchedBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)
```

### Touch-Points in `ExportService`

- Konstruktor erweitert um `+ assetBundleExporter: AssetBundleExporter`.
- In `exportProject()`:
  - Wizard-Daten laden (über bestehende `WizardService.getWizardData(projectId)` oder analogen Pfad — exakte Quelle wird im Implementation-Plan verifiziert).
  - `val bundles = assetBundleExporter.matchedBundles(wizardData)` — einmalig vor dem ZIP-Schreiben.
  - `assetBundleExporter.writeToZip(zip, prefix, bundles)` — innerhalb des `ZipOutputStream.use { }`-Blocks neben den anderen ZIP-Entries.
- In `generateReadme()`:
  - Methode bekommt zusätzlichen Parameter `bundles: List<MatchedBundle>`.
  - Am Ende der README wird `assetBundleExporter.renderReadmeSection(bundles)` angehängt (leerer String bei null Bundles → no-op).

## Daten-Fluss

1. **Trigger.** `POST /api/v1/projects/{id}/export` → `ExportService.exportProject(projectId, request)` wie bisher.
2. **Wizard-Load.** `wizardData = wizardService.getWizardData(projectId)` (oder bestehender Code-Pfad).
3. **Bundle-Match (`matchedBundles(wizardData)`).**
   ```
   storage.listAll()                           → List<AssetBundleManifest>
     for each manifest m:
       val raw = wizardData.steps[m.step.name]?.fields?.get(m.field)
       val candidates: Set<String> = when (raw) {
         is JsonPrimitive (string)  → setOf(raw.content)
         is JsonPrimitive (number)  → setOf(raw.content)  // toString-Repräsentation
         is JsonArray               → raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
         else (null/object/JsonNull) → emptySet()
       }
       val bundleSlug = assetBundleSlug(m.value)
       if (candidates.any { assetBundleSlug(it) == bundleSlug }) → keep
     for each kept manifest:
       storage.find(m.step, m.field, m.value)  → AssetBundle (incl. files list)
     wrap each in MatchedBundle, sort alphabetically by manifest.id
     return List<MatchedBundle>
   ```
4. **Files in ZIP schreiben (`writeToZip`).** Pro Bundle:
   - Iteriere `bundle.files` (Manifest ist im Storage bereits aus der Liste herausgefiltert).
   - Splitte `file.relativePath` am ersten `/` in `<type>` (erstes Segment) und `<rest>` (alles danach).
   - Whitelist-Check: `<type>` muss `skills` | `commands` | `agents` sein. Sonst skip.
   - `Path.normalize()`-Check auf `file.relativePath`: keine `..`-Komponenten, kein leading `/`, normalize-Output identisch zum Original. Sonst skip + log WARN.
   - `bytes = storage.loadFileBytes(step, field, value, file.relativePath)` — bei `null`: skip dieses File + log WARN.
   - ZIP-Entry: `<prefix>/.claude/<type>/<bundle.manifest.id>/<rest>`. Beispiel: Bundle-ID `backend.framework.spring-boot`, File `skills/api-design/SKILL.md` → `<type>=skills`, `<rest>=api-design/SKILL.md` → ZIP-Entry `<prefix>/.claude/skills/backend.framework.spring-boot/api-design/SKILL.md`.
5. **README-Sektion (`renderReadmeSection`).** Bei mindestens einem Bundle wird folgendes Markdown-Snippet am Ende der von `generateReadme()` gerenderten README angehängt:
   ```markdown
   ## Included Asset Bundles

   The following Claude Code asset bundles were merged into `.claude/` based on your wizard choices:

   - **<bundle.title>** (`<bundle.id>` v<bundle.version>) — matched on `<step>.<field> = <value>`
     <bundle.description>
   - ...
   ```
   Bei null Bundles: leerer String → README bleibt unverändert.
6. **Endergebnis.** Eine ZIP, identisch zur bisherigen Output-Struktur, plus optional `<prefix>/.claude/{skills,commands,agents}/<bundle-id>/...` und die optionale README-Sektion.

## Edge Cases & Error Handling

**Defensiv-Strategie:** Asset-Bundle-Export darf den Project-Export nie brechen. Alle Fehler führen zum Skippen des betroffenen Bundles bzw. Files, nicht zum Export-Abbruch.

| Edge Case | Verhalten |
|---|---|
| Wizard-Step fehlt in `wizardData.steps` | Skip — Bundle matcht nicht. Normal-Fall, kein Log. |
| Field fehlt im Step | Skip — Normal-Fall, kein Log. |
| Value ist `JsonNull` / `JsonObject` | Skip — keine Match-Semantik definierbar. Normal-Fall. |
| Storage `listAll()` wirft (S3-Outage) | Catch, log `WARN`, return `emptyList()`. Export läuft ohne Bundles weiter. README schreibt keine Sektion. |
| `storage.find()` returns null (Race: Bundle gelöscht zwischen `listAll` und `find`) | Skip dieses Bundle, log `WARN`. Andere laufen normal. |
| `loadFileBytes()` returns null für eine erwartete Datei | Skip dieses einzelne File, log `WARN`. Andere Files des Bundles werden trotzdem geschrieben. |
| Bundle hat 0 Files unter den drei Top-Dirs | Bundle wird in README gelistet, aber keine ZIP-Entries. Akzeptabel; im Log eine `INFO`-Zeile. |
| Pfad-Traversal in `relativePath` (`skills/../../etc/passwd`) | Durch B-Validation schon beim Upload abgewiesen. Defensiv: trotzdem `Path.normalize()`-Check, reject + log `WARN` falls auffällig. |
| Bundle-Manifest hat ungültigen `step`-Enum-Wert (Refactor-Drift) | Catch im Match-Loop, log `WARN`, skip. |
| Wizard hat Number `3`, Bundle-Triple-Value ist String `"3"` | Match (Number → String → slugify). |
| Wizard hat Display-String `"Spring Boot"`, Bundle-Triple-Value `"spring-boot"` | Match (slugify-tolerant). |

## Testing Strategy

Drei neue Test-Klassen plus zwei Cases in bestehendem ExportServiceTest. Alle Edge Cases aus dem Abschnitt oben sind testabgedeckt.

### `AssetBundleExporterMatchTest`
Match-Logik isoliert, ohne ZIP, ohne Filesystem. Mock `AssetBundleStorage.listAll()`.
- Empty wizard, empty storage → 0 matches.
- Empty wizard, 3 bundles → 0 matches.
- Wizard mit String-Field, Bundle matcht → 1 match.
- Wizard mit JsonArray (`["spring-boot", "ktor"]`), 2 Bundles für jedes Element → 2 matches.
- Wizard mit Number-Field (`3`), Bundle Triple `"3"` → 1 match.
- Wizard mit Display-String `"Spring Boot"`, Bundle Triple `"spring-boot"` → 1 match.
- Wizard mit `JsonNull`, Bundle existiert für Field → 0 matches.
- Wizard-Step fehlt komplett → 0 matches, kein Crash.
- Bundle mit Manifest-Drift (invalid step enum) → skip + log, andere Bundles trotzdem gefunden.
- `storage.listAll()` wirft IOException → emptyList(), kein Throw.

### `AssetBundleExporterZipTest`
ZIP-Schreiben mit `ByteArrayOutputStream` + `ZipInputStream`-Verifikation.
- 1 Bundle mit `skills/foo.md` → ZIP enthält genau `<prefix>/.claude/skills/<bundle-id>/foo.md` mit korrektem Inhalt.
- 1 Bundle mit Files in allen drei Top-Dirs → 3 ZIP-Entries an erwarteten Pfaden.
- 2 Bundles mit gleichnamiger Datei (`skills/api-design.md`) → beide Entries existieren unter ihren `<bundle-id>/`-Namespaces.
- Bundle mit File ausserhalb `skills/commands/agents` → wird übersprungen.
- Pfad-Traversal-Versuch im fixture → wird abgewiesen, kein Entry, log WARN.
- `loadFileBytes()` returns null für ein File → File übersprungen, andere Files des Bundles geschrieben.
- 0 Bundles → keine `.claude/`-Entries.

### `AssetBundleExporterReadmeTest`
- 0 Bundles → returns empty string.
- 1 Bundle → enthält `## Included Asset Bundles`, Title, ID, Version, Trigger-Triple, Description.
- N Bundles → deterministische Reihenfolge (alphabetisch nach Bundle-ID).

### Erweiterte `ExportServiceTest`-Cases
- `exportProject - includes asset bundle files in zip`: Mockt AssetBundleExporter (oder Storage), prüft Bundle-Entries unter erwarteten Prefixes im finalen ZIP.
- `exportProject - readme contains bundle list when bundles match`: prüft README-Bestandteil im ZIP.

Bestehende `ExportServiceTest`-Cases bleiben mit leerem Bundle-Set grün (Mock-default).

## Open Questions

Keine — alle Architektur-Entscheidungen sind im Brainstorming geklärt. Die exakte Quelle für `wizardData` (direkt aus `wizardService.getWizardData()` oder via einer Methode in `ProjectService`) wird im Implementation-Plan finalisiert; sie ist nicht designrelevant.
