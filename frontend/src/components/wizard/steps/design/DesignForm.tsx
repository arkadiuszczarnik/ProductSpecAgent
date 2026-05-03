"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";
import { useDesignBundleStore } from "@/lib/stores/design-bundle-store";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { DesignDropzone } from "./DesignDropzone";
import { DesignBundleHeader } from "./DesignBundleHeader";
import { DesignPagesList } from "./DesignPagesList";
import { DesignIframePreview } from "./DesignIframePreview";
import { DesignReplaceConfirmDialog } from "./DesignReplaceConfirmDialog";
import type { DesignPage } from "@/lib/api";

interface Props {
  projectId: string;
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081";

export function DesignForm({ projectId }: Props) {
  const { bundle, loading, uploading, error, loadBundle, uploadBundle, deleteBundle } = useDesignBundleStore();
  const completeStep = useWizardStore((s) => s.completeStep);
  const completing = useWizardStore((s) => s.chatPending);

  const [activePageId, setActivePageId] = useState<string | null>(null);
  const [replaceOpen, setReplaceOpen] = useState(false);

  // iframeKey derived from bundle.uploadedAt + activePageId — no Effect needed
  const iframeKey = bundle
    ? activePageId
      ? `${bundle.uploadedAt}-${activePageId}`
      : bundle.uploadedAt
    : "initial";

  useEffect(() => {
    loadBundle(projectId);
  }, [projectId, loadBundle]);

  function handleSelectPage(p: DesignPage) {
    setActivePageId(p.id);
    // Iframe-navigation by hash; falls back to noop if canvas doesn't honor it
    // iframeKey updates automatically via derived state, forcing re-mount
  }

  async function handleSkip() {
    await completeStep(projectId, "DESIGN");
  }

  async function handleComplete() {
    await completeStep(projectId, "DESIGN");
  }

  function handleReplace() {
    setReplaceOpen(true);
  }

  function handleConfirmReplace() {
    setReplaceOpen(false);
    // Trigger upload UI: open the dropzone's hidden picker via event
    // simplest: reuse Dropzone by hiding header temporarily
    deleteBundle(projectId).then(() => {
      // After delete, store.bundle becomes null → empty-state shows
    });
  }

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 size={20} className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!bundle) {
    return (
      <DesignDropzone
        uploading={uploading}
        error={error}
        onPick={(file) => uploadBundle(projectId, file)}
        onSkip={handleSkip}
      />
    );
  }

  const iframeSrc = activePageId
    ? `${API_BASE}${bundle.entryUrl}#${activePageId}`
    : `${API_BASE}${bundle.entryUrl}`;

  return (
    <div className="flex h-full flex-col">
      <DesignBundleHeader
        bundle={bundle}
        onReplace={handleReplace}
        onDelete={() => deleteBundle(projectId)}
      />
      <div className="flex flex-1 overflow-hidden">
        <div className="w-60 shrink-0 overflow-y-auto border-r border-border bg-card">
          <DesignPagesList
            pages={bundle.pages}
            activeId={activePageId}
            onSelect={handleSelectPage}
          />
        </div>
        <div className="flex-1 overflow-hidden">
          <DesignIframePreview key={iframeKey} src={iframeSrc} />
        </div>
      </div>
      <div className="flex justify-end border-t border-border bg-card px-4 py-3">
        <Button onClick={handleComplete} disabled={completing}>
          {completing && <Loader2 size={14} className="mr-2 animate-spin" />}
          Step abschließen
        </Button>
      </div>
      <DesignReplaceConfirmDialog
        open={replaceOpen}
        bundleName={bundle.originalFilename}
        onConfirm={handleConfirmReplace}
        onCancel={() => setReplaceOpen(false)}
      />
    </div>
  );
}
