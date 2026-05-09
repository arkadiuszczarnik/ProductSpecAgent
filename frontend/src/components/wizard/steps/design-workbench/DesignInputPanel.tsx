"use client";

import { useState } from "react";
import { FileText, Loader2, Plus, ScanLine } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import type { DesignInput, DesignWorkbench } from "@/lib/api";

interface DesignInputPanelProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  working: boolean;
}

function inputLabel(input: DesignInput) {
  if (input.userLabel) return input.userLabel;
  if (input.originalName) return input.originalName;
  if (input.kind === "TEXT") return "Textbeschreibung";
  if (input.kind === "IMAGE") return "Bildreferenz";
  return "HTML/CSS Referenz";
}

export function DesignInputPanel({ projectId, workbench, working }: DesignInputPanelProps) {
  const addTextInput = useDesignWorkbenchStore((s) => s.addTextInput);
  const analyze = useDesignWorkbenchStore((s) => s.analyze);
  const [text, setText] = useState("");

  async function handleAddText() {
    const trimmed = text.trim();
    if (!trimmed) return;
    await addTextInput(projectId, trimmed);
    if (!useDesignWorkbenchStore.getState().error) setText("");
  }

  return (
    <aside className="flex min-h-[280px] flex-col overflow-hidden rounded-md border border-border bg-card">
      <div className="flex h-11 shrink-0 items-center justify-between gap-2 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileText size={15} className="shrink-0 text-muted-foreground" />
          <h2 className="truncate text-xs font-semibold text-foreground">Inputs</h2>
        </div>
        <Button
          type="button"
          variant="outline"
          size="xs"
          onClick={() => analyze(projectId)}
          disabled={working || !workbench?.inputs.length}
          className="gap-1.5"
        >
          {working ? <Loader2 size={12} className="animate-spin" /> : <ScanLine size={12} />}
          Analysieren
        </Button>
      </div>

      <div className="flex shrink-0 flex-col gap-2 border-b border-border p-3">
        <Textarea
          aria-label="Designbeschreibung"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="UI-Richtung, Referenzen, gewuenschte Screens..."
          disabled={working}
          className="min-h-24 resize-none text-sm"
        />
        <Button
          type="button"
          size="sm"
          onClick={handleAddText}
          disabled={working || !text.trim()}
          className="w-full gap-1.5"
        >
          {working ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
          Hinzufuegen
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {workbench?.inputs.length ? (
          <div className="flex flex-col gap-2">
            {workbench.inputs.map((input) => (
              <div key={input.id} className="rounded-md border border-border bg-background p-2">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span className="min-w-0 truncate text-xs font-medium text-foreground">{inputLabel(input)}</span>
                  <Badge variant="ghost" className="shrink-0 text-[10px]">
                    {input.kind}
                  </Badge>
                </div>
                {input.classification ? (
                  <div className="space-y-1 text-[11px] leading-4 text-muted-foreground">
                    <p className="line-clamp-2">{input.classification.summary}</p>
                    <p className="line-clamp-2 text-foreground/80">{input.classification.suggestedUse}</p>
                  </div>
                ) : (
                  <p className="text-[11px] text-muted-foreground">Noch nicht analysiert.</p>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="flex h-full min-h-24 items-center justify-center rounded-md border border-dashed border-border px-3 text-center text-xs text-muted-foreground">
            Beschreibe die visuelle Richtung.
          </div>
        )}
      </div>
    </aside>
  );
}
