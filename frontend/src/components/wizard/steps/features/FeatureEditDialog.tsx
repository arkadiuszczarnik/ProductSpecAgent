"use client";
import { useEffect, useRef, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { FeatureScope, WizardFeature } from "@/lib/api";

interface FeatureEditDialogProps {
  feature: WizardFeature | null;
  allowedScopes: FeatureScope[];
  open: boolean;
  onClose: () => void;
  onSave: (patch: Partial<WizardFeature>) => void;
  onDelete: () => void;
}

type DraftFeature = Pick<WizardFeature,
  "title" | "description" | "scopes" | "scopeFields">;

function snapshot(f: WizardFeature): DraftFeature {
  return {
    title: f.title,
    description: f.description,
    scopes: [...f.scopes],
    scopeFields: { ...f.scopeFields },
  };
}

function equalDraft(a: DraftFeature, b: DraftFeature): boolean {
  if (a.title !== b.title || a.description !== b.description) return false;
  if (a.scopes.length !== b.scopes.length) return false;
  for (const s of a.scopes) if (!b.scopes.includes(s)) return false;
  const ak = Object.keys(a.scopeFields);
  const bk = Object.keys(b.scopeFields);
  if (ak.length !== bk.length) return false;
  for (const k of ak) if (a.scopeFields[k] !== b.scopeFields[k]) return false;
  return true;
}

export function FeatureEditDialog({
  feature,
  allowedScopes,
  open,
  onClose,
  onSave,
  onDelete,
}: FeatureEditDialogProps) {
  const initialRef = useRef<DraftFeature | null>(null);
  const [draft, setDraft] = useState<DraftFeature | null>(null);

  // Snapshot beim Übergang closed→open
  useEffect(() => {
    if (open && feature) {
      const snap = snapshot(feature);
      initialRef.current = snap;
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDraft(snap);
    }
    if (!open) {
      initialRef.current = null;
      setDraft(null);
    }
  }, [open, feature]);

  // Suppress "unused" until Task 3 wires them.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _used = { onSave, onDelete, allowedScopes };

  if (!draft) {
    return (
      <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
        <DialogContent />
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-3xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Feature bearbeiten</DialogTitle>
          <DialogDescription>{draft.title || "(ohne Titel)"}</DialogDescription>
        </DialogHeader>
        <div className="py-4">
          <p className="text-sm text-muted-foreground">Form folgt in Task 3.</p>
        </div>
        <DialogFooter />
      </DialogContent>
    </Dialog>
  );
}

// Suppress "unused" until Task 3 wires it.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
const _equalDraftUsed = equalDraft;
