"use client";

import { useEffect, useRef, useState } from "react";
import { Settings } from "lucide-react";
import { useFeatureStore } from "@/lib/stores/feature-store";
import { setProjectGraphMeshEnabled, type Project } from "@/lib/api";
import { cn } from "@/lib/utils";

interface Props {
  project: Project;
  onProjectUpdate: (project: Project) => void;
}

export function GraphMeshToggle({ project, onProjectUpdate }: Props) {
  const { flags, loadFeatures } = useFeatureStore();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => { loadFeatures(); }, [loadFeatures]);

  useEffect(() => {
    if (!open) return;
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    function onEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    document.addEventListener("keydown", onEscape);
    return () => {
      document.removeEventListener("mousedown", onClickOutside);
      document.removeEventListener("keydown", onEscape);
    };
  }, [open]);

  const backendEnabled = flags?.graphmeshEnabled ?? false;
  const checked = project.graphmeshEnabled ?? false;
  const disabled = !backendEnabled || saving;

  async function handleToggle(next: boolean) {
    setError(null);
    setSaving(true);
    try {
      const updated = await setProjectGraphMeshEnabled(project.id, next);
      onProjectUpdate(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Update failed");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center justify-center rounded-md p-1.5 text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
        title="Project settings"
      >
        <Settings size={14} />
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-1 z-50 w-72 rounded-md border bg-card shadow-md p-3">
          <div className="text-xs font-medium mb-2">Projekt-Einstellungen</div>
          <label className={cn("flex items-start gap-2 text-xs", disabled && "opacity-60")}>
            <input
              type="checkbox"
              checked={checked}
              disabled={disabled}
              onChange={(e) => handleToggle(e.target.checked)}
              className="mt-0.5 accent-primary"
            />
            <div>
              <div className="font-medium">GraphMesh aktivieren</div>
              <div className="text-[10px] text-muted-foreground mt-0.5">
                {backendEnabled
                  ? "Dokumente werden zusätzlich an GraphMesh gesendet (RAG)."
                  : "Im Backend deaktiviert (application.yml). GraphMesh kann nicht aktiviert werden."}
              </div>
            </div>
          </label>
          {error && <div className="text-[10px] text-destructive mt-2">{error}</div>}
        </div>
      )}
    </div>
  );
}
