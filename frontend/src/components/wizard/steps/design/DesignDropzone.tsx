"use client";

import { useRef, useState, type DragEvent } from "react";
import { Upload, Info } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface Props {
  uploading: boolean;
  error: string | null;
  onPick: (file: File) => void;
  onSkip: () => void;
}

export function DesignDropzone({ uploading, error, onPick, onSkip }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  function handleDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) onPick(file);
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-6 px-8 py-12">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        className={cn(
          "flex w-full max-w-2xl cursor-pointer flex-col items-center gap-3 rounded-2xl border-2 border-dashed bg-card px-6 py-12 transition-colors",
          dragOver ? "border-primary bg-primary/5" : "border-border",
          uploading && "cursor-not-allowed opacity-50",
        )}
      >
        <Upload size={32} className="text-muted-foreground" />
        <p className="text-base font-semibold">Design-Bundle hochladen</p>
        <p className="text-sm text-muted-foreground">
          Drag &amp; Drop oder Klick · ZIP · max 5 MB
        </p>
        <p className="text-xs text-muted-foreground">
          Bundles aus Claude Design (.zip)
        </p>
        <input
          ref={inputRef}
          type="file"
          accept=".zip,application/zip"
          className="hidden"
          disabled={uploading}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) onPick(file);
            e.target.value = "";
          }}
        />
      </div>

      {error && (
        <div className="max-w-2xl rounded-md border border-destructive bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </div>
      )}

      <div className="flex w-full max-w-2xl items-start gap-2 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
        <Info size={14} className="mt-0.5 shrink-0" />
        <span>
          Optional: Du kannst diesen Schritt überspringen. Mit Design-Bundle
          werden FRONTEND/BACKEND-Specs konkreter.
        </span>
      </div>

      <Button variant="outline" onClick={onSkip} disabled={uploading}>
        Überspringen
      </Button>
    </div>
  );
}
