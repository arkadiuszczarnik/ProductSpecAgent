---
description: Scaffold a new shared web component with outer Shadow DOM, theme service, ApplicationService input, and registration.
argument-hint: <component-name>
---

Lege einen neuen Web Component `$ARGUMENTS` an.

Delegiere an `frontend-builder`:

> Erzeuge einen Web Component mit Namen `$ARGUMENTS`.
> - Angular-Component + Shadow-DOM-Service + `createCustomElement`-Registrierung.
> - Naming: `<team>-$ARGUMENTS-webcomponent-<version>` (Team-Prefix aus `CLAUDE.md` oder Frage).
> - Test-Skeleton (Karma+Jasmine).
> - Verwende Skills `creating-webcomponents`, `theme-shadow-dom`, `applicationservice-mediator`.
> - Lies vorab eine reale Referenz: `projects/@engineering/lib-ui-core-angular/progress-bar/progress-bar.component.ts` (einfach) oder `projects/@engineering/lib-player-ui-angular/video-progress-bar/video-progress-bar.component.ts` (komplex). Keine Patterns erfinden.
> - Styles konsumieren echte Tokens aus `lib-ui-theme` (`--brand-1-*`, `--spacing-*`, `--typography-*` …). Keine erfundenen Variablen — siehe Skill `theme-shadow-dom`.
> - Final-Report.
