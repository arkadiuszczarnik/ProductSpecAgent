"use client";

import { useEffect } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { DesignCanvasPreview } from "./DesignCanvasPreview";
import { DesignInputPanel } from "./DesignInputPanel";

interface DesignWorkbenchFormProps {
  projectId: string;
  isBlocked?: boolean;
}

export function DesignWorkbenchForm({ projectId, isBlocked = false }: DesignWorkbenchFormProps) {
  const { workbench, working, error, load } = useDesignWorkbenchStore();
  const completeStep = useWizardStore((s) => s.completeStep);
  const completing = useWizardStore((s) => s.chatPending);
  const readyWorkbench = workbench?.projectId === projectId ? workbench : null;

  useEffect(() => {
    if (!readyWorkbench) {
      load(projectId);
    }
  }, [load, projectId, readyWorkbench]);

  async function handleComplete() {
    if (isBlocked || working || completing || !readyWorkbench?.currentDesign) return;
    await completeStep(projectId, "DESIGN");
  }

  if (!readyWorkbench) {
    return (
      <div className="flex h-full min-h-[420px] min-w-0 flex-col gap-3">
        {error ? (
          <div className="flex shrink-0 items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
            <AlertTriangle size={14} />
            <span className="min-w-0 truncate">{error}</span>
          </div>
        ) : null}
        <div className="flex flex-1 items-center justify-center">
          <Loader2 size={20} className="animate-spin text-muted-foreground" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-[620px] min-w-0 flex-col gap-3">
      {error && (
        <div className="flex shrink-0 items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
          <AlertTriangle size={14} />
          <span className="min-w-0 truncate">{error}</span>
        </div>
      )}
      <div className="grid min-h-0 min-w-0 flex-1 gap-3 lg:grid-cols-[minmax(20rem,24rem)_minmax(0,1fr)]">
        <DesignInputPanel
          projectId={projectId}
          workbench={readyWorkbench}
          working={working}
          completing={completing}
          blocked={isBlocked}
          onComplete={handleComplete}
        />
        <DesignCanvasPreview projectId={projectId} workbench={readyWorkbench} working={working} />
      </div>
    </div>
  );
}
