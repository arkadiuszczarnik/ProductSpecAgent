# Wizard-Options-Admin — Design Spec

**Datum:** 2026-05-09
**Bezug:** Feature 46. Macht die Wizard-Auswahloptionen fuer Architektur, Datenbanken, Backend-Frameworks, Frontend-Frameworks und verwandte Felder ueber eine Admin-Seite pflegbar.
**Status:** Design — Review ausstehend

## Kontext

Die Wizard-Optionen sind heute im Frontend hart codiert:

- Datei: `frontend/src/lib/category-step-config.ts`
- Kategorien: `SaaS`, `Mobile App`, `CLI Tool`, `Library`, `Desktop App`, `API`
- Felder: unter anderem `ARCHITECTURE.architecture`, `ARCHITECTURE.database`, `BACKEND.framework`, `FRONTEND.framework`

Diese Datei ist aktuell gleichzeitig:

- Quelle fuer Wizard-Step-Sichtbarkeit und Feldoptionen
- Quelle fuer erlaubte Feature-Scopes je Kategorie
- Quelle fuer die Asset-Bundle-Coverage-View, die fehlende Bundle-Triples ableitet

Das ist fuer Produktpflege zu starr. Eine neue Sprache, Datenbank oder Architektur-Option braucht heute Codeaenderung, Build und Deployment.

## Ziel

Eine Admin-Seite `/admin/wizard-options`, auf der ein Admin/Kurator Wizard-Optionen global pflegen kann:

- Kategorien anzeigen und bearbeiten
- sichtbare Wizard-Steps pro Kategorie pflegen
- erlaubte Feature-Scopes pro Kategorie pflegen
- Feldgruppen fuer `ARCHITECTURE`, `BACKEND` und `FRONTEND` pflegen
- Optionen hinzufuegen, umbenennen, deaktivieren und sortieren
- Katalog speichern und auf Default zuruecksetzen

Wizard-Forms und Asset-Bundle-Coverage lesen denselben Katalog. Neue Optionen erscheinen dadurch ohne Codeaenderung im Wizard und in der Missing-Bundle-Ansicht.

## Nicht-Ziele

- Keine Rollen-/Rechteverwaltung in dieser Iteration. Falls es noch keine Admin-Rolle gibt, folgt die Seite dem gleichen Zugriffsmuster wie die bestehende Asset-Bundle-Admin-UI.
- Keine Katalog-Historie oder Versionierung.
- Keine mehrsprachigen Labels.
- Keine per-Projekt-Overrides.
- Keine automatische Asset-Bundle-Erzeugung.
- Keine feingranularen Patch-Endpunkte pro Option. Ein vollstaendiger `PUT` des Katalogs reicht fuer die erste Version.

## Recherche-Notizen

Context7 bestaetigt fuer Next.js App Router die bestehende Projektlinie: Daten koennen in App-Router-Seiten geladen und an Client-Komponenten weitergereicht werden; fuer interaktive Admin-Formulare ist eine Client-Komponente mit REST-API-Aufrufen passend.

Context7 bestaetigt fuer Spring Boot die bestehende Controller/Test-Linie: `@RestController` fuer CRUD-artige Endpoints und MockMvc-/SpringBootTest-basierte Tests passen zur vorhandenen Codebasis.

## Architektur

### Datenfluss heute

```text
frontend/src/lib/category-step-config.ts
        |
        +--> Wizard-Step-Sichtbarkeit
        +--> Wizard-Feldoptionen
        +--> allowedScopes
        +--> Asset-Bundle Missing-Triples
```

### Datenfluss Ziel

```text
Default Wizard Option Catalog
        |
        v
WizardOptionCatalogService
        |
        +--> GET /api/v1/wizard-options
        |       |
        |       +--> Wizard UI
        |       +--> Asset-Bundle-Coverage
        |
        +--> GET/PUT/POST /api/v1/admin/wizard-options
                |
                +--> Admin UI /admin/wizard-options
```

