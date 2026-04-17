# Feature 22 — Implementation Notes (Addendum)

**Date:** 2026-04-17
**Base Spec:** [`docs/features/22-features-graph-wizard.md`](../../features/22-features-graph-wizard.md)
**Status:** Approved — ready for `writing-plans`

Dieses Dokument ergaenzt die Feature-Spec um konkrete Umsetzungs-Entscheidungen, die sich aus context7-Recherche und Codebasis-Erkundung ergeben haben. Alle anderen Details (Datenmodell, DAG-Semantik, Agent-Verhalten, Integration mit Feature 21, Akzeptanzkriterien) bleiben wie im Feature-Dokument beschrieben.

## Kontext

Die Feature-Spec wurde bewusst tech-agnostisch gehalten. Beim Uebergang zur Implementierung haben drei Annahmen einen Realitaetscheck nicht bestanden:

- `@dagrejs/dagre` war als Auto-Layout-Engine vorgesehen — in der Rete.js-v2-Welt gibt es dafuer aber ein natives Plugin
- `ResizablePanelGroup` (shadcn) ist nicht im Projekt — Feature 17 hat stattdessen einen eigenen `useResizable`-Hook etabliert
- `estimate` wurde aus `WizardFeature` entfernt, bricht aber die Input-Signatur von `PlanGeneratorAgent.generatePlanForFeature`

Die drei Entscheidungen unten loesen diese Abweichungen.

## Entscheidung 1 — Auto-Layout via `rete-auto-arrange-plugin`

**Was:** Statt `@dagrejs/dagre` wird das offizielle Rete.js-v2-Auto-Layout-Plugin genutzt. Basiert auf `elkjs`, integriert sich in den bestehenden Plugin-Stack aus `frontend/src/components/spec-flow/editor.ts`.

```ts
import { AutoArrangePlugin, Presets as ArrangePresets } from "rete-auto-arrange-plugin";

const arrange = new AutoArrangePlugin<Schemes>();
arrange.addPreset(ArrangePresets.classic.setup());
area.use(arrange);

// Button-Handler:
await arrange.layout();
```

**Dependencies:**
- Neu: `rete-auto-arrange-plugin` (zieht `elkjs` als Dependency ein)
- Entfaellt: `@dagrejs/dagre` (war in der Feature-Spec vorgesehen, ist nicht installiert)

**Warum:** Weniger Glue-Code, kein manuelles Mappen von Dagre-Ergebnissen auf Rete-Positionen. Folgt dem idiomatischen v2-Muster aus den Rete-Docs.

## Entscheidung 2 — Split-Layout via `useResizable`

**Was:** Der Split zwischen Graph-Canvas (links) und Feature-Detail-Panel (rechts) nutzt den bestehenden `frontend/src/lib/hooks/use-resizable.ts` (eingefuehrt mit Feature 17). Kein shadcn/Radix `ResizablePanelGroup`.

```tsx
// frontend/src/components/wizard/steps/features/FeaturesGraphEditor.tsx
const { width: rightPanelWidth, handleProps } = useResizable({
  initial: 360,
  min: 280,
  max: 560,
  storageKey: "feature-detail-width",
});

return (
  <div className="flex h-full">
    <div className="flex-1 min-w-0">{/* Rete-Canvas */}</div>
    <div className="w-1 cursor-col-resize bg-border hover:bg-primary/20" {...handleProps} />
    <div style={{ width: rightPanelWidth }} className="shrink-0 overflow-y-auto">
      {/* Detail-Panel */}
    </div>
  </div>
);
```

**Dependencies:** Keine Aenderung. Hook ist bereits im Projekt.

**Warum:** Projekt-Konsistenz (bestehendes Pattern), keine neue Dependency, bereits getestetes Drag-Verhalten.

## Entscheidung 3 — `estimate` bleibt im `SpecTask`, LLM schaetzt

**Was:** `WizardFeature` hat kein `estimate`-Feld mehr (per User-Entscheidung). Trotzdem bleibt `SpecTask.estimate` vorhanden (verhindert Breaking Changes in Tests, Docs-Scaffold, Frontend). Der `PlanGeneratorAgent` bekommt die Verantwortung, einen EPIC-`estimate` aus `{XS, S, M, L, XL}` zu vergeben.

