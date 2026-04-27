"use client";

import { useState } from "react";
import { Download, X, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { exportProject } from "@/lib/api";

interface ExportDialogProps {
  projectId: string;
  projectName: string;
  open: boolean;
  onClose: () => void;
}

export function ExportDialog({ projectId, projectName, open, onClose }: ExportDialogProps) {
  const [includeDecisions, setIncludeDecisions] = useState(true);
  const [includeClarifications, setIncludeClarifications] = useState(true);
  const [includeTasks, setIncludeTasks] = useState(true);
  const [exporting, setExporting] = useState(false);

  if (!open) return null;

  async function handleExport() {
    setExporting(true);
    try {
      const blob = await exportProject(projectId, {
        includeDecisions,
        includeClarifications,
        includeTasks,
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${projectName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      onClose();
    } catch {
      // error state could be added
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <Card className="relative z-10 w-full max-w-md mx-4">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Export Project</CardTitle>
            <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
              <X size={16} />
            </button>
          </div>
          <CardDescription>Export &quot;{projectName}&quot; as a Git-ready ZIP archive.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">Select what to include:</p>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors">
            <input type="checkbox" checked={true} disabled className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Specification</span>
              <p className="text-xs text-muted-foreground">SPEC.md, README.md, .gitignore</p>
            </div>
          </label>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 opacity-70 cursor-not-allowed transition-colors">
            <input type="checkbox" checked={true} disabled className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Docs scaffold</span>
              <p className="text-xs text-muted-foreground">docs/features, docs/architecture, docs/backend, docs/frontend</p>
            </div>
          </label>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors">
            <input type="checkbox" checked={includeDecisions} onChange={(e) => setIncludeDecisions(e.target.checked)} className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Decisions</span>
              <p className="text-xs text-muted-foreground">All decisions with pro/contra and rationale</p>
            </div>
          </label>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors">
            <input type="checkbox" checked={includeClarifications} onChange={(e) => setIncludeClarifications(e.target.checked)} className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Clarifications</span>
              <p className="text-xs text-muted-foreground">All clarification questions and answers</p>
            </div>
          </label>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 cursor-pointer hover:bg-muted/30 transition-colors">
            <input type="checkbox" checked={includeTasks} onChange={(e) => setIncludeTasks(e.target.checked)} className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Tasks & Plan</span>
              <p className="text-xs text-muted-foreground">PLAN.md and individual task files</p>
            </div>
          </label>
          <label className="flex items-center gap-3 rounded-md border px-3 py-2.5 opacity-70 cursor-not-allowed transition-colors">
            <input type="checkbox" checked={true} disabled className="accent-primary" />
            <div>
              <span className="text-sm font-medium">Documents</span>
              <p className="text-xs text-muted-foreground">Hochgeladene Dateien aus docs/uploads/</p>
            </div>
          </label>
        </CardContent>
        <CardFooter className="justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={exporting}>Cancel</Button>
          <Button onClick={handleExport} disabled={exporting}>
            {exporting ? <><Loader2 size={14} className="animate-spin" /> Exporting...</> : <><Download size={14} /> Export ZIP</>}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
