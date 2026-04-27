"use client";

import { useState, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { X, Loader2, FileText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { readProjectFile, type FileContent } from "@/lib/api";
import { cn } from "@/lib/utils";

interface OpenTab {
  path: string;
  name: string;
  content: FileContent | null;
  loading: boolean;
  html: string;
}

interface SpecFileViewerProps {
  projectId: string;
  initialPath: string;
  open: boolean;
  onClose: () => void;
}

export function SpecFileViewer({ projectId, initialPath, open, onClose }: SpecFileViewerProps) {
  const [tabs, setTabs] = useState<OpenTab[]>([]);
  const [activeTab, setActiveTab] = useState<string>("");

  // Open a tab for the initial path
  useEffect(() => {
    if (!open || !initialPath) return;
    openTab(initialPath);
  }, [open, initialPath]); // eslint-disable-line react-hooks/exhaustive-deps

  const openTab = useCallback(async (path: string) => {
    // If tab already exists, just activate it
    const existing = tabs.find((t) => t.path === path);
    if (existing) {
      setActiveTab(path);
      return;
    }

    const name = path.split("/").pop() ?? path;
    const newTab: OpenTab = { path, name, content: null, loading: true, html: "" };
    setTabs((prev) => [...prev, newTab]);
    setActiveTab(path);

    try {
      const content = await readProjectFile(projectId, path);
      const html = content.binary
        ? `<div class="flex h-full flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground"><div class="text-sm font-medium">Binärdatei</div><div class="text-xs">Keine Inline-Vorschau für ${escapeHtml(content.name)}.</div></div>`
        : await highlightCode(content.content, content.language);
      setTabs((prev) =>
        prev.map((t) => (t.path === path ? { ...t, content, html, loading: false } : t))
      );
    } catch {
      setTabs((prev) =>
        prev.map((t) => (t.path === path ? { ...t, loading: false, html: "<pre>Failed to load file</pre>" } : t))
      );
    }
  }, [tabs, projectId]);

  function closeTab(path: string) {
    const newTabs = tabs.filter((t) => t.path !== path);
    setTabs(newTabs);
    if (activeTab === path) {
      setActiveTab(newTabs.length > 0 ? newTabs[newTabs.length - 1].path : "");
    }
    if (newTabs.length === 0) onClose();
  }

  useEffect(() => {
    if (!open) return;
    function handleEscape(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleEscape);
    return () => window.removeEventListener("keydown", handleEscape);
  }, [open, onClose]);

  if (!open || tabs.length === 0) return null;

  const currentTab = tabs.find((t) => t.path === activeTab);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative z-10 w-full max-w-3xl mx-4 max-h-[80vh] flex flex-col rounded-xl border bg-card overflow-hidden">
        {/* Tab bar */}
        <div className="flex items-center border-b bg-card shrink-0 overflow-x-auto">
          {tabs.map((tab) => (
            <div
              key={tab.path}
              className={cn(
                "flex items-center gap-1.5 px-3 py-2 text-xs border-r cursor-pointer shrink-0 transition-colors",
                activeTab === tab.path ? "bg-background text-foreground" : "text-muted-foreground hover:text-foreground"
              )}
              onClick={() => setActiveTab(tab.path)}
            >
              <FileText size={12} />
              <span>{tab.name}</span>
              <button
                onClick={(e) => { e.stopPropagation(); closeTab(tab.path); }}
                className="ml-1 hover:text-destructive"
              >
                <X size={11} />
              </button>
            </div>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto min-h-0">
          {currentTab?.loading ? (
            <div className="flex h-full items-center justify-center">
              <Loader2 size={20} className="animate-spin text-muted-foreground" />
            </div>
          ) : currentTab?.html ? (
            <div
              className="p-4 text-xs font-mono leading-relaxed [&_pre]:!bg-transparent [&_pre]:!p-0 [&_code]:!text-xs"
              dangerouslySetInnerHTML={{ __html: currentTab.html }}
            />
          ) : null}
        </div>

        {/* Footer */}
        {currentTab?.content && (
          <div className="flex items-center gap-3 border-t px-4 py-1.5 text-[10px] text-muted-foreground shrink-0">
            <Badge variant="ghost">{currentTab.content.binary ? "binary" : currentTab.content.language}</Badge>
            {!currentTab.content.binary && <span>{currentTab.content.lineCount} lines</span>}
            {!currentTab.content.binary && <span>UTF-8</span>}
            <span className="ml-auto">{currentTab.content.path}</span>
          </div>
        )}
      </div>
    </div>,
    document.body
  );
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

async function highlightCode(code: string, language: string): Promise<string> {
  try {
    const { codeToHtml } = await import("shiki");
    // Map our language names to shiki language IDs
    const langMap: Record<string, string> = {
      kotlin: "kotlin",
      groovy: "groovy",
      json: "json",
      yaml: "yaml",
      markdown: "markdown",
      properties: "ini",
      xml: "xml",
      typescript: "typescript",
      javascript: "javascript",
      css: "css",
      html: "html",
      dockerfile: "dockerfile",
      text: "text",
    };
    const shikiLang = langMap[language] ?? "text";

    return await codeToHtml(code, {
      lang: shikiLang,
      theme: "one-dark-pro",
    });
  } catch {
    // Fallback: plain text with escaping
    const escaped = code.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return `<pre><code>${escaped}</code></pre>`;
  }
}
