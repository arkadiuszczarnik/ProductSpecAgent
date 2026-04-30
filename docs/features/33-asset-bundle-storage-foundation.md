# Feature 33: Asset-Bundle-Storage-Foundation (Sub-Feature A)

**Phase:** Asset-Bundles (Sub-Feature A von 3)
**Abhängig von:** Feature 31 (Project Storage auf S3)
**Aufwand:** M
**Status:** Spec approved, Implementierung ausstehend

> **Hinweis zur Nachträglichkeit:** Diese Feature-Doc wurde erst nach der Implementierung erstellt. Die Konvention aus `CLAUDE.md` ("Always write the feature doc BEFORE implementing code") wurde bei Sub-Feature A initial übergangen — die Spec/Plan-Dokumente lagen unter `docs/superpowers/specs/` und `docs/superpowers/plans/`, jedoch ohne korrespondierende `docs/features/`-Datei. Diese Doc holt das nach.

## Problem & Ziel

Beim Project-Export sollen passende, vorkurierte Claude-Code-Assets (Skills, Commands, Agents) zur jeweiligen Tech-Wahl ins ZIP aufgenommen werden — ein generiertes Projekt soll direkt mit Claude-Code lauffähig sein. Sub-Feature A liefert dafür die Backend-Foundation.

**Ziel:** Read-only Storage-Schicht für "Asset-Bundles" in S3. Pro Wizard-Wahl-Triple `(step, field, value)` ein Bundle, identifiziert über deterministischen Slug-Schlüssel. REST-API zum Auflisten und Detail-Abfragen.

Sub-Feature B (Admin-UI) und C (Export-Integration) bauen darauf auf.

## Architektur (Kurzfassung)

```
AssetBundleController (REST)
        │
        ▼
AssetBundleStorage (List + Find)
        │
        ▼
ObjectStore (existiert seit Feature 31)
        │
        ▼
S3 / MinIO  (Prefix: asset-bundles/)
```

**Schlüsselentscheidungen:**
- Granularität: ein Bundle pro Wizard-Wert-Triple (`step.field.value`)
- Bundle-Inhalt polymorph: Skills + Commands + Agents in einem Bundle
- Identität: zusammengesetzter Schlüssel `${step.lower()}.${field}.${slugify(value)}`
- Single-Version, In-Place-Overwrite (Versions-Feld informativ)
- Gleicher S3-Bucket wie Projekt-Daten, separater Prefix `asset-bundles/`
- Discovery live via `listCommonPrefixes`, kein Cache (S3 = Source of Truth)

## S3-Layout

```
asset-bundles/
  backend.framework.kotlin-spring/
    manifest.json
    skills/
      spring-testing/
        SKILL.md
    commands/
      gradle-build.md
    agents/
      spring-debug.md
  frontend.framework.stitch/
    manifest.json
    skills/...
```

**Bundle-Folder-Name = Bundle-ID = `${step.lowercase()}.${field}.${slugify(value)}`**

`slugify`: lowercase, ersetze `[^a-z0-9]+` durch `-`, trimme `-` an den Rändern.

## Manifest-Schema

```json
{
  "id": "backend.framework.kotlin-spring",
  "step": "BACKEND",
  "field": "framework",
  "value": "Kotlin+Spring",
  "version": "1.0.0",
  "title": "Kotlin + Spring Boot Essentials",
  "description": "Skills, Commands und Agents für Spring-Boot-Backend-Entwicklung",
  "createdAt": "2026-04-29T12:00:00Z",
  "updatedAt": "2026-04-29T12:00:00Z"
}
```

`step` ∈ {`BACKEND`, `FRONTEND`, `ARCHITECTURE`}. Andere Werte → Bundle wird verworfen mit Log-Warnung.

## API-Endpoints

| Methode | Pfad | Response |
|---|---|---|
| GET | `/api/v1/asset-bundles` | `200 OK` + Liste `AssetBundleListItem` (mit `fileCount`) |
| GET | `/api/v1/asset-bundles/{step}/{field}/{value}` | `200 OK` + `AssetBundleDetail` oder `404 Not Found` |

**Read-only.** Schreibende Endpoints kommen erst in Sub-Feature B.

## Akzeptanzkriterien

1. `AssetBundle*`-Domain-Klassen vorhanden, `kotlinx.serialization`-annotiert wo nötig
2. `AssetBundleStorage.listAll()` liefert alle Manifeste aus `asset-bundles/`-Prefix
3. `AssetBundleStorage.find(triple)` liefert Bundle inkl. Files (relative Pfade ohne S3-Key-Prefix)
4. Korrupte Manifeste werden geloggt und übersprungen, nicht propagiert
5. `GET /api/v1/asset-bundles` listet alle Bundles
6. `GET /api/v1/asset-bundles/{step}/{field}/{value}` liefert Detail oder 404
7. Invalides `step`-Enum → 400
8. Integration-Test gegen Testcontainers-MinIO grün
9. `docs/architecture/persistence.md` um Asset-Bundle-Layout ergänzt
10. Alle bestehenden Tests bleiben grün (`./gradlew test`)

## Out of Scope (YAGNI)

- Schreibende API (Upload/Delete/Update) — Sub-Feature B
- Frontend-UI — Sub-Feature B
- File-Download-Endpoint (`…/files/{path}`) — Sub-Feature B
- Match-Algorithmus Wizard-Wahl → Bundle — Sub-Feature C
- ZIP-Merging beim Export — Sub-Feature C
- Versionierung jenseits des informativen `version`-String-Felds
- Authentifizierung/Autorisierung
- Performance-Optimierung (Index-Files, Caching)

## Detaildesign

Vollständige Spezifikation inkl. Komponenten-Skizze, Datentypen, Test-Setup, Error-Tabelle und Risiken:
[docs/superpowers/specs/2026-04-29-asset-bundle-storage-design.md](../superpowers/specs/2026-04-29-asset-bundle-storage-design.md)

Implementierungsplan:
[docs/superpowers/plans/2026-04-29-asset-bundle-storage.md](../superpowers/plans/2026-04-29-asset-bundle-storage.md)
