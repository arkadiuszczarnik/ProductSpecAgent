"use client";

import { useEffect, useState } from "react";
import { Check, Clipboard, Download, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { exportHandoff, getHandoffPreview } from "@/lib/api";

interface HandoffDialogProps {
  projectId: string;
  projectName: string;
  open: boolean;
  onClose: () => void;
}

export function HandoffDialog({ projectId, projectName, open, onClose }: HandoffDialogProps) {
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [handoffMd, setHandoffMd] = useState("");
  const [implementationOrder, setImplementationOrder] = useState("");

  useEffect(() => {
    if (!open) return;
    setCopied(false);
    setLoading(true);
    getHandoffPreview(projectId)
      .then((preview) => {
        setHandoffMd(preview.claudeMd);
        setImplementationOrder(preview.implementationOrder);
      })
      .catch(() => {
        setHandoffMd("");
        setImplementationOrder("");
      })
      .finally(() => setLoading(false));
  }, [open, projectId]);

  if (!open) return null;

  async function handleCopy() {
    await navigator.clipboard.writeText(handoffMd);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1600);
  }

  async function handleExport() {
    setExporting(true);
    try {
      const blob = await exportHandoff(projectId, {
        claudeMd: handoffMd,
        agentsMd: handoffMd,
        implementationOrder,
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${projectName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-handoff.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      onClose();
    } catch {
      /* handled silently */
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <Card className="relative z-10 w-full max-w-5xl mx-4 h-[88vh] flex flex-col">
        <CardHeader className="shrink-0">
          <div className="flex items-center justify-between">
            <CardTitle>Agent Handoff</CardTitle>
            <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
              <X size={16} />
            </button>
          </div>
          <CardDescription>
            Copy the handoff Markdown or export the full package for &quot;{projectName}&quot;.
          </CardDescription>
        </CardHeader>

        <CardContent className="flex-1 min-h-0 pb-0">
          {loading ? (
            <div className="flex h-full items-center justify-center">
              <Loader2 size={20} className="animate-spin text-muted-foreground" />
            </div>
          ) : (
            <textarea
              value={handoffMd}
              onChange={(event) => setHandoffMd(event.target.value)}
              className="h-full min-h-0 w-full resize-none rounded-md border bg-input px-3 py-2 font-mono text-xs text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            />
          )}
        </CardContent>

        <CardFooter className="shrink-0 justify-end gap-2 pt-3">
          <Button variant="ghost" onClick={onClose} disabled={exporting}>Cancel</Button>
          <Button variant="secondary" onClick={handleCopy} disabled={loading || !handoffMd}>
            {copied ? <><Check size={14} /> Copied</> : <><Clipboard size={14} /> Copy Markdown</>}
          </Button>
          <Button onClick={handleExport} disabled={exporting || loading || !handoffMd}>
            {exporting ? <><Loader2 size={14} className="animate-spin" /> Exporting...</> : <><Download size={14} /> Export Handoff ZIP</>}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
