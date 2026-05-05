---
name: bundle-system
description: Use when creating or modifying bundles in @engineering/frontend-core — covers BundleBuilderConfiguration, addFeatureBundleBuilderConfiguration, host vs feature bundle, version immutability, team-prefix, deploy path, fileReplacements.
---

# Bundle-System in `@engineering/frontend-core`

## Wann anwenden
Anwenden bei jeder Arbeit am Bundle-System: neues Bundle anlegen, Feature-Bundle umbauen, Bundle-Konfiguration im Host registrieren, Initializer oder Custom Elements zu einem Bundle hinzufuegen, Deploy. Diese Skill setzt voraus, dass `frontend-core-context` schon aktiv ist.

## BundleBuilderConfigurationBase-Hierarchie
Jedes Bundle definiert eine konkrete Klasse `BundleBuilderConfiguration extends BundleBuilderConfigurationBase`. Im Konstruktor wird das `bundleInfo` an `super(bundleInfo, false)` uebergeben; in `override create()` werden Feature-Konfigurationen ueber `this.addFeatureBundleBuilderConfiguration(...)` reingestapelt. `BundleBuilderConfigurationBase` kommt aus `@engineering/lib-infrastructure-core/lib/classes/bundle-builder-configuration-base`. Eine `IFeatureBundleBuilderConfiguration` ist ein Objekt mit `name` und `add(bundleConfiguration: IBundleBuilderConfiguration): void` — sie kapselt einen Aspekt (Logging, Routing, Identity, Layout, Video-MSE …).

## IBundleInfo.isHostBundle
Steuert, ob das Bundle als Host laeuft oder als Feature reingeladen wird.
- `true` — Host: vollstaendige App mit Routing, Layout, Navigation, eigener Identity.
- `false` — Feature: fokussiertes Modul, das im Host eingeklinkt wird; erbt Identity und Layout.

Konsequenzen: Host-Bundles sind gross, werden beim Start geladen und konfigurieren OAuth selbst. Feature-Bundles sind klein, werden zur Laufzeit nachgeladen und sollten Host-Settings nicht ueberschreiben.

## Versionsregel
Bundle-Versionen sind **unveraenderlich**. Sie duerfen weder ueberschrieben noch geloescht werden (ADR 0003). Konsequenz: jede Aenderung an einem Bundle fuehrt zu einer neuen Version. Beim Anheben der Version bitte semver beachten (Patch fuer Bugfix, Minor fuer additive Aenderungen, Major fuer Breaking — Major bedingt einen neuen Web-Component-Tag mit neuem Versions-Postfix).

## Team-Prefix
Bundle-Namen tragen einen Team-Prefix, z. B. `mit-facetimeline`, `epiphany-collection`. Der Prefix ist Pflicht und macht klar, welches Team Owner ist. **Nur Mitglieder der „mimions"-Guild duerfen Core-Bundles aktualisieren.** Web-Component-Tags innerhalb eines Bundles tragen zusaetzlich die Bundle-Version als Postfix (`<team>-<name>-webcomponent-<version>`).

## addBundleConfigurations
Im Host wird ein Feature-Bundle deklarativ in der `BundleBuilderConfiguration.create()` registriert. Der `LayoutService` laedt das Bundle bei Bedarf nach. Das Parameter-Objekt entspricht der `IBundleConfiguration` aus `@engineering/lib-infrastructure-core/lib/interfaces/i-layout-service-configuration`.

```ts
this.addBundleConfigurations([
  {
    id: 'engineering-external-mse-player-webcomponent',
    sourcePath:
      'https://<scripts-host-for-stage>/v3/bff/scripts?name=engineering-external-mse-player-webcomponent',
    customElements: ['engineering-external-mse-player-webcomponent'],
    // initializers?: ['engineeringPlayerMSEInitializerService'],
    // bindingParams?: { foo: 'bar' },
  },
]);
```

`id` muss eindeutig sein, `sourcePath` zeigt auf den deployten Bundle-Script, `customElements` listet alle Tags, die das Bundle registriert. `initializers` und `bindingParams` sind optional. Versionsschema und Stages stehen im `IBundleInfo` des Bundles selbst, nicht in der `IBundleConfiguration`-Uebergabe.

