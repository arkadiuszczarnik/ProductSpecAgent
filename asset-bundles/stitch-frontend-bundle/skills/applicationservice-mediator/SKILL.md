---
name: applicationservice-mediator
description: Use when adding cross-bundle communication, events, states, services, or DI in @engineering/frontend-core — enforces team-prefix naming, framework-agnostic models, additive interfaces, IIFE encapsulation, and DI interface-vs-implementation pattern.
---

# `IApplicationService` als Mediator

## Wann anwenden
Anwenden, sobald Code teamuebergreifend kommuniziert: neues Event, neuer State, neuer Service in der DI, neues Interface fuer Cross-Bundle-Konsumenten. Diese Skill setzt ADR 0001 in konkrete Naming-, API- und Encapsulation-Regeln um.

## Mediator-Prinzip
Jedes Bundle bekommt dieselbe `IApplicationService`-Instanz. Web Components erhalten sie ueber `@Input applicationservice: IApplicationService`. Alle Cross-Bundle-Kommunikation laeuft ueber diese Instanz: Events, geteilte States, Logging, DI-Lookups. **Kein direkter Import zwischen Team-Bundles**, kein Schreiben in `window`, keine globalen Singletons. Der Mediator ist der einzige offizielle Kanal.

## Naming-Regeln
Alle StateIds, Event-Namen und Service-Namen tragen den **Team-Prefix** (ADR 0001).

Beispiele:
- `engineering.ui.dropdown.opened` — Event aus dem Engineering-Team-Bundle.
- `mit.facetimeline.state.selectedTimespan` — State des MIT-Teams.
- `epiphany.collection.service.dataProvider` — Service-Name des Epiphany-Teams.

Faustregel: `<team>.<lib-or-feature>.<state|event|service>.<concreteName>`. Der konkrete Team-Prefix dieses Repos steht in `CLAUDE.md`.

## Framework-agnostische Models
Models, die ueber den Mediator wandern, sind reine TypeScript-Types — keine `Observable`s, keine Angular-Types (`InjectionToken`, `Signal`, `WritableSignal`), kein RxJS in der Signatur. Plain Interfaces mit primitiven Feldern, Enums oder anderen plain-TS-Types. So koennen Konsumenten in anderen Frameworks (oder ohne Framework) die Interfaces verwenden.

```ts
// gut
export interface IFooState {
  readonly id: string;
  readonly value: number;
  readonly updatedAtUtc: string;
}

// schlecht — RxJS in der Signatur
export interface IFooState {
  readonly value$: Observable<number>;
}
```

## Additive Interface-Aenderungen
Nur additive Aenderungen sind erlaubt (ADR 0001). Konkret:
- Neue Felder werden mit `?` als optional ergaenzt.
- **Keine Renames** existierender Felder.
- **Keine Removals** existierender Felder.
- Breaking Change → neue Major-Version + neuer Bundle. Der alte Bundle bleibt parallel verfuegbar.

## IIFE statt Globals
Modul-Code darf nicht in `window` oder `globalThis` schreiben. Wenn Initialisierungs-Code nur einmal laufen soll, eine IIFE verwenden:

```ts
(() => {
  // private setup, kein Leak in den globalen Scope
})();
```

Hintergrund: Mehrere Bundles teilen sich denselben DOM und denselben globalen Scope. Globale Schreibzugriffe fuehren zu Konflikten zwischen Bundles unterschiedlicher Versionen. Siehe MDN-IIFE: https://developer.mozilla.org/en-US/docs/Glossary/IIFE.

## DI-Pattern
Die DI haelt die konkrete Implementierung; die oeffentliche API ist immer das Interface. Konsumenten holen sich den Service ueber den `dependencyInjectionService` des Mediators:

```ts
const fooService = applicationService.dependencyInjectionService.resolveInstance<IFooService>('FooService');
fooService.doStuff();
```

Die Registrierung laeuft ueber das Gegenstueck `applicationService.dependencyInjectionService.registerInstance<FooService>('FooService', new FooService(...))` (typischerweise in einem Initializer-Service, vgl. `engineering-ui-dropdown-example-initializer-service.ts`).

Konsequenz: Ein Team kann seine Implementierung intern austauschen, ohne dass andere Teams ihren Code anpassen muessen, solange das Interface stabil bleibt (additive Aenderungen).

## StateBinding und eventAggregationService
`StateBinding<T>` ist die kanonische Klasse fuer State-Subscriptions in Angular-Komponenten. Aus `@engineering/lib-infrastructure-core/lib/classes/state-binding` importieren. Konstruktor: `new StateBinding<T>(stateId, applicationService, optionalCallback, optionalChangeDetector)`. Auf Destroy via `binding?.destroy()` aufraeumen.

