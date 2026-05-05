---
name: frontend-core-context
description: Use whenever working in the @engineering/frontend-core repository — loads ADR-1/2/3 short form, workspace map, team-prefix rule, and pointers to detail skills (bundle-system, applicationservice-mediator, theme-shadow-dom).
---

# Eintritts-Skill `@engineering/frontend-core`

## Wann anwenden
Anwenden bei jeder Arbeit im Repository `@engineering/frontend-core`. Diese Skill ist der Eintrittspunkt: Sie liefert die ADR-Kurzfassung, eine Workspace-Karte, die Team-Prefix-Regel und Verweise auf die spezialisierten Detail-Skills.

## ADR-Kurzfassung

**ADR 0001 — Modular TypeScript:**
- Keine CommonJS-Libraries.
- Kommunikation laeuft ausschliesslich ueber den Mediator `IApplicationService` (Events und States).
- Kommunikations-Models sind framework-agnostisch — kein RxJS, keine Angular-Types, keine `Observable`s.
- Nur additive Interface-Aenderungen (neue Felder optional, keine Renames, keine Removals).
- Keine Globals — IIFEs verwenden.
- StateIds, Event-Namen und Service-Namen tragen den Team-Prefix.

**ADR 0002 — Modular UI Components:**
- Geteilte UI-Components als Web Components mit outer Shadow DOM.
- I/O zwischen Component und Host nur ueber den Mediator (`@Input applicationservice`).
- Web-Component-Registrierung via `LayoutService.importWebComponents` (Achtung: grosses `C` in `WebComponents`).
- Pro UI-Lib genau ein Bundle; Components tragen die Bundle-Version als Postfix (`engineering-ui-dropdown-webcomponent-1.0.3`).
- Domain-Teams implementieren einen `DomainShadowDomService` (extends `ThemeShadowDomService`) fuer Fonts, CSS-Variablen und Theme-Reaktion.

**ADR 0003 — Bundles:**
- Bundle ist das Atom der Auslieferung; Versionen sind unveraenderlich (kein Override, kein Delete).
- Team-Prefix Pflicht (`mit-facetimeline`, `epiphany-collection`, …).
- Core-Bundles duerfen ausschliesslich von Mitgliedern der „mimions"-Guild aktualisiert werden.

## Workspace-Karte
- `lib-infrastructure-core` — Mediator, DI, Events/States, Logging, Initializers, Layout-Service, Custom-Element-Registry. Fundament.
- `lib-infrastructure-core-angular` — Angular-Adapter: Routing, Zoneless, Animations, Error-Handling, ChangeDetector-Bridge, Bootstrap-Helper.
- `lib-infrastructure-core-signalr` — Realtime-Transport ueber `@microsoft/signalr` (mit MessagePack), bridged auf den Mediator.
- `lib-ui-core` — Framework-agnostische UI-Primitives, Interfaces, Enums, Shortcut-Konfig, Theme/Shadow-DOM-Services.
- `lib-ui-core-angular` — Angular-Implementierungen der UI-Components (Buttons, Chips, Dropdowns) als Web Components mit Shadow DOM.
- `lib-ui-theme` — Design-Tokens, Sass-Quellen, Style-Dictionary-Pipeline.
- `lib-data-core` — Data-Layer: API-Service, HTTP-Interceptor, Data-Context, Change-Tracking, Client-Service.
- `lib-identity-oauth` — OAuth/OIDC, Rollen-/Team-Modell, Auth-Guard, Login/Logout-Directive, Mock-Identity.
- `lib-player-core` — Video/MSE-Core: Orchestration, Streaming-Konfig, IndexedDB-Caching, Initializer.
- `lib-player-ui-angular` — Angular-UI fuer den Video-Player inkl. `VideoMSEBundleModule`-Web-Component.
- `lib-player-e2e-playwright` — E2E-Tests fuer den Player.
- `lib-e2e-utils-playwright` — Geteilte Playwright-Helper.
- `app-demo-host` — Referenz-Host mit Theme, Navigation, i18n (de/en) und registrierten Web Components.
- `app-element-host` — Minimaler Host fuer Element-Bundle-Tests.
- `projects/bundler/*` — Feature-Bundle-Definitionen (`bundle-video-mse`, `bundle-layout`, `bundle-configuration`).
- `projects/stitch-catalog` — Reference-App fuer das Bundle-Builder-Pattern.

## Team-Prefix-Regel
Alle generierten Names — StateIds, Event-Namen, Service-Namen, Bundle-Namen — tragen den Team-Prefix. Beispiele: `engineering.ui.dropdown.opened`, `mit.facetimeline.state.selectedTimespan`, `epiphany-collection`. Der konkrete Team-Prefix dieses Projekts wird in der `CLAUDE.md` des Repositories im Slot `<team-prefix>` eingetragen — Claude liest ihn von dort und verwendet ihn fuer alle generierten Names.

## Wann welche Detail-Skill ziehen

| Du arbeitest an X | aktiviere Skill Y |
|---|---|
| Neuem Bundle, Bundle-Konfiguration, `addFeatureBundleBuilderConfiguration` | `bundle-system` |
| Cross-Bundle-Kommunikation, Events/States, DI, Naming | `applicationservice-mediator` |
| Geteiltem Web Component, Custom Element, Shadow DOM, `componentTag` | `creating-webcomponents` |
| Domain-Theming, CSS-Variablen im Shadow Root, Theme-Switch | `theme-shadow-dom` |
| Architekturentscheidungen Host vs. Feature, Bundle vs. NPM | `angular-mfe-architecture` |
| Neuer Angular-Library (`lib-*`) im Workspace | `creating-angular-libraries` |

## Wichtige Pfade
- `docs/adr/` — Architecture Decision Records (0001 Modular TypeScript, 0002 Modular UI Components, 0003 Bundles).
- `docs/implementation/` — Konfigurations- und Integrations-Guides (Angular-App, Feature-Bundle, Custom Element).
- `docs/library-overview.md` — Framework-Ueberblick mit Workspace-Karte.
- `gulpfile.js` — Build-/Test-/Deploy-Pipelines.
- `angular.json` — Multi-Project-Workspace-Definition mit `fileReplacements`.

## Build-/Test-Commands
- `npm run gulp-build-libs` — alle Libraries bauen.
- `npm run serve-demo-host` — Referenz-Host auf `http://localhost:4600`.
- `npm run gulp-test-libs` — Unit-Tests aller Libs.
- `npm run e2e` — Playwright-E2E lokal.
- `npm run gulp-deploy-bundle` — Feature-Bundle deployen (via `gulp-build-element` / `gulp-build-videoMse` vorab).