Der Backend-Service liefert einen Default-Katalog, wenn noch nichts gespeichert wurde. Dadurch bleibt die App direkt startfaehig und der bestehende hart codierte Katalog kann schrittweise zurueckgebaut werden.

## Backend-Design

### Domain-Modell

Neue Datei: `backend/src/main/kotlin/com/agentwork/productspecagent/domain/WizardOptionCatalog.kt`

```kotlin
@Serializable
data class WizardOptionCatalog(
    val version: Int,
    val categories: List<WizardCategoryOptions>,
    val updatedAt: String,
)

@Serializable
data class WizardCategoryOptions(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
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
    val enabled: Boolean = true,
    val sortOrder: Int,
)
```

`id` ist ein stabiler, slug-artiger technischer Wert. `label` ist der angezeigte Wert. Fuer die erste Version duerfen beide beim Erzeugen aus Defaults identisch-slugifiziert sein.

### Persistenz

Neue Storage-Methoden entweder in einer eigenen Klasse oder ueber `ProjectStorage`/`ObjectStore`:

```text
config/wizard-options/catalog.json
```

Das ist global, nicht projektbezogen. Es passt damit zu Asset-Bundles und Agent-Modellen, die ebenfalls globale Admin-Daten sind.

### Default-Katalog

Neue Backend-Quelle: `WizardOptionDefaults`.

Sie bildet den aktuellen Inhalt von `frontend/src/lib/category-step-config.ts` 1:1 ab:

- gleiche Kategorien
- gleiche `visibleSteps`
- gleiche `allowedScopes`
- gleiche Feldgruppen und Optionen

Die Default-Quelle muss im Backend liegen, damit `GET /api/v1/wizard-options` ohne Frontend-Konstante funktioniert. Das Frontend behaelt nur noch einen minimalen Fallback fuer Fehlerfaelle.

### Service

Neue Klasse: `WizardOptionCatalogService`

Aufgaben:

- `getCatalog()`: gespeicherten Katalog laden oder Default liefern
- `saveCatalog(catalog)`: validieren, `updatedAt` setzen, speichern
- `resetCatalog()`: Default speichern und zurueckgeben
- `validate(catalog)`: harte fachliche Fehler ablehnen

Validierungsregeln:

- Kategorie-IDs sind nicht leer und eindeutig.
- Kategorie-Labels sind nicht leer.
- `visibleSteps` enthalten nur bekannte `FlowStepType`.
- `allowedScopes` enthalten nur bekannte `FeatureScope`.
- Pro Kategorie und Step ist `fieldKey` eindeutig.
- Pro Feldgruppe sind Options-IDs eindeutig.
- Option-Labels sind nicht leer.
- `sortOrder` muss innerhalb einer Feldgruppe eindeutig sein.

Deaktivierte Optionen werden nicht geloescht. Sie bleiben im Katalog, damit alte Projekte weiter lesbar bleiben.

### REST API

Neue Controller:

```text
GET  /api/v1/wizard-options
GET  /api/v1/admin/wizard-options
PUT  /api/v1/admin/wizard-options
POST /api/v1/admin/wizard-options/reset
```

`GET /api/v1/wizard-options` ist die read-only API fuer normale Wizard-Nutzung.

`/api/v1/admin/wizard-options` ist fuer die Admin-Seite. Solange Rollen fehlen, wird sie wie bestehende Admin-Bereiche behandelt. Wenn spaeter Rollen eingefuehrt werden, ist der Pfad bereits sauber getrennt.

Fehler:

- 400 bei ungueltigem Katalog mit klarer Fehlermeldung.
- 401/403 folgt der bestehenden Security-Konfiguration.

## Frontend-Design

### API-Typen

Erweiterung in `frontend/src/lib/api.ts`:

```ts
export interface WizardOptionCatalog {
  version: number;
  categories: WizardCategoryOptions[];
  updatedAt: string;
}

export interface WizardCategoryOptions {
  id: string;
  label: string;
  enabled: boolean;
  visibleSteps: StepType[];
  allowedScopes: FeatureScope[];
  fieldOptions: Partial<Record<StepType, WizardFieldOptionGroup[]>>;
}

export interface WizardFieldOptionGroup {
  fieldKey: string;
  label: string;
  options: WizardSelectableOption[];
}

export interface WizardSelectableOption {
  id: string;
  label: string;
  enabled: boolean;
  sortOrder: number;
}
```

