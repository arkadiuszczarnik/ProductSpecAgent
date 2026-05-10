"use client";

import { Loader2, Monitor, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { designPreviewUrl, type DesignWorkbench } from "@/lib/api";

interface DesignCanvasPreviewProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  working: boolean;
}

export function DesignCanvasPreview({ projectId, workbench, working }: DesignCanvasPreviewProps) {
  const currentDesign = workbench?.currentDesign ?? null;
  const previewSrc = currentDesign
    ? `${designPreviewUrl(projectId)}?v=${encodeURIComponent(currentDesign.id)}`
    : null;

  return (
    <section className="flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden rounded-md border border-border bg-background">
      <div className="flex h-11 shrink-0 items-center justify-between gap-3 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-secondary text-muted-foreground">
            <Monitor size={15} />
          </div>
          <div className="min-w-0">
            <div className="truncate text-xs font-semibold text-foreground">
              {currentDesign?.title ?? "Canvas Preview"}
            </div>
            <div className="truncate text-[11px] text-muted-foreground">
              {currentDesign ? "Generiertes Design" : "Noch kein Design generiert"}
            </div>
          </div>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          disabled={!previewSrc || working}
          onClick={() => {
            const frame = document.getElementById("design-preview-frame") as HTMLIFrameElement | null;
            if (frame && previewSrc) frame.src = previewSrc;
          }}
          title="Vorschau neu laden"
          aria-label="Vorschau neu laden"
        >
          <RefreshCw size={14} />
        </Button>
      </div>

      <div className="flex flex-1 overflow-auto bg-muted/35 p-4">
        <div className="mx-auto flex h-full min-h-[360px] w-full max-w-5xl items-stretch justify-center">
          {previewSrc ? (
            <div className="relative h-full min-h-[360px] w-full">
              <iframe
                id="design-preview-frame"
                key={currentDesign?.id}
                src={previewSrc}
                sandbox="allow-scripts"
                title={`Design Vorschau ${currentDesign?.title ?? ""}`}
                className="h-full min-h-[360px] w-full rounded-md border border-border bg-white shadow-sm"
              />
              {working && (
                <div className="absolute inset-0 flex items-center justify-center rounded-md bg-background/70">
                  <Loader2 size={18} className="animate-spin text-muted-foreground" />
                </div>
              )}
            </div>
          ) : (
            <div className="flex h-full min-h-[360px] w-full items-center justify-center rounded-md border border-dashed border-border bg-background text-xs text-muted-foreground">
              Beschreibung oder Bild eingeben und Design generieren.
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
