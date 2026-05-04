"use client";

import { Button } from "@/components/ui/button";
import { RefreshCw, Trash2 } from "lucide-react";
import type { DesignBundle } from "@/lib/api";

interface Props {
  bundle: DesignBundle;
  onReplace: () => void;
  onDelete: () => void;
}

export function DesignBundleHeader({ bundle, onReplace, onDelete }: Props) {
  const sizeKb = (bundle.sizeBytes / 1024).toFixed(0);
  const date = new Date(bundle.uploadedAt).toLocaleDateString("de-DE");

  return (
    <div className="flex items-center justify-between border-b border-border bg-card px-4 py-3">
      <div className="text-sm">
        <span className="font-semibold">{bundle.originalFilename}</span>
        <span className="text-muted-foreground"> · {sizeKb} KB · {bundle.pages.length} Pages · hochgeladen {date}</span>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={onReplace} className="gap-1.5">
          <RefreshCw size={13} /> Ersetzen
        </Button>
        <Button variant="ghost" size="sm" onClick={onDelete} className="gap-1.5 text-destructive">
          <Trash2 size={13} /> Entfernen
        </Button>
      </div>
    </div>
  );
}
