"use client";

import { Monitor, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { DesignScreen, DesignVariant } from "@/lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface DesignCanvasPreviewProps {
  projectId: string;
  screen: DesignScreen | null;
  variant: DesignVariant | null;
  working: boolean;
}

export function DesignCanvasPreview({ projectId, screen, variant, working }: DesignCanvasPreviewProps) {
  const previewSrc = variant
    ? `${API_BASE}/api/v1/projects/${encodeURIComponent(projectId)}/design/preview/${encodeURIComponent(variant.id)}`
    : null;

  return (
    <section className="flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden rounded-md border border-border bg-background">
      <div className="flex h-11 shrink-0 items-center justify-between gap-3 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-secondary text-muted-foreground">
            <Monitor size={15} />
          </div>
          <div className="min-w-0">
            <div className="truncate text-xs font-semibold text-foreground">{screen?.name ?? "Canvas"}</div>
            <div className="truncate text-[11px] text-muted-foreground">
              {variant ? `${variant.title} - v${variant.version}` : "Keine Variante"}
            </div>
          </div>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          disabled={!variant || working}
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
            <iframe
              id="design-preview-frame"
              key={variant?.id}
              src={previewSrc}
              sandbox="allow-scripts"
              title={`Design Vorschau ${variant?.title ?? ""}`}
              className="h-full min-h-[360px] w-full rounded-md border border-border bg-white shadow-sm"
            />
          ) : (
            <div className="flex h-full min-h-[360px] w-full items-center justify-center rounded-md border border-dashed border-border bg-background text-xs text-muted-foreground">
              Variante generieren, um die Vorschau zu laden.
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