Neue API-Funktionen:

- `getWizardOptions()`
- `getAdminWizardOptions()`
- `saveAdminWizardOptions(catalog)`
- `resetAdminWizardOptions()`

### Store

Neue Datei: `frontend/src/lib/stores/wizard-options-store.ts`

Zustand:

- `catalog`
- `loading`
- `saving`
- `error`
- `dirty`
- `selectedCategoryId`
- `selectedStep`

Aktionen:

- `load()`
- `save()`
- `resetToDefault()`
- `selectCategory(id)`
- `updateCategory(...)`
- `addOption(categoryId, step, fieldKey, label)`
- `updateOption(...)`
- `disableOption(...)`
- `moveOption(...)`

Der normale Wizard kann denselben Store oder einen read-only Hook nutzen. Wichtig ist, dass Wizard-Forms nicht direkt den Admin-Store editieren.

### Admin-Seite

Neue Route:

```text
frontend/src/app/admin/wizard-options/page.tsx
```

Neue Komponenten:

- `components/wizard-options/WizardOptionsAdminPage.tsx`
- `components/wizard-options/CategoryList.tsx`
- `components/wizard-options/CategoryEditor.tsx`
- `components/wizard-options/FieldOptionEditor.tsx`
- `components/wizard-options/OptionRow.tsx`

Layout:

```text
+-------------------------------------------------------------+
| Wizard Options                                      Save     |
+----------------------+----------------------+---------------+
| Kategorien           | Kategorie            | Optionen      |
| - SaaS               | visibleSteps         | ARCHITECTURE  |
| - Mobile App         | allowedScopes        | BACKEND       |
| - API                | enabled              | FRONTEND      |
+----------------------+----------------------+---------------+
```

Das ist bewusst arbeitsorientiert und dicht, aehnlich einer Admin-Konsole. Keine Marketing- oder Wizard-Erklaerseite.

### Integration in bestehende Wizard-Forms

`frontend/src/lib/category-step-config.ts` wird nicht sofort geloescht. Es wird in zwei Schritten umgebaut:

1. Default-Daten bleiben dort oder werden in ein gemeinsames Modul verschoben.
2. Runtime-Funktionen `getVisibleSteps`, `getFieldOptions`, `getAllowedScopes` werden so erweitert, dass sie bevorzugt den geladenen Katalog nutzen.

In der Implementierung sollte kein grosses Refactoring der Wizard-Forms passieren. Ziel ist ein kleiner Adapter:

```ts
getFieldOptionsFromCatalog(catalog, category, step)
```

Die bestehenden Step-Forms koennen weiter mit `Record<string, string[]>` arbeiten. Der Katalog wird dafuer in die alte Shape transformiert.

### Integration Asset-Bundle-Coverage

`possible-triples.ts` nutzt heute `CATEGORY_STEP_CONFIG`. Es soll auf den geladenen Katalog umgestellt werden:

```ts
getAllPossibleTriples(catalog: WizardOptionCatalog): BundleTriple[]
```

Damit erzeugt eine neu hinzugefuegte Datenbank oder ein neues Backend-Framework automatisch einen fehlenden Bundle-Eintrag.

## UX-Verhalten

### Hinzufuegen

Ein Admin waehlt Kategorie, Step und Feldgruppe aus und klickt "Option hinzufuegen".

Minimalfelder:

- Label
- Enabled

`id` wird aus Label slugifiziert, kann bei Konflikt aber manuell angepasst werden.

### Deaktivieren statt Loeschen

Die erste Version setzt auf Deaktivieren. Loeschen ist riskanter, weil bestehende Projekte gespeicherte Werte enthalten koennen. Ein Delete-Button kann spaeter folgen, wenn Nutzungspruefung implementiert ist.

