# Feature 39 — Chat-Anzeige-Fix für strukturierte Wizard-Felder

## Problem

Beim Abschluss des FEATURES-Steps erscheint im Agent-Chat statt einer
lesbaren Zusammenfassung:

```
**Features**

Feature-Liste: [object Object], [object Object], …
edges: [object Object], [object Object], …
```

Ursache: `formatStepFields` (`frontend/src/lib/step-field-labels.ts`) ruft
auf jedem Array-Element `value.join(", ")` auf. Für `WizardFeature[]` und
`WizardFeatureEdge[]` liefert das den Default `Object.prototype.toString()`
→ `"[object Object]"`. Zusätzlich existiert für den Key `edges` kein
Label-Eintrag, wodurch der Raw-Key sichtbar wird.

Der Bug betrifft nur die User-facing Chat-Nachricht — der Backend-Agent
erhält die Daten weiterhin korrekt strukturiert über `plainFields` (siehe
`wizard-store.ts:188`).

## Ziel

Wizard-Step-Inputs werden im Chat als lesbare Markdown-Zusammenfassung
dargestellt, auch für Object-Arrays.

## Scope

### In Scope

1. **FEATURES-Sonderbehandlung** in `formatStepFields`:
   - `features` rendert als Bullet-Liste mit Title und (sofern vorhanden)
     `depends on: <Title1>, <Title2>` — Dependencies werden über die
     Feature-Liste von ID auf Title aufgelöst.
   - `edges` wird im Chat-Output **nicht** dargestellt (redundant zu
     `dependsOn` der Features).
2. **Generischer Fallback** für unbekannte Object-Arrays: statt
   `[object Object]` ein `JSON.stringify`-Result, damit zukünftige neue
   Object-Felder nicht still kaputt rendern.
3. Keine Backend-Änderungen.

### Out of Scope

- Vollständige Scope-Field-Darstellung (UI-Komponenten, API-Endpunkte etc.)
  im Chat — der Agent erhält diese Details ohnehin strukturiert.
- Markdown-Rendering im `ChatMessage`-Component (`{message.content}` rendert
  weiterhin als Plain Text mit Line-Breaks).
- Persistenzformat oder Wire-Format der Wizard-Daten.

## Akzeptanzkriterien

- Beim Completen des FEATURES-Steps erscheint im Chat eine Bullet-Liste der
  Feature-Titles statt `[object Object]`.
- Dependencies werden mit Titles statt UUIDs angezeigt.
- `edges` taucht im Chat-Output nicht mehr auf.
- Andere Steps (IDEA, PROBLEM, MVP, ARCHITECTURE, BACKEND, FRONTEND) sind
  unverändert.
- `npm run lint` und `npm run build` (frontend) laufen sauber.

## Verifikation

Manuell im Browser: SaaS-Projekt anlegen, Wizard bis FEATURES durchklicken,
mehrere Features mit Dependencies hinzufügen, Step abschliessen, Chat
inspizieren.
