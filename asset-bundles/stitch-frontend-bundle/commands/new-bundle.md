---
description: Scaffold a new feature bundle in projects/bundler/ following frontend-core BundleBuilderConfiguration conventions.
argument-hint: <bundle-name>
---

Lege ein neues Feature-Bundle `$ARGUMENTS` an.

Delegiere an `frontend-builder`:

> Erzeuge ein Feature-Bundle mit dem Namen `$ARGUMENTS` im Layout `projects/bundler/$ARGUMENTS/`.
> - `BundleBuilderConfiguration`, `webpack.config.js`, `angular.json`-Eintrag, `fileReplacements`.
> - `IBundleInfo.isHostBundle = false`.
> - Verwende den Skill `bundle-system`.
> - Wenn Team-Prefix nicht klar ist, frag nach.
> - Final-Report.
