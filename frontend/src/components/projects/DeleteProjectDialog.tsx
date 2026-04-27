"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { deleteProject } from "@/lib/api";

interface DeleteProjectDialogProps {
  project: { id: string; name: string } | null;
  onClose: () => void;
  onDeleted: (id: string) => void;
}

export function DeleteProjectDialog({ project, onClose, onDeleted }: DeleteProjectDialogProps) {
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  const [prevProjectId, setPrevProjectId] = useState<string | null>(project?.id ?? null);
  if (project?.id !== prevProjectId) {
    setPrevProjectId(project?.id ?? null);
    setDeleting(false);
    setDeleteError(null);
  }

  useEffect(() => {
    if (project) cancelButtonRef.current?.focus();
  }, [project]);

  useEffect(() => {
    if (!project) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !deleting) onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [project, deleting, onClose]);

  if (!project) return null;

  async function handleDelete() {
    if (!project) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await deleteProject(project.id);
      onDeleted(project.id);
      onClose();
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : "Löschen fehlgeschlagen.");
      setDeleting(false);
    }
  }

  function handleBackdropClick() {
    if (!deleting) onClose();
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={handleBackdropClick} />
      <Card className="relative z-10 w-full max-w-md mx-4">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>Projekt löschen?</CardTitle>
            <button
              type="button"
              onClick={onClose}
              disabled={deleting}
              className="text-muted-foreground hover:text-foreground disabled:opacity-50"
              aria-label="Dialog schließen"
            >
              <X size={16} />
            </button>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm">
            Möchtest du <strong>&bdquo;{project.name}&ldquo;</strong> wirklich löschen? Diese Aktion entfernt
            Spec, Decisions, Clarifications, Tasks, Documents und Docs-Scaffold — sie kann nicht
            rückgängig gemacht werden.
          </p>
          {deleteError && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              {deleteError}
            </div>
          )}
        </CardContent>
        <CardFooter className="justify-end gap-2">
          <Button ref={cancelButtonRef} variant="ghost" onClick={onClose} disabled={deleting}>
            Abbrechen
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
            {deleting ? (
              <>
                <Loader2 size={14} className="animate-spin" /> Lösche…
              </>
            ) : (
              <>
                <Trash2 size={14} /> Löschen
              </>
            )}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
