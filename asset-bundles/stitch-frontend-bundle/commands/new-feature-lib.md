---
description: Scaffold a new feature library plus an initial web component in one go.
argument-hint: <lib-name>
---

Lege eine neue Feature-Library `lib-$ARGUMENTS` an, inklusive eines initialen Web Components.

Delegiere an `frontend-builder`:

> 1. Library `lib-$ARGUMENTS` nach `creating-angular-libraries`-Konventionen.
> 2. Initialer Web Component im Library, Tag-Naming nach Skill `creating-webcomponents` (`<team>-$ARGUMENTS-main-webcomponent-<version>`). Komponentenklassenname kann z. B. `${ArgumentsCamelCase}MainComponent` sein. Skills `creating-webcomponents`, `theme-shadow-dom`, `applicationservice-mediator` anwenden.
> 3. Public-API-Export für die Component-Configuration-Interfaces.
> 4. Final-Report.
