"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import type { AssetBundleManifest } from "@/lib/api";

interface Props {
  manifest: AssetBundleManifest | null;
  onClose: () => void;
}

export function DeleteBundleDialog({ manifest, onClose }: Props) {
  const { delete: deleteBundle } = useAssetBundleStore();
  const [deleting, setDeleting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (manifest) cancelRef.current?.focus();
  }, [manifest]);

  useEffect(() => {
    if (!manifest) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !deleting) onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [manifest, deleting, onClose]);

  if (!manifest) return null;

  async function handleDelete() {
    if (!manifest) return;
    setDeleting(true);
    setErr(null);
    try {
      await deleteBundle(manifest.step, manifest.field, manifest.value);
      onClose();
    } catch (e) {
      setErr((e as Error).message);
      setDeleting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={() => !deleting && onClose()} />
      <Card className="relative z-10 w-full max-w-md mx-4">
        <CardHeader>
          <CardTitle>Bundle löschen?</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm">
            Bundle <code className="font-mono">{manifest.id}</code> wird komplett aus S3 entfernt.
            Diese Aktion ist nicht rückgängig zu machen.
          </p>
          {err && <p className="mt-3 text-sm text-red-400">{err}</p>}
        </CardContent>
        <CardFooter className="flex justify-end gap-2">
          <Button ref={cancelRef} variant="ghost" onClick={onClose} disabled={deleting}>
            Abbrechen
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
            {deleting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Löschen
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
