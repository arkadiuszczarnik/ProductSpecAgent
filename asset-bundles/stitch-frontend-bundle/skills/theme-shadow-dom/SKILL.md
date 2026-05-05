---
name: theme-shadow-dom
description: Use when building a domain web component that needs theming inside Shadow DOM — covers ThemeShadowDomService extension, CSS variable injection, font loading, theme-switch reaction, Style Dictionary pipeline from lib-ui-theme.
---

# Theming im Shadow DOM

## Wann anwenden
Anwenden, wenn ein Domain-Web-Component eigene Fonts, Farben oder Tokens innerhalb des Shadow Roots braucht und auf Theme-Switches reagieren muss. Diese Skill setzt voraus, dass `creating-webcomponents` und `frontend-core-context` bekannt sind.

## ThemeShadowDomService-Vererbung
Die Basisklasse `ThemeShadowDomService` wird aus `lib-infrastructure-core` exportiert (`projects/@engineering/lib-infrastructure-core/src/lib/services/theme-shadow-dom-service.ts`). `lib-ui-core` stellt die kanonische engineering-Subklasse `EngineeringUIThemeShadowDomService` bereit (`projects/@engineering/lib-ui-core/src/lib/services/engineering-theme-shadow-dom-service.ts`), die `ThemeShadowDomService` erweitert. Pro Domain-Library wird typischerweise ein eigener Service implementiert, der entweder direkt von `ThemeShadowDomService` oder von `EngineeringUIThemeShadowDomService` erbt. Override-Punkte sind die Methoden zum Injizieren von Fonts, CSS-Variablen und das Theme-Switch-Handling. Der Service bekommt den `IApplicationService` als Konstruktor-Argument und nutzt ihn fuer Logging und State-/Event-Subscriptions.

```ts
export class FacetimelineShadowDomService extends ThemeShadowDomService {
  constructor(applicationService: IApplicationService) {
    super(applicationService);
  }

  // Override-Punkte:
  // - getFontFaces()     -> die Liste der Fonts, die in den Shadow Root sollen
  // - getCssVariables()  -> Theme-Variablen pro Theme-Name
  // - onThemeChanged()   -> Reaktion auf den Theme-Switch
}
```

## CSS-Variablen
Der Service injiziert CSS-Variablen direkt in den Shadow Root des Components (nicht in den Light DOM des Hosts). Die **Single Source of Truth** sind die Tokens aus `projects/@engineering/lib-ui-theme/scss/design-tokens/design-tokens.scss`. Die Variablen werden dort definiert — Components **konsumieren** sie nur, sie werden im Component-CSS nicht neu deklariert. **Niemals** eigene Variablennamen mit Custom-Prefix (`--engineering-*`, `--myteam-*`, …) erfinden.

```css
/* Domain-Component-CSS innerhalb des Shadow Roots */
.button {
  background: var(--brand-1-color-button-primary-default);
  color: var(--brand-1-color-text-highlight-primary);
  padding: var(--spacing-xs) var(--spacing-s);
  font-family: var(--typography-font-family);
  font-size: var(--typography-button-fontsize);
  font-weight: var(--typography-button-fontweight);
  line-height: var(--typography-button-lineheight);
  border-radius: 4px;
}

.button--danger {
  background: var(--brand-1-color-button-danger-default);
  color: var(--brand-1-color-text-highlight-primary);
}

.icon {
  width: var(--icon-size-m);
  height: var(--icon-size-m);
  color: var(--brand-1-color-icon-primary);
}
```

Theme-Switch zwischen Light und Dark erfolgt nicht durch Umbenennen im Component, sondern durch die `DomainShadowDomService`-Subklasse, die das Mapping setzt (z. B. `--component-bg: var(--brand-2-color-background-primary)` bei dark theme). Der `ThemeShadowDomService` schreibt die Variablen entweder in ein `<style>`-Element im Shadow Root oder ueber `adoptedStyleSheets`.

### Token-Namespaces
Kurzueberblick — Details und vollstaendige Listen in `design-tokens.scss`:

