# Feature 46 — Wizard-Options-Admin

**Phase:** Wizard-Konfiguration / Admin
**Abhängig von:** Feature 11 (Guided Wizard Forms), Feature 12 (Dynamische Wizard-Steps), Feature 34 (Asset-Bundle-Admin-UI), Feature 41 (Asset-Bundle-Coverage-View)
**Aufwand:** M
**Status:** Spec erstellt, Implementierung ausstehend

## Problem

Die Auswahloptionen für Projektkategorie, Architektur, Datenbank, Backend-Sprache/-Framework, API-Stil, Auth, Frontend-Framework, UI-Library, Styling und ähnliche Wizard-Felder sind heute hart in `frontend/src/lib/category-step-config.ts` gepflegt.

Das bedeutet:

- Eine neue Programmiersprache wie "Java+Spring" oder "Elixir+Phoenix" erfordert Codeänderung, Build und Deployment.
- Eine neue Datenbank oder Architektur-Option muss manuell in mehreren Kategorien ergänzt werden.
- Asset-Bundle-Curatoren sehen fehlende Bundles aus dieser statischen Liste, können die Liste selbst aber nicht pflegen.
- Die fachliche Pflege ist mit Entwicklerarbeit gekoppelt, obwohl es eigentlich Admin-/Kurator-Aufgabe ist.

## Ziel

Eine pflegbare Admin-Seite, mit der berechtigte Nutzer Wizard-Auswahloptionen verwalten können. Die Seite ersetzt die hart codierten Optionslisten schrittweise durch eine persistierte globale Konfiguration.

Der erste Zielzustand:

- Kategorien wie `SaaS`, `Mobile App`, `CLI Tool`, `Library`, `Desktop App`, `API` können angezeigt und bearbeitet werden.
- Pro Kategorie können sichtbare Wizard-Steps, erlaubte Feature-Scopes und Feldoptionen gepflegt werden.
- Für `ARCHITECTURE`, `BACKEND` und `FRONTEND` können Admins neue Werte hinzufügen, bearbeiten, deaktivieren und sortieren.
- Wizard-Forms und Asset-Bundle-Coverage-View lesen die Optionen aus derselben Quelle.

## Nutzerrollen

- **Admin / Curator:** pflegt Kategorien, Felder und Optionen.
- **Spec-Ersteller:** nutzt die gepflegten Optionen im normalen Wizard.
- **Asset-Bundle-Curator:** sieht neue Optionen in der Missing-Bundle-Ansicht und kann passende Bundles ergänzen.

## Architektur

### Heute

```text
frontend/src/lib/category-step-config.ts
  -> WizardForm / Step-Forms
  -> Asset-Bundle Missing-Triples
```

### Ziel

```text
Admin UI (/admin/wizard-options)
        |
        v
WizardOptionController
        |
        v
WizardOptionCatalogService
        |
        v
ProjectStorage/ObjectStore

Wizard UI und Asset-Bundle-Coverage laden denselben Katalog per API.
```

Die bestehende `CATEGORY_STEP_CONFIG` bleibt als Default/Fallback erhalten, damit bestehende Installationen sofort weiter funktionieren. Beim ersten Start kann der Backend-Service diesen Default-Katalog aus einer Ressource oder aus Code initialisieren, falls noch keine persistierte Konfiguration existiert.

## Datenmodell

Ein globaler Katalog beschreibt Kategorien, sichtbare Steps, erlaubte Scopes und Feldoptionen.

```kotlin
@Serializable
data class WizardOptionCatalog(
    val version: Int,
    val categories: List<WizardCategoryConfig>,
    val updatedAt: String,
)

@Serializable
data class WizardCategoryConfig(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val visibleSteps: List<FlowStepType>,
    val allowedScopes: List<FeatureScope>,
    val fieldOptions: Map<FlowStepType, List<WizardFieldOptionGroup>>,
)

@Serializable
data class WizardFieldOptionGroup(
    val fieldKey: String,
    val label: String,
    val options: List<WizardSelectableOption>,
)

@Serializable
data class WizardSelectableOption(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val sortOrder: Int,
)
```

Persistenz: global unter einem stabilen Key, z. B. `config/wizard-options/catalog.json`.