### Prompt-Erweiterung

Der bestehende Feature-Plan-Prompt in `PlanGeneratorAgent.generatePlanForFeature` bekommt eine zusaetzliche Regel:

> "Waehle ausserdem einen `estimate` fuer das gesamte Feature aus `{XS, S, M, L, XL}`, basierend auf Anzahl/Komplexitaet der Stories und dem Scope. Gib ihn im JSON als Top-Level-Feld `epicEstimate` zurueck."

Der Agent parsed `epicEstimate` und setzt ihn auf den EPIC-Task. Bei Parsing-Fehler: Fallback `"M"`.

### Signatur-Refactoring

Statt positionaler Parameter nimmt die Methode kuenftig die Struktur:

```kotlin
suspend fun generatePlanForFeature(
    input: WizardFeatureInput,   // id, title, description, scopes, scopeFields, dependsOn
    startPriority: Int,
): List<SpecTask>
```

Vorteile:
- Saubere Uebergabe der neuen Felder (`scopes`, `scopeFields`, `dependsOn`)
- Keine positionale Liste, die bei jedem neuen Feld waechst
- `TaskService.replaceWizardFeatureTasks` kann `WizardFeatureInput` direkt weitergeben

### JSON-Response-Schema (erweitert)

```json
{
  "epicEstimate": "M",
  "stories": [
    { "title": "...", "description": "...", "estimate": "M",
      "tasks": [{ "title": "...", "description": "...", "estimate": "S" }] }
  ]
}
```

**Fallback bei JSON-Parsing-Fehler:** EPIC mit `estimate = "M"` und ohne Stories (wie bisher).

## context7-Erkenntnisse fuer Rete.js v2

Fuer den `writing-plans`-Schritt relevant, damit die Task-Breakdowns stimmen:

### Cycle-Prevention via `addPipe`

Die in der Feature-Spec beschriebene client-seitige Zyklus-Praevention wird ueber den Rete.js-v2-Pipe-Mechanismus umgesetzt:

```ts
editor.addPipe((ctx) => {
  if (ctx.type === "connectioncreate") {
    const { source, target } = ctx.data;
    if (wouldCreateCycle(editor, source, target)) {
      toast.warn("Zyklus verhindert");
      return;  // undefined => Event wird unterdrueckt
    }
  }
  return ctx;
});
```

Der DFS selbst ist in `lib/graph/cycleCheck.ts` gekapselt und erhaelt aktuelle `connections` aus `editor.getConnections()`.

### Custom Node via `Presets.classic.setup({ customize })`

Das Pattern existiert bereits in `spec-flow/editor.ts:52-59`. Unveraendert uebernehmen. Nur der `customNodeComponent`-Parameter wird ausgetauscht (ersetzt `FlowNodeComponent` durch `FeatureNodeComponent`).

## Test-Strategie (Klarstellung)

Das Feature-Dokument listet neue Test-Dateien auf. Zwei Details, die beim Plan-Schreiben wichtig werden:

- **`FeatureProposalAgentTest`** nutzt den bestehenden Agent-Test-Pattern aus `DecisionAgentTest` (Mock-Runner via `override fun runAgent`, nicht Mockk)
- **Rete.js-Komponenten** bekommen *keine* Unit-Tests (in der Codebasis bislang nicht etabliert — `spec-flow/` hat auch keine). Stattdessen ein E2E-Smoke-Test-Protokoll im Done-Dokument

## Offene Punkte fuer writing-plans

- Reihenfolge der Implementierung: Domain + Backend zuerst (unabhaengig testbar), dann Frontend (braucht API)
- Parallelisierbarkeit: `FeatureProposalAgent`, `cycleCheck.ts`, und die Docs-Scaffold-Erweiterung sind weitgehend unabhaengig
- Backward-Compat: Legacy-`features`-Eintraege ohne `id`/`scopes` muessen beim ersten Read konvertiert werden — `parseWizardFeatures` ist der Single-Point-of-Truth dafuer