### Sortierung

Sortierung erfolgt ueber Up/Down-Buttons. Drag & Drop ist fuer die erste Version nicht noetig.

### Reset

Reset auf Default braucht einen Confirm-Dialog:

> "Alle gespeicherten Wizard-Optionen werden durch die Standardkonfiguration ersetzt."

## Testing

Backend:

- `WizardOptionCatalogServiceTest`
  - liefert Default, wenn nichts gespeichert ist
  - speichert und laedt Katalog
  - reset stellt Default wieder her
  - validiert doppelte Kategorie-ID
  - validiert doppelte Option-ID pro Feldgruppe
  - validiert leere Labels

- `WizardOptionControllerTest`
  - `GET /api/v1/wizard-options`
  - `GET /api/v1/admin/wizard-options`
  - `PUT /api/v1/admin/wizard-options`
  - `POST /api/v1/admin/wizard-options/reset`
  - 400 bei ungueltigem Katalog

Frontend:

- Es gibt aktuell keinen stabilen Frontend-Unit-Test-Runner.
- Verifikation ueber `npm run build` oder mindestens `npm run lint`, soweit bestehende Lint-Fehler es erlauben.
- Browser-Smoke:
  - Admin-Seite laedt Default-Katalog
  - Option hinzufuegen und speichern
  - Wizard zeigt neue Option
  - Asset-Bundle-Missing-Liste zeigt neues Triple
  - Reset entfernt neue Option wieder

## Migrationsplan

1. Backend-Default-Katalog aus heutiger `CATEGORY_STEP_CONFIG` modellieren.
2. API + Service + Storage bauen.
3. Frontend-API und Store bauen.
4. Admin-Seite bauen.
5. Wizard-Forms auf Katalogdaten umstellen.
6. Asset-Bundle-Coverage auf Katalogdaten umstellen.
7. `CATEGORY_STEP_CONFIG` als Runtime-Quelle entfernen oder auf reinen Fallback reduzieren.

## Risiken

### Backend- und Frontend-Default driften auseinander

Mit dem neuen Backend-Default darf die Frontend-Konstante nicht dauerhaft zweite Wahrheit bleiben. Der Plan muss daher explizit einen Schritt enthalten, der Wizard und Coverage auf den API-Katalog umstellt.

### Alte Projekte mit deaktivierten Werten

Deaktivierte Optionen bleiben im Katalog. Wizard-Anzeigen duerfen gespeicherte Altwerte nicht als "ungueltig" behandeln.

### Admin-Berechtigung

Wenn echte Rollen fehlen, ist der Endpoint technisch nicht strikt Admin-only. Das ist konsistent mit bestehenden Admin-Bereichen, muss aber in einer spaeteren Auth/RBAC-Erweiterung nachgezogen werden.

## Akzeptanzkriterien

1. `/admin/wizard-options` ist in der App erreichbar.
2. Die Seite laedt den aktuellen Katalog aus dem Backend.
3. Admins koennen eine neue Option zu `BACKEND.framework` hinzufuegen und speichern.
4. Die neue Option erscheint im Wizard fuer passende Kategorien ohne Codeaenderung.
5. Die neue Option erscheint in der Asset-Bundle-Coverage als fehlendes Triple.
6. Optionen koennen deaktiviert werden und verschwinden aus neuen Wizard-Auswahlen.
7. Bestehende Projekte mit alten/deaktivierten Werten bleiben lesbar.
8. Reset stellt den heutigen Default-Katalog wieder her.
9. Backend lehnt ungueltige Kataloge mit 400 ab.
10. Backend-Tests fuer Service und Controller sind gruen.
11. Keine bestehende Asset-Bundle-Admin-Funktion wird regressiert.

## Getroffene Entscheidungen

1. Die Route wird fuer die erste Version als `/admin/wizard-options` festgelegt.
2. Loeschen ist fuer die erste Version nicht vorgesehen; Deaktivieren reicht.
3. Rollenpruefung wird nicht neu erfunden. Die Seite folgt der vorhandenen Security-Struktur.