## Deploy-Pfad
Feature-Bundles werden als Standalone-Scripts auf einen Scripts-Server deployt. Der Host ist stage-spezifisch (siehe `projects/app-bundle-builder/deployment/deployment.ts`, z. B. `redaktionsportal-dev.netrtl.com`, `redaktionsportal-int.netrtl.com`, `redaktionsportal.netrtl.com`). Es existieren zwei URL-Formen:

```
# Path-Style (in einigen Konfigurationen genutzt)
https://<scripts-host-for-stage>/v3/bff/Scripts/<...>

# Query-Style (in `addBundleConfigurations.sourcePath` genutzt)
https://<scripts-host-for-stage>/v3/bff/scripts?name=<bundleName>
```

Der Pfad zeigt auf einen deployten Script — nicht zwingend auf `/main.js`. Build erfolgt ueber Gulp-Tasks (z. B. `gulp-build-element`, `gulp-build-videoMse`, allgemein `gulp build-app-bundle-builder --c <env>`); der Deploy via `npm run gulp-deploy-bundle`. Das per-Stage-Host-Mapping steht in `projects/app-bundle-builder/deployment/deployment.ts`.

## fileReplacements
Welche Bundle-Variante gebaut wird, entscheidet `angular.json` ueber `fileReplacements`. Dadurch tauscht der Build die Default-Dateien der `app-bundle-builder` gegen die spezifischen Bundle-Dateien aus.

```json
{
  "configurations": {
    "videoMse": {
      "fileReplacements": [
        {
          "replace": "projects/app-bundle-builder/src/default-bundle-info.ts",
          "with": "projects/bundler/bundle-video-mse/bundle-info.ts"
        },
        {
          "replace": "projects/app-bundle-builder/src/default-bundle-builder-configuration.ts",
          "with": "projects/bundler/bundle-video-mse/bundle-builder-configuration.ts"
        }
      ]
    }
  }
}
```

## Initializer & Custom-Element-Registrierung
Innerhalb einer Feature-Konfiguration werden Initializer und Custom Elements registriert.

```ts
bundleConfiguration.addBootInitializerNames(['engineeringPlayerMSEInitializerService']);
bundleConfiguration.addInitializers([
  {
    name: 'engineeringPlayerMSEInitializerService',
    type: InitializerType.BootInitializer,
    instance: new EngineeringPlayerMSEInitializerService(),
  },
]);

bundleConfiguration.addCustomElementRegistrations([
  async () => ({
    type: CustomElementRegistrationType.CustomElementAngularNgModule,
    module: (await import('@engineering/lib-player-ui-angular/video-mse-bundle')).VideoMSEBundleModule,
  }),
  async () => ({
    type: CustomElementRegistrationType.CustomElementAngularStandaloneComponent,
    component: (await import('../root-element')).RootElementComponent,
  }),
]);
```

`CustomElementRegistrationType` kennt `CustomElementAngularNgModule`, `CustomElementAngularStandaloneComponent` und `CustomElementPlain`. Jede registrierte Component braucht ein `public static componentTag = '<tag>'`.

## Environment-Parameter
Globale Konfigurationen werden ueber `bundleConfiguration.setGlobalEnvironmentParameter(key, value)` gesetzt. Die Keys sind streng typisierte Enums pro Library:
- `EnvironmentParameters` aus `lib-infrastructure-core` (Logging, Routing, Region-Service, Navigation, …).
- `LibPlayerEnvironmentParameters` aus `lib-player-core` (MSE-Service-Konfig, Player-Orchestration, …).
- `EngineeringUiCoreEnvironmentParameters` aus `lib-ui-core` (Shortcut-Optionen, …).

Bundle-spezifische Werte werden mit `setBundleSpecificEnvironmentParameter(key, value)` abgelegt — typisch fuer Identity-Federation-Konfigurationen.

## Verweise
- `applicationservice-mediator` — Naming-Regeln, States/Events, DI-Pattern, Anti-Patterns.
- `creating-webcomponents` — Tag-Naming mit Versions-Postfix, Shadow DOM, Component-Anatomie.
- ADR 0003 (`docs/adr/0003-bundles.md`) — Versionsregel und Team-Prefix.
