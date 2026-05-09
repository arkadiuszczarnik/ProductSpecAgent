"use client";

import { useState } from "react";
import { ArrowRight, Check, Layers3, Loader2, PanelRight, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import type { DesignScreen, DesignVariant, DesignWorkbench } from "@/lib/api";

interface DesignControlPanelProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  selectedScreen: DesignScreen | null;
  previewVariant: DesignVariant | null;
  working: boolean;
  completing: boolean;
  blocked: boolean;
  onComplete: () => void;
}

export function DesignControlPanel({
  projectId,
  workbench,
  selectedScreen,
  previewVariant,
  working,
  completing,
  blocked,
  onComplete,
}: DesignControlPanelProps) {
  const proposeScreens = useDesignWorkbenchStore((s) => s.proposeScreens);
  const generateVariant = useDesignWorkbenchStore((s) => s.generateVariant);
  const setActiveVariant = useDesignWorkbenchStore((s) => s.setActiveVariant);
  const selectScreen = useDesignWorkbenchStore((s) => s.selectScreen);
  const [prompt, setPrompt] = useState("");

  const hasActiveValidVariant = Boolean(
    workbench?.screens.some((screen) =>
      screen.variants.some((variant) => variant.id === screen.activeVariantId && variant.status === "VALID"),
    ),
  );

  async function handleGenerate() {
    if (!selectedScreen) return;
    await generateVariant(projectId, selectedScreen.id, prompt.trim());
    if (!useDesignWorkbenchStore.getState().error) setPrompt("");
  }

  return (
    <aside className="flex min-h-[280px] flex-col overflow-hidden rounded-md border border-border bg-card">
      <div className="flex h-11 shrink-0 items-center justify-between gap-2 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <PanelRight size={15} className="shrink-0 text-muted-foreground" />
          <h2 className="truncate text-xs font-semibold text-foreground">Steuerung</h2>
        </div>
        <Button
          type="button"
          variant="outline"
          size="xs"
          onClick={() => proposeScreens(projectId)}
          disabled={working}
          className="gap-1.5"
        >
          {working ? <Loader2 size={12} className="animate-spin" /> : <Layers3 size={12} />}
          Screens
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="border-b border-border p-2">
          <div className="mb-2 flex items-center justify-between gap-2 px-1">
            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Screens</span>
            <Badge variant="ghost" className="text-[10px]">{workbench?.screens.length ?? 0}</Badge>
          </div>
          <div className="flex flex-col gap-1">
            {workbench?.screens.length ? (
              workbench.screens.map((screen) => (
                <button
                  key={screen.id}
                  type="button"
                  onClick={() => selectScreen(screen.id)}
                  className={cn(
                    "rounded-md border px-2 py-2 text-left transition-colors",
                    selectedScreen?.id === screen.id
                      ? "border-primary/40 bg-primary/10"
                      : "border-transparent hover:border-border hover:bg-secondary/60",
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="min-w-0 truncate text-xs font-medium text-foreground">{screen.name}</span>
                    <span className="shrink-0 text-[10px] text-muted-foreground">{screen.variants.length}</span>
                  </div>
                  <p className="mt-1 line-clamp-2 text-[11px] leading-4 text-muted-foreground">{screen.purpose}</p>
                </button>
              ))
            ) : (
              <div className="rounded-md border border-dashed border-border px-3 py-5 text-center text-xs text-muted-foreground">
                Screens vorschlagen lassen.
              </div>
            )}
          </div>
        </div>

        <div className="border-b border-border p-3">
          <Textarea
            aria-label="Variantenvorgabe"
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            placeholder="Variante: dichter, ruhiger, mehr Dashboard..."
            disabled={working || !selectedScreen}
            className="min-h-20 resize-none text-sm"
          />
          <Button
            type="button"
            size="sm"
            onClick={handleGenerate}
            disabled={working || !selectedScreen}
            className="mt-2 w-full gap-1.5"
          >
            {working ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
            Variante generieren
          </Button>
        </div>

        <div className="p-2">
          <div className="mb-2 flex items-center justify-between gap-2 px-1">
            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Varianten</span>
            <Badge variant="ghost" className="text-[10px]">{selectedScreen?.variants.length ?? 0}</Badge>
          </div>
          <div className="flex flex-col gap-2">
            {selectedScreen?.variants.length ? (
              [...selectedScreen.variants].reverse().map((variant) => {
                const active = selectedScreen.activeVariantId === variant.id;
                const previewing = previewVariant?.id === variant.id;
                return (
                  <div
                    key={variant.id}
                    className={cn(
                      "rounded-md border bg-background p-2",
                      previewing ? "border-primary/40" : "border-border",
                    )}
                  >
                    <div className="mb-1 flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="truncate text-xs font-medium text-foreground">{variant.title}</div>
                        <div className="text-[10px] text-muted-foreground">v{variant.version} - {variant.status}</div>
                      </div>
                      {active && (
                        <Badge variant="success" className="shrink-0 gap-1 text-[10px]">
                          <Check size={10} /> Aktiv
                        </Badge>
                      )}
                    </div>
                    {variant.rationale && (
                      <p className="mb-2 line-clamp-2 text-[11px] leading-4 text-muted-foreground">{variant.rationale}</p>
                    )}
                    <Button
                      type="button"
                      variant={active ? "secondary" : "outline"}
                      size="xs"
                      onClick={() => setActiveVariant(projectId, selectedScreen.id, variant.id)}
                      disabled={working || active}
                      className="w-full gap-1.5"
                    >
                      <Check size={12} />
                      Aktivieren
                    </Button>
                  </div>
                );
              })
            ) : (
              <div className="rounded-md border border-dashed border-border px-3 py-5 text-center text-xs text-muted-foreground">
                Noch keine Varianten.
              </div>
            )}
          </div>
        </div>

        {workbench?.suggestions.length ? (
          <div className="border-t border-border p-2">
            <div className="mb-2 px-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Vorschlaege</div>
            <div className="flex flex-col gap-2">
              {workbench.suggestions.map((suggestion) => (
                <div key={suggestion.id} className="rounded-md border border-border bg-background p-2">
                  <div className="text-xs font-medium text-foreground">{suggestion.title}</div>
                  <p className="mt-1 line-clamp-2 text-[11px] leading-4 text-muted-foreground">{suggestion.description}</p>
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </div>

      <div className="shrink-0 border-t border-border p-3">
        <Button
          type="button"
          size="sm"
          onClick={onComplete}
          disabled={working || completing || blocked || !hasActiveValidVariant}
          title={blocked ? "Offene Blocker klaeren" : undefined}
          className="w-full gap-1.5"
        >
          {completing ? <Loader2 size={14} className="animate-spin" /> : <ArrowRight size={14} />}
          Abschliessen
        </Button>
      </div>
    </aside>
  );
}
