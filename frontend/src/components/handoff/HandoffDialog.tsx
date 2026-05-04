"use client";

import { useState, useEffect } from "react";
import { X, Loader2, Download, Bot, FileText, ListOrdered } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { getHandoffPreview, exportHandoff, type HandoffPreview } from "@/lib/api";
import { cn } from "@/lib/utils";

interface HandoffDialogProps {
  projectId: string;
  projectName: string;
  open: boolean;
  onClose: () => void;
}

const FORMATS = [
  { id: "claude-code", label: "Claude Code" },
  { id: "codex", label: "Codex" },
  { id: "custom", label: "Custom" },
];

type TabKey = "claude" | "agents" | "order";

export function HandoffDialog({ projectId, projectName, open, onClose }: HandoffDialogProps) {
  const [format, setFormat] = useState("claude-code");
  const [preview, setPreview] = useState<HandoffPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [activeTab, setActiveTab] = useState<TabKey>("claude");

  const [claudeMd, setClaudeMd] = useState("");
  const [agentsMd, setAgentsMd] = useState("");
  const [implOrder, setImplOrder] = useState("");

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    getHandoffPreview(projectId, format)
      .then((p) => {
        setPreview(p);
        setClaudeMd(p.claudeMd);
        setAgentsMd(p.agentsMd);
        setImplOrder(p.implementationOrder);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [open, format, projectId]);

  if (!open) return null;

  async function handleExport() {
    setExporting(true);
    try {
      const blob = await exportHandoff(projectId, {
        format,
        claudeMd,
        agentsMd,
        implementationOrder: implOrder,
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

  const tabs: { key: TabKey; label: string; icon: React.ReactNode }[] = [
    { key: "claude", label: "CLAUDE.md", icon: <Bot size={13} /> },
    { key: "agents", label: "AGENTS.md", icon: <FileText size={13} /> },
    { key: "order", label: "Impl Order", icon: <ListOrdered size={13} /> },
  ];

  const currentContent = activeTab === "claude" ? claudeMd : activeTab === "agents" ? agentsMd : implOrder;
  const setCurrentContent = activeTab === "claude" ? setClaudeMd : activeTab === "agents" ? setAgentsMd : setImplOrder;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />
      <Card className="relative z-10 w-full max-w-6xl mx-4 h-[92vh] flex flex-col">
        <CardHeader className="shrink-0">
          <div className="flex items-center justify-between">
            <CardTitle>Agent Handoff</CardTitle>
            <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
              <X size={16} />
            </button>
          </div>
          <CardDescription>
            Generate AI-agent-ready files for &quot;{projectName}&quot;. Edit before export.
          </CardDescription>
          {/* Format selector */}
          <div className="flex items-center gap-2 mt-2">
            <span className="text-xs text-muted-foreground">Format:</span>
            {FORMATS.map((f) => (
              <button
                key={f.id}
                onClick={() => setFormat(f.id)}
                className={cn(
                  "rounded-full px-3 py-1 text-xs font-medium transition-colors",
                  format === f.id ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground hover:text-foreground"
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
        </CardHeader>

        <CardContent className="flex-1 overflow-hidden flex flex-col min-h-0 pb-0">
          {loading ? (
            <div className="flex flex-1 items-center justify-center">
              <Loader2 size={20} className="animate-spin text-muted-foreground" />
            </div>
          ) : (
            <>
              {/* Tabs */}
              <div className="flex border-b mb-3">
                {tabs.map((t) => (
                  <button
                    key={t.key}
                    onClick={() => setActiveTab(t.key)}
                    className={cn(
                      "flex items-center gap-1.5 px-3 py-2 text-xs font-medium transition-colors",
                      activeTab === t.key ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    {t.icon} {t.label}
                  </button>
                ))}
              </div>

              {/* Editable content */}
              <textarea
                value={currentContent}
                onChange={(e) => setCurrentContent(e.target.value)}
                className={cn(
                  "flex-1 min-h-0 w-full resize-none rounded-md border bg-input px-3 py-2 text-xs font-mono text-foreground",
                  "focus:outline-none focus:ring-2 focus:ring-ring"
                )}
              />
            </>
          )}
        </CardContent>

        <CardFooter className="shrink-0 justify-end gap-2 pt-3">
          <Button variant="ghost" onClick={onClose} disabled={exporting}>Cancel</Button>
          <Button onClick={handleExport} disabled={exporting || loading}>
            {exporting ? <><Loader2 size={14} className="animate-spin" /> Exporting...</> : <><Download size={14} /> Export Handoff ZIP</>}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