| Namespace | Beschreibung | Beispiel |
|---|---|---|
| `--brand-1-*` | Light-Theme-Tokens (semantisch) | `--brand-1-color-background-primary` (`#ffffff`) |
| `--brand-2-*` | Dark-Theme-Tokens (semantisch) | `--brand-2-color-background-primary` (`#000000`) |
| `--brand-color-palette-*` | Rohpalette (primary 50..900, grey 0..900, red 1..4, yellow 1..4, green-1, blue) | `--brand-color-palette-primary-500` |
| `--breakpoint-*` | Media-Query-Stages `xs/sm/md/lg/xl` | `--breakpoint-min-md` |
| `--spacing-*` | `xxxs` (4px) … `xxl` (96px) | `--spacing-s` (24px) |
| `--icon-size-*` | `s` (16px), `m` (20px), `l` (24px) | `--icon-size-m` |
| `--typography-font-family` | Globaler Font-Stack | `'Source Sans Pro', 'Helvetica Neue', …` |
| `--typography-*` | Skalen: `headline-1..6`, `subtitle-1..2`, `body-1..2`, `button`, `caption`, `label`, `label-sm`, `overline` (jeweils `fontsize`/`fontweight`/`lineheight`) | `--typography-button-fontsize` |
| `--module-toolbar-*` | Modul-spezifische Werte (`size-xs..xl`, `height`) | `--module-toolbar-height` (56px) |
| `--z-index-*` | Tiers: `deepdive` (-99999) … `toast` (10000) | `--z-index-modal` |

## Font-Loading
Fonts werden im Shadow Root geladen — nicht (nur) im Host. Reihenfolge:
1. `@font-face`-Definitionen ueber das Theme-Stylesheet im Shadow Root deklarieren.
2. Fallback-Schriftarten in der `font-family`-Kette angeben (z. B. `'RTLSans', system-ui, sans-serif`).
3. `font-display: swap` setzen, damit der Text sofort sichtbar ist.

Wenn der Host die Fonts bereits vorlaedt (z. B. via `<link rel="preload">`), wird der Browser die Datei nur einmal holen. Liegen die Fonts im Host gar nicht, greift der Fallback aus der `font-family`-Kette — kein Layout-Shift, aber das gewuenschte Schriftbild fehlt.

## Theme-Switch-Reaktion
Der Theme-Switch wird ueber den Mediator als Event/State propagiert (z. B. ein `isDarkTheme`-State, vgl. `isDarkThemeBindingInitializerService` in `lib-infrastructure-core`). Der `DomainShadowDomService` subscribed auf diesen State, baut die CSS-Variablen fuer das neue Theme und schreibt sie in den Shadow Root. Wichtig: nicht das gesamte `<style>` neu erzeugen — nur die Variablen aktualisieren, damit der Browser nicht alle Styles neu rechnet.

## lib-ui-theme-Pipeline
Die Design-Tokens leben in `projects/@engineering/lib-ui-theme/`. Pipeline:

1. **Token-Quellen** — JSON/YAML-Tokens (Farben, Spacings, Typography, Radii) in der Library.
2. **Style Dictionary** — Build-Schritt, der die Tokens in mehrere Outputs uebersetzt: Sass-Variablen, CSS-Custom-Properties, ggf. TS-Konstanten.
3. **Sass** — Sass-Sources (Mixins, Maps) konsumieren die Tokens und erzeugen finale CSS-Bloecke.
4. **CSS-Variablen** — der finale Build-Output liegt in `projects/@engineering/lib-ui-theme/scss/design-tokens/design-tokens.scss` (Single Source of Truth) und wird vom `ThemeShadowDomService` in den Shadow Root injiziert.

Domain-Libraries konsumieren `lib-ui-theme` als Dependency. Sie mappen die globalen Tokens **nicht** auf eigene Variablennamen mit Custom-Prefix, sondern verwenden direkt die Namespaces aus `design-tokens.scss` (`--brand-1-*` / `--brand-2-*` / `--brand-color-palette-*` / `--spacing-*` / `--typography-*` / `--icon-size-*` / `--z-index-*`). So bleibt das Theme-Vokabular konsistent.

## Anti-Patterns
- Globales CSS in den Light DOM des Hosts kippen — verletzt die Shadow-DOM-Isolation und verursacht Style-Bleed.
- Hardcoded Hex-Werte (`#ff0000`) im Component-CSS, statt `var(--brand-1-color-text-error)`.
- Eigene Variablennamen mit Custom-Prefix erfinden (`--engineering-*`, `--myteam-*`, …) — Tokens kommen ausschliesslich aus `lib-ui-theme/scss/design-tokens/design-tokens.scss`.
- `:host` mit `!important`-Regeln ueberladen, um Host-Styles zu „uebersteuern" — der Shadow Root tut das ohnehin.
- Theme-Switch ueber `document.body.classList.toggle('dark')` statt ueber den Mediator-State.
- Fonts nur im Host laden und vergessen, dass der Shadow Root sie auch braucht.
- Eigene Token-Definitionen in der Domain-Library erfinden, statt `lib-ui-theme` zu erweitern.

## Verweise
- ADR 0002 (`docs/adr/0002-modular-ui-components.md`) — Pflicht zum outer Shadow DOM und zum `DomainShadowDomService`.
- `creating-webcomponents` — Component-Anatomie, `componentTag`, `@Input applicationservice`.
