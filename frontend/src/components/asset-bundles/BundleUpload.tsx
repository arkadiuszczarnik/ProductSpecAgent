"use client";

import { useRef, useState } from "react";
import { Upload, AlertCircle } from "lucide-react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { cn } from "@/lib/utils";

export function BundleUpload() {
  const { uploading, error, upload, clearError } = useAssetBundleStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    const file = files[0];
    if (!file.name.toLowerCase().endsWith(".zip")) {
      useAssetBundleStore.setState({ error: "Bitte eine .zip-Datei hochladen." });
      return;
    }
    upload(file);
  }

  return (
    <div className="space-y-2">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "border-2 border-dashed rounded-md p-4 text-center cursor-pointer text-xs text-muted-foreground transition-colors",
          dragOver ? "border-primary bg-primary/5" : "border-border hover:border-primary/50",
        )}
      >
        <Upload size={16} className="mx-auto mb-1.5" />
        {uploading ? "Lädt hoch …" : "ZIP hier ablegen oder klicken"}
        <input
          ref={fileInputRef}
          type="file"
          accept=".zip,application/zip"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>
      {error && (
        <div className="flex items-start gap-2 rounded-md border border-red-500/40 bg-red-500/5 px-3 py-2 text-xs text-red-300">
          <AlertCircle size={14} className="mt-0.5 shrink-0" />
          <div className="flex-1">{error}</div>
          <button onClick={clearError} className="text-red-400 hover:text-red-200">×</button>
        </div>
      )}
    </div>
  );
}
