"use client";

import { useEffect } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { DesignCanvasPreview } from "./DesignCanvasPreview";
import { DesignControlPanel } from "./DesignControlPanel";
import { DesignInputPanel } from "./DesignInputPanel";

interface DesignWorkbenchFormProps {
  projectId: string;
}

export function DesignWorkbenchForm({ projectId }: DesignWorkbenchFormProps) {
  const { workbench, selectedScreenId, loading, working, error, load } = useDesignWorkbenchStore();
  const completeStep = useWizardStore((s) => s.completeStep);
  const completing = useWizardStore((s) => s.chatPending);

  useEffect(() => {
    if (!workbench || workbench.projectId !== projectId) {
      load(projectId);
    }
  }, [load, projectId, workbench]);

  const selectedScreen =
    workbench?.screens.find((screen) => screen.id === selectedScreenId) ?? workbench?.screens[0] ?? null;
  const previewVariant =
    selectedScreen?.variants.find((variant) => variant.id === selectedScreen.activeVariantId) ??
    selectedScreen?.variants.at(-1) ??
    null;

  async function handleComplete() {
    await completeStep(projectId, "DESIGN");
  }

  if (loading && !workbench) {
    return (
      <div className="flex h-full min-h-[420px] items-center justify-center">
        <Loader2 size={20} className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-[620px] flex-col gap-3">
      {error && (
        <div className="flex shrink-0 items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
          <AlertTriangle size={14} />
          <span className="min-w-0 truncate">{error}</span>
        </div>
      )}
      <div className="grid min-h-0 flex-1 gap-3 xl:grid-cols-[280px_minmax(360px,1fr)_300px]">
        <DesignInputPanel projectId={projectId} workbench={workbench} working={working} />
        <DesignCanvasPreview
          projectId={projectId}
          screen={selectedScreen}
          variant={previewVariant}
          working={working}
        />
        <DesignControlPanel
          projectId={projectId}
          workbench={workbench}
          selectedScreen={selectedScreen}
          previewVariant={previewVariant}
          working={working}
          completing={completing}
          onComplete={handleComplete}
        />
      </div>
    </div>
  );
}
