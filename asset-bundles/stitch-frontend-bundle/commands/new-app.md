---
description: Scaffold a new Angular host application under projects/@engineering/app-* — minimal Angular 20 host with applicationServiceFactory boot, environment-base, AppModule + standalone AppComponent, and a complete angular.json entry. Verified to build cleanly with `ng build`.
argument-hint: <app-name>
---

Lege eine neue Host-App `app-$ARGUMENTS` im Workspace an.

Delegiere an `frontend-builder`:

> Erzeuge eine neue Host-App mit dem Namen `app-$ARGUMENTS` unter `projects/@engineering/app-$ARGUMENTS/`. Die App muss nach Anlage über `ng build @engineering/app-$ARGUMENTS --configuration=development` UND `--configuration=production` ohne Fehler bauen.
>
> Verwende die Skills `frontend-core-context`, `applicationservice-mediator`, `creating-angular-libraries` (für Workspace-Konventionen). Die Patterns stammen aus dem verifizierten Test-Scaffold gegen `app-demo-host` als Referenz.
>
> Wenn der gewünschte Scope nicht `@engineering` ist, vorher nachfragen.
>
> ## Pflicht-Files (alle anlegen)
>
> ### Build-Konfigurationen
>
> 1. `projects/@engineering/app-$ARGUMENTS/tsconfig.app.json`
>    ```json
>    {
>        "extends": "../../../tsconfig.json",
>        "compilerOptions": {
>            "outDir": "../../out-tsc/app",
>            "types": []
>        },
>        "files": ["src/main.ts", "src/polyfills.ts"],
>        "include": ["src/**/*.d.ts"]
>    }
>    ```
>
> 2. `projects/@engineering/app-$ARGUMENTS/tsconfig.spec.json`
>    ```json
>    {
>        "extends": "../../../tsconfig.json",
>        "compilerOptions": {
>            "outDir": "../../../out-tsc/spec",
>            "types": ["jasmine"]
>        },
>        "files": ["src/test.ts", "src/polyfills.ts"],
>        "include": ["src/**/*.spec.ts", "src/**/*.d.ts"]
>    }
>    ```
>
> 3. `projects/@engineering/app-$ARGUMENTS/karma.conf.js` — übernimm das Schema aus `projects/@engineering/app-demo-host/karma.conf.js`, ersetze nur den `coverageReporter.dir`-Pfad auf `../../coverage/@engineering/app-$ARGUMENTS`.
>
> ### Entry-Points
>
> 4. `src/polyfills.ts` — `import 'document-register-element'; import 'zone.js';`
>
> 5. `src/test.ts` — Standard-Karma-Setup mit `getTestBed().initTestEnvironment(...)`. Schema aus `app-demo-host/src/test.ts` übernehmen.
>
> 6. `src/index.html` — minimal: `<!doctype html><html lang="de"><head><meta charset="utf-8"><title>app-$ARGUMENTS</title><base href="/"><link rel="icon" type="image/x-icon" href="favicon.ico"></head><body><app></app></body></html>`. Splash-Screen weglassen (kommt erst mit echtem Layout).
>
> 7. `src/styles.scss` — initial leer mit `html { font-size: 100%; }`. Theme-Imports erst hinzufügen, wenn Layout-Webcomponents gebraucht werden.
>
> 8. `src/main.ts`
>    ```ts
>    import { enableProdMode } from '@angular/core';
>    import { platformBrowser } from '@angular/platform-browser';
>    import { applicationServiceFactory } from '@engineering/lib-infrastructure-core-angular/application-service-factory';
>    import { buildInfo } from './build-info';
>    import { environment } from './environments/environment';
>
>    applicationServiceFactory.bootApplicationService(environment);
>
>    if (environment.isProduction) {
>        enableProdMode();
>    }
>
>    console.log('Build-Infos', buildInfo);
>
>    import('./app/app.module').then((jsModule) => {
>        platformBrowser()
>            .bootstrapModule(jsModule.AppModule)
>            .catch((err) => console.error(err));
>    });
>    ```
>
> 9. `src/build-info.ts`
>    ```ts
>    import { IBuildInfo } from '@engineering/lib-infrastructure-core/lib/interfaces/i-build-info';
>
>    export const buildInfo: IBuildInfo = {
>        branchName: 'local',
>        branchSHA: '0000000000000000000000000000000000000000',
>        branchLastCommitTime: 'unknown',
>        branchLastCommitAuthor: 'unknown',
>        buildTime: new Date().toISOString(),
>        commitMessage: 'scaffolded by /new-app',
>    };
>    ```
>
> ### App-Module + Standalone Component
>
> 10. `src/app/app.component.ts` — Standalone-Component (Default in Angular 20). Selector `app`, minimaler Template.
>     ```ts
>     import { ChangeDetectionStrategy, Component, ViewEncapsulation } from '@angular/core';
>     import { IApplicationService } from '@engineering/lib-infrastructure-core/lib/interfaces/i-application-service';
>     import { applicationServiceProvider } from '@engineering/lib-infrastructure-core/lib/providers/application-service-provider';
>
>     @Component({
>         selector: 'app',
>         template: `
>             <main>
>                 <h1>{{ title }}</h1>
>                 <p>Scaffolded host shell. Ersetze diese Komponente durch deinen Layout-Webcomponent.</p>
>             </main>
>         `,
>         encapsulation: ViewEncapsulation.None,
>         changeDetection: ChangeDetectionStrategy.Default,
>     })
>     export class AppComponent {
>         title = '@engineering/app-$ARGUMENTS';
>         applicationService: IApplicationService = applicationServiceProvider.currentApplicationService;
>     }
>     ```
>
> 11. `src/app/webcomponents.module.ts` — leeres NgModule, Slot für spätere Web-Component-Imports.
>     ```ts
>     import { NgModule } from '@angular/core';
>
>     @NgModule({
>         declarations: [],
>         imports: [],
>         exports: [],
>     })
>     export class WebcomponentsModule {}
>     ```
>
> 12. `src/app/app.module.ts` — **WICHTIG:** Standalone-Component `AppComponent` gehört in `imports`, NICHT in `declarations` (Angular 20).
>     ```ts
>     import { CUSTOM_ELEMENTS_SCHEMA, ErrorHandler, NgModule } from '@angular/core';
>     import { BrowserModule } from '@angular/platform-browser';
>     import { GlobalAngularErrorHandler } from '@engineering/lib-infrastructure-core-angular/global-angular-error-handler-service';
>     import { AppComponent } from './app.component';
>     import { WebcomponentsModule } from './webcomponents.module';
>
>     @NgModule({
>         declarations: [],
>         imports: [BrowserModule, AppComponent, WebcomponentsModule],
>         providers: [
>             {
>                 provide: ErrorHandler,
>                 useClass: GlobalAngularErrorHandler,
>             },
>         ],
>         schemas: [CUSTOM_ELEMENTS_SCHEMA],
>         bootstrap: [AppComponent],
>     })
>     export class AppModule {}
>     ```
>
> ### Environments
>
> 13. `src/environments/environment-base.ts`
>     ```ts
>     import { environmentBase as coreEnvironmentBase } from '@engineering/lib-infrastructure-core/lib/classes/environment-base';
>     import { EnvironmentParameters as CoreEnvironmentParameters } from '@engineering/lib-infrastructure-core/lib/enums/environment-parameters-enum';
>     import { IEnvironment } from '@engineering/lib-infrastructure-core/lib/interfaces/i-environment';
>     import { buildInfo } from '../build-info';
>
>     export const environmentBase: IEnvironment = coreEnvironmentBase;
>
>     environmentBase.isProduction = false;
>     environmentBase.name = 'base';
>
>     environmentBase.parameters.set(CoreEnvironmentParameters.BuildInfo, buildInfo);
>     ```
>
> 14. `src/environments/environment.ts`
>     ```ts
>     import { IEnvironment } from '@engineering/lib-infrastructure-core/lib/interfaces/i-environment';
>     import { environmentBase } from './environment-base';
>
>     export const environment: IEnvironment = environmentBase;
>
>     environment.isProduction = false;
>     environment.name = 'dev';
>     ```
>
> 15. `src/environments/environment.prod.ts`
>     ```ts
>     import { IEnvironment } from '@engineering/lib-infrastructure-core/lib/interfaces/i-environment';
>     import { environmentBase } from './environment-base';
>
>     export const environment: IEnvironment = environmentBase;
>
>     environment.isProduction = true;
>     environment.name = 'prod';
>     ```
>
> ### Assets
>
> 16. `src/assets/.gitkeep` — leer.
>
> ## Pflicht-Eintrag in `angular.json`
>
> Füge unter `projects` einen neuen Block `"@engineering/app-$ARGUMENTS"` ein. Schema (analog zu `@engineering/app-demo-host`, mit angepasstem Namen). **Wichtig:** `assets` enthält NUR `.../src/assets`, KEIN `favicon.ico` (sonst bricht der Build, weil das File nicht existiert). `defaultConfiguration` für `build` ist `development` (für Smoke-Test).
>
> Vollständiger Block:
>
> ```json
> "@engineering/app-$ARGUMENTS": {
>     "projectType": "application",
>     "schematics": {
>         "@schematics/angular:component": { "style": "scss" }
>     },
>     "root": "projects/@engineering/app-$ARGUMENTS",
>     "sourceRoot": "projects/@engineering/app-$ARGUMENTS/src",
>     "prefix": "app",
>     "architect": {
>         "build": {
>             "builder": "@angular/build:application",
>             "options": {
>                 "outputPath": { "base": "dist/@engineering/app-$ARGUMENTS" },
>                 "index": "projects/@engineering/app-$ARGUMENTS/src/index.html",
>                 "polyfills": ["projects/@engineering/app-$ARGUMENTS/src/polyfills.ts"],
>                 "tsConfig": "projects/@engineering/app-$ARGUMENTS/tsconfig.app.json",
>                 "inlineStyleLanguage": "scss",
>                 "assets": ["projects/@engineering/app-$ARGUMENTS/src/assets"],
>                 "styles": ["projects/@engineering/app-$ARGUMENTS/src/styles.scss"],
>                 "scripts": [],
>                 "browser": "projects/@engineering/app-$ARGUMENTS/src/main.ts"
>             },
>             "configurations": {
>                 "production": {
>                     "budgets": [
>                         { "type": "initial", "maximumWarning": "4.5mb", "maximumError": "8mb" },
>                         { "type": "anyComponentStyle", "maximumWarning": "36kb", "maximumError": "40kb" }
>                     ],
>                     "fileReplacements": [
>                         {
>                             "replace": "projects/@engineering/app-$ARGUMENTS/src/environments/environment.ts",
>                             "with": "projects/@engineering/app-$ARGUMENTS/src/environments/environment.prod.ts"
>                         }
>                     ],
>                     "outputHashing": "all"
>                 },
>                 "development": {
>                     "optimization": false,
>                     "extractLicenses": false,
>                     "sourceMap": true,
>                     "namedChunks": true
>                 }
>             },
>             "defaultConfiguration": "development"
>         },
>         "serve": {
>             "builder": "@angular/build:dev-server",
>             "configurations": {
>                 "production": { "buildTarget": "@engineering/app-$ARGUMENTS:build:production" },
>                 "development": { "buildTarget": "@engineering/app-$ARGUMENTS:build:development" }
>             },
>             "defaultConfiguration": "development"
>         },
>         "test": {
>             "builder": "@angular/build:karma",
>             "options": {
>                 "main": "projects/@engineering/app-$ARGUMENTS/src/test.ts",
>                 "polyfills": ["projects/@engineering/app-$ARGUMENTS/src/polyfills.ts"],
>                 "tsConfig": "projects/@engineering/app-$ARGUMENTS/tsconfig.spec.json",
>                 "karmaConfig": "projects/@engineering/app-$ARGUMENTS/karma.conf.js",
>                 "inlineStyleLanguage": "scss",
>                 "assets": ["projects/@engineering/app-$ARGUMENTS/src/assets"],
>                 "styles": ["projects/@engineering/app-$ARGUMENTS/src/styles.scss"],
>                 "scripts": []
>             }
>         }
>     }
> }
> ```
>
> ## Optionaler npm-Wrapper
>
> Frag den Nutzer, ob ein `serve-app-$ARGUMENTS`-Script in der Root-`package.json` ergänzt werden soll. Pattern:
> ```json
> "serve-app-$ARGUMENTS": "ng s @engineering/app-$ARGUMENTS --port <freier-port>"
> ```
> Freie Ports prüfen — derzeit belegt: 4200 (app-generic2), 4300 (app-generic), 4400 (app-element-host), 4600 (app-demo-host).
>
> ## Smoke-Test (Pflicht vor Final-Report)
>
> Nach dem Anlegen ausführen und beide Builds müssen grün sein:
> ```bash
> npx ng build @engineering/app-$ARGUMENTS --configuration=development
> npx ng build @engineering/app-$ARGUMENTS --configuration=production
> ```
>
> Bei Build-Fehler: zurück an den Auftraggeber mit Fehlermeldung — **nicht** blind reparieren.
>
> ## Final-Report
>
> Liste alle 16 angelegten Files, den `angular.json`-Diff und die Build-Outputs (Bundle-Größen aus dem `ng build`-Output). Erwähne, falls ein npm-Skript ergänzt wurde.
