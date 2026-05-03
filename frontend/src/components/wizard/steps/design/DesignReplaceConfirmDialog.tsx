"use client";

import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

interface Props {
  open: boolean;
  bundleName: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function DesignReplaceConfirmDialog({ open, bundleName, onConfirm, onCancel }: Props) {
  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onCancel(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Design-Bundle ersetzen?</DialogTitle>
          <DialogDescription>
            Das vorhandene Bundle &quot;{bundleName}&quot; wird vollständig
            ersetzt. Falls du den Step bereits abgeschlossen hast, wird
            <code className="mx-1 rounded bg-muted px-1">design.md</code>
            beim erneuten Step-Complete neu generiert.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={onCancel}>Abbrechen</Button>
          <Button onClick={onConfirm}>Ersetzen</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
