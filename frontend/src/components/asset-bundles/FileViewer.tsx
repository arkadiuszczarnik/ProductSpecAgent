"use client";

import { useEffect, useState } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";

export function FileViewer() {
  const { loadedFile } = useAssetBundleStore();
  const [html, setHtml] = useState<string>("");

  useEffect(() => {
    if (!loadedFile) {
      setHtml("");
      return;
    }
    if (loadedFile.text === undefined) return;

    let cancelled = false;
    renderText(loadedFile.text, loadedFile.contentType).then((rendered) => {
      if (!cancelled) setHtml(rendered);
    });
    return () => { cancelled = true; };
  }, [loadedFile]);

  if (!loadedFile) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Datei aus dem Tree links auswählen.
      </div>
    );
  }

  if (loadedFile.blobUrl && loadedFile.contentType.startsWith("image/")) {
    return (
      <div className="p-4">
        <img src={loadedFile.blobUrl} alt={loadedFile.path} className="max-w-full max-h-[60vh]" />
      </div>
    );
  }

  if (loadedFile.text === undefined) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        Vorschau nicht verfügbar für {loadedFile.contentType}.
      </div>
    );
  }

  return (
    <div className="p-4 overflow-auto text-sm" dangerouslySetInnerHTML={{ __html: html }} />
  );
}

async function renderText(text: string, contentType: string): Promise<string> {
  // Strip charset suffix (e.g. "text/markdown;charset=UTF-8" → "text/markdown")
  const baseCt = contentType.split(";")[0].trim();

  if (baseCt === "text/markdown") {
    const escaped = escapeHtml(text);
    return `<pre class="whitespace-pre-wrap font-mono text-xs">${escaped}</pre>`;
  }

  const language = contentTypeToLang(baseCt);
  try {
    const { codeToHtml } = await import("shiki");
    return await codeToHtml(text, { lang: language, theme: "one-dark-pro" });
  } catch {
    return `<pre class="whitespace-pre-wrap font-mono text-xs">${escapeHtml(text)}</pre>`;
  }
}

function contentTypeToLang(ct: string): string {
  switch (ct) {
    case "application/json": return "json";
    case "application/yaml": return "yaml";
    case "application/typescript": return "typescript";
    case "application/javascript": return "javascript";
    case "text/x-python": return "python";
    default: return "text";
  }
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
