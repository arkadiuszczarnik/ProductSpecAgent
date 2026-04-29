"use client";

import { useState } from "react";
import { Trash2, FileText, FolderTree } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { FileViewer } from "./FileViewer";
import { DeleteBundleDialog } from "./DeleteBundleDialog";
import { cn } from "@/lib/utils";

export function BundleDetail() {
  const { selectedBundle, selectedFilePath, selectFile } = useAssetBundleStore();
  const [showDelete, setShowDelete] = useState(false);

  if (!selectedBundle) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
        Bundle aus der Liste auswählen.
      </div>
    );
  }

  const m = selectedBundle.manifest;

  return (
    <div className="flex h-full flex-col">
      <div className="border-b p-4 space-y-2">
        <div className="flex items-start justify-between">
          <div>
            <div className="text-xs font-mono text-muted-foreground">{m.id}</div>
            <h2 className="text-lg font-semibold mt-1">{m.title}</h2>
            <p className="text-sm text-muted-foreground">{m.description}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setShowDelete(true)}>
            <Trash2 size={16} className="mr-1.5" />
            Löschen
          </Button>
        </div>
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span>v{m.version}</span>
          <span>·</span>
          <span>{m.step.toLowerCase()}.{m.field}.{m.value}</span>
          <span>·</span>
          <span>updated {m.updatedAt}</span>
        </div>
      </div>

      <div className="flex flex-1 min-h-0">
        <div className="w-72 border-r overflow-y-auto">
          <div className="p-3 text-xs font-medium text-muted-foreground flex items-center gap-1.5">
            <FolderTree size={12} /> Files ({selectedBundle.files.length})
          </div>
          {selectedBundle.files.map((f) => (
            <button
              key={f.relativePath}
              onClick={() => selectFile(f.relativePath)}
              className={cn(
                "w-full flex items-center gap-2 px-3 py-1.5 text-xs text-left hover:bg-muted/50",
                selectedFilePath === f.relativePath ? "bg-muted" : "",
              )}
            >
              <FileText size={12} />
              <span className="truncate">{f.relativePath}</span>
              <span className="ml-auto text-muted-foreground">{Math.round(f.size / 1024)} KB</span>
            </button>
          ))}
        </div>
        <div className="flex-1 overflow-auto">
          <FileViewer />
        </div>
      </div>

      <DeleteBundleDialog manifest={showDelete ? m : null} onClose={() => setShowDelete(false)} />
    </div>
  );
}