```ts
private payloadBinding: StateBinding<IVideoProgressBarPayload>;

private init(): void {
    this.payloadBinding = new StateBinding<IVideoProgressBarPayload>(
        this.getEventPrefix() + LibPlayerStateIds.VideoProgressBarPayload,
        this.applicationservice,
        this.initProgressBar.bind(this, true),  // Callback bei Wertaenderung
        this.changeDetector,                    // optionaler CD-Trigger
    );
}

ngOnDestroy(): void {
    this.payloadBinding?.destroy();
}
```

`eventAggregationService.subscribe` gibt eine Subscription-ID zurueck, die du in `ngOnDestroy` fuer `unSubscribe` brauchst. Ereignisnamen sind Enum-Werte aus den `lib-*-core`-Paketen (Team-Prefix steckt im Enum-Namensraum, optional zusaetzlich pro `dataContextId` per `getEventPrefix()`).

```ts
private subscriptionId: string;

private initSubscriptions(): void {
    this.subscriptionId = this.applicationservice.eventAggregationService.subscribe(
        this.getEventPrefix() + LibPlayerEvents.VideoCanPlay,
        (payload) => this.onCanPlay(payload),
    );
}

ngOnDestroy(): void {
    if (this.subscriptionId) {
        this.applicationservice.eventAggregationService.unSubscribe(
            this.getEventPrefix() + LibPlayerEvents.VideoCanPlay,
            this.subscriptionId,
        );
    }
}
```

Publish ist symmetrisch:

```ts
this.applicationservice.eventAggregationService.publish(
    this.getEventPrefix() + LibPlayerEvents.VideoSetFrameFromExternal,
    currentFrame,
);
```

`getEventPrefix()` und die `dataContextId`-Konvention: viele Components erlauben mehrere parallele Instanzen im selben DOM. Damit deren Events sich nicht kreuzen, wird der `dataContextId`-Input als Prefix vor den Enum-Wert gehaengt. Pattern aus `video-progress-bar.component.ts`:

```ts
@Input() dataContextId: string = '';

public getEventPrefix(): string {
    return this.dataContextId ? this.dataContextId + ':' : '';
}
```

Ohne `dataContextId` ist der Prefix leer und die Events sind global. Mit gesetztem `dataContextId` (z. B. `'player-A'`) werden alle Events automatisch namespaced (`player-A:LibPlayerEvents.VideoCanPlay`).

Reale Referenzen: `projects/@engineering/lib-ui-core-angular/progress-bar/progress-bar.component.ts` (eine Binding) und `projects/@engineering/lib-player-ui-angular/video-progress-bar/video-progress-bar.component.ts` (mehrere Bindings + Subscriptions + Cleanup-Pattern).

## Events vs. States
- **Event** — ein Vorgang, transient. Wird gefeuert, einmal verarbeitet, dann weg. Beispiel: `engineering.ui.dropdown.opened`.
- **State** — der aktuelle Wert, persistent. Wird gelesen, beobachtet, geschrieben; spaeter beigetretene Subscribers bekommen sofort den aktuellen Wert. Beispiel: `mit.facetimeline.state.selectedTimespan`.

| Aspekt | Event | State |
|---|---|---|
| Semantik | „etwas ist passiert" | „so steht es gerade" |
| Persistenz | transient (fire-and-forget) | persistent |
| Late-Subscriber | bekommt nichts | bekommt den aktuellen Wert |
| Typisches Beispiel | UserClick, RequestCompleted | SelectedItemId, IsLoading |

Faustregel: alles was eine konkrete Aktion ist → Event. Alles was einen Wert hat, den man lesen oder darstellen muss → State.

## Anti-Patterns
- `Observable<…>` oder `Signal<…>` in einem Interface, das ueber den Mediator wandert.
- StateId / EventName ohne Team-Prefix (z. B. `dropdownOpened` statt `engineering.ui.dropdown.opened`).
- Direkter Import eines Service aus einem fremden Team-Bundle (`import { … } from '@<other-team>/…'`).
- Schreiben in `window.foo = …`, statt IIFE oder DI.
- Rename oder Removal eines bereits ausgelieferten Interface-Feldes.
- Verschicken eines Angular-`HttpResponse` oder Material-`MatDialogRef` ueber den Mediator.

## Verweise
- ADR 0001 (`docs/adr/0001-modular-typescript.md`) — Quelle fuer alle Regeln dieser Skill.
- `bundle-system` — wie der Mediator ins Bundle eingebunden wird.
- `creating-webcomponents` — `@Input applicationservice` als Eintrittspunkt fuer den Mediator in Web Components.