## API

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/v1/admin/wizard-options` | Aktuellen Katalog laden |
| `PUT` | `/api/v1/admin/wizard-options` | Vollständigen Katalog speichern |
| `POST` | `/api/v1/admin/wizard-options/reset` | Auf Default-Katalog zurücksetzen |
| `GET` | `/api/v1/wizard-options` | Read-only Katalog für Wizard und öffentliche Frontend-Nutzung |

Für die erste Iteration reicht ein vollständiger `PUT` des Katalogs. Feingranulare Patch-Endpunkte sind bewusst out of scope.

## Frontend

Neue Admin-Seite:

```text
/admin/wizard-options
```

Layout:

- Linke Spalte: Kategorienliste mit Status, z. B. `SaaS`, `Mobile App`, `API`
- Mitte: Kategorie-Editor mit sichtbaren Steps und erlaubten Scopes
- Rechte Seite oder Tab: Feldgruppen und Optionen pro Step

Bedienung:

- Option hinzufügen
- Option umbenennen
- Option deaktivieren/aktivieren
- Option löschen, wenn sie noch nicht genutzt wurde
- Reihenfolge ändern
- Kategorie aktivieren/deaktivieren
- Reset auf Default mit Bestätigungsdialog

Bestehende Wizard-Formulare nutzen die API-Daten über einen Store/Hook, z. B. `useWizardOptionsStore`.

## Validierung

Backend-validiert:

- Kategorie-IDs eindeutig und nicht leer
- Feldgruppen je Kategorie/Step eindeutig
- Options-IDs je Feldgruppe eindeutig
- `visibleSteps` enthalten nur bekannte `FlowStepType`
- `allowedScopes` enthalten nur bekannte `FeatureScope`
- Deaktivierte Optionen bleiben im Katalog, damit alte Projekte weiterhin lesbar bleiben

Frontend-validiert zusätzlich live:

- leere Labels markieren
- Duplikate pro Feldgruppe markieren
- Save-Button deaktivieren, solange harte Fehler bestehen

## Migration

1. Default-Katalog bildet den heutigen Inhalt von `CATEGORY_STEP_CONFIG` ab.
2. Backend liefert bei fehlender Persistenz automatisch diesen Default.
3. Frontend stellt `category-step-config.ts` auf Kompatibilitäts-/Fallback-Funktion um.
4. Wizard und Asset-Bundle-Coverage verwenden künftig die geladene Konfiguration.

Bestehende Projekt-Wizard-Daten bleiben unverändert, weil gespeicherte Werte Labels/Strings enthalten. Wenn eine Option später deaktiviert wird, bleibt sie in alten Projekten sichtbar, wird aber nicht mehr als neue Auswahl angeboten.

## Akzeptanzkriterien

1. Admin-Seite `/admin/wizard-options` ist über die App-Navigation erreichbar.
2. Admins können Kategorien und deren sichtbare Steps einsehen.
3. Admins können Optionen für `ARCHITECTURE.architecture`, `ARCHITECTURE.database`, `ARCHITECTURE.deployment`, `BACKEND.framework`, `BACKEND.apiStyle`, `BACKEND.auth`, `FRONTEND.framework`, `FRONTEND.uiLibrary`, `FRONTEND.styling`, `FRONTEND.theme` hinzufügen.
4. Neue Optionen erscheinen nach dem Speichern im Wizard ohne Codeänderung.
5. Neue Optionen erscheinen in der Asset-Bundle-Coverage-View als fehlende Triples, solange kein passendes Bundle existiert.
6. Optionen können deaktiviert werden und verschwinden danach aus neuen Wizard-Auswahlen.
7. Bereits gespeicherte Projekte mit alten/deaktivierten Werten bleiben lesbar.
8. Backend lehnt ungültige Kataloge mit 400 und verständlicher Fehlermeldung ab.
9. Reset auf Default stellt den heutigen `CATEGORY_STEP_CONFIG`-Inhalt wieder her.
10. Backend-Tests decken Katalog-Persistenz, Validierung und Reset ab.
11. Frontend zeigt Lade-, Fehler-, Dirty- und Speichern-erfolgreich-Zustände.

## Out of Scope

- Rollen-/Rechteverwaltung über echte Admin-Rollen, falls Auth noch keine Rollen kennt.
- Historie/Versionierung des Katalogs.
- Mehrsprachige Labels.
- Per-Projekt-Overrides.
- Automatisches Erzeugen von Asset-Bundles.
- Feingranulare Patch-API pro Option.

## Offene Designfragen

1. Soll die Admin-Seite unter `/admin/wizard-options` oder in die bestehende `/asset-bundles`-Admin-Umgebung integriert werden?
2. Soll Löschen wirklich erlaubt sein, oder reicht Deaktivieren für die erste Version?
3. Soll die erste Version globale Admin-Berechtigung voraussetzen oder vorerst wie die bestehende Asset-Bundle-Admin-UI zugänglich sein?
