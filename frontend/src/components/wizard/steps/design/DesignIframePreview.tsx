"use client";

import { useEffect, useRef, useState } from "react";
import { Eye, AlertCircle } from "lucide-react";

interface Props {
  src: string;          // full URL incl. optional #hash
  reloadKey: string;    // bump to force iframe re-mount
}

export function DesignIframePreview({ src, reloadKey }: Props) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [loaded, setLoaded] = useState(false);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    setLoaded(false);
    setTimedOut(false);
    const t = setTimeout(() => {
      if (!loaded) setTimedOut(true);
    }, 5000);
    return () => clearTimeout(t);
  }, [src, reloadKey, loaded]);

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b border-border bg-muted px-3 py-1.5 text-xs text-muted-foreground">
        <Eye size={11} />
        <span>Vorschau aus Upload — Interaktionen werden nicht gespeichert</span>
      </div>
      <div className="relative flex-1">
        <iframe
          ref={iframeRef}
          key={reloadKey}
          src={src}
          sandbox="allow-scripts allow-same-origin"
          onLoad={() => setLoaded(true)}
          className="h-full w-full border-0"
          style={{ minHeight: "70vh" }}
        />
        {timedOut && !loaded && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-background/80 p-6 text-center text-sm text-muted-foreground">
            <AlertCircle size={20} />
            <p>Vorschau konnte nicht geladen werden — siehe Files-Liste</p>
          </div>
        )}
      </div>
    </div>
  );
}
