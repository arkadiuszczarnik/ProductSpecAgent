"use client";

import { useEffect, useRef, useState } from "react";
import { Trash2, Upload, FileText } from "lucide-react";
import { useDocumentStore } from "@/lib/stores/document-store";
import type { DocumentState } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props { projectId: string }

const STATE_STYLES: Record<DocumentState, string> = {
  UPLOADED: "bg-muted text-muted-foreground",
  PROCESSING: "bg-blue-500/20 text-blue-300",
  EXTRACTED: "bg-emerald-500/20 text-emerald-300",
  FAILED: "bg-red-500/20 text-red-300",
};

export function DocumentsPanel({ projectId }: Props) {
  const { documents, loading, uploading, error, loadDocuments, uploadDocument, deleteDocument, startPolling, stopPolling, reset } = useDocumentStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    loadDocuments(projectId).then(() => startPolling(projectId));
    return () => { stopPolling(); reset(); };
  }, [projectId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleFiles = (files: FileList | null) => {
    if (!files) return;
    Array.from(files).forEach((f) => uploadDocument(projectId, f));
  };

  return (
    <div className="flex h-full flex-col p-3 gap-3">
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFiles(e.dataTransfer.files); }}
        onClick={() => fileInputRef.current?.click()}
        className={cn(
          "border-2 border-dashed rounded-md p-4 text-center cursor-pointer text-xs text-muted-foreground transition-colors",
          dragOver ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"
        )}
      >
        <Upload size={16} className="mx-auto mb-1.5" />
        {uploading ? "Uploading..." : "PDF, Markdown oder Text hier ablegen oder klicken"}
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.md,.txt,application/pdf,text/markdown,text/plain"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
      </div>

      {error && <div className="text-xs text-destructive">{error}</div>}

      <div className="flex-1 overflow-y-auto space-y-1">
        {loading && documents.length === 0 && <div className="text-xs text-muted-foreground">Lade...</div>}
        {!loading && documents.length === 0 && <div className="text-xs text-muted-foreground">Noch keine Dokumente.</div>}
        {documents.map((doc) => (
          <div key={doc.id} className="flex items-center gap-2 rounded-md border border-border p-2">
            <FileText size={14} className="shrink-0 text-muted-foreground" />
            <div className="flex-1 min-w-0">
              <div className="text-xs truncate">{doc.title}</div>
              <div className="text-[10px] text-muted-foreground">{new Date(doc.createdAt).toLocaleString("de-DE")}</div>
            </div>
            <span className={cn("rounded-full px-2 py-0.5 text-[10px]", STATE_STYLES[doc.state])}>
              {doc.state}
            </span>
            <button
              onClick={() => deleteDocument(projectId, doc.id)}
              className="text-muted-foreground hover:text-destructive transition-colors"
              title="Löschen"
            >
              <Trash2 size={13} />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
