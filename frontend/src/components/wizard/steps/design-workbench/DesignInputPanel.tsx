"use client";

import { useState } from "react";
import { ArrowRight, Check, FileText, ImagePlus, Loader2, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import type { DesignWorkbench } from "@/lib/api";

interface DesignInputPanelProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  working: boolean;
  completing: boolean;
  blocked: boolean;
  onComplete: () => void;
}

export function DesignInputPanel({
  projectId,
  workbench,
  working,
  completing,
  blocked,
  onComplete,
}: DesignInputPanelProps) {
  const saveInput = useDesignWorkbenchStore((s) => s.saveInput);
  const analyzeImage = useDesignWorkbenchStore((s) => s.analyzeImage);
  const generate = useDesignWorkbenchStore((s) => s.generate);
  const analyzingImage = useDesignWorkbenchStore((s) => s.analyzingImage);
  const [description, setDescription] = useState(workbench?.description ?? "");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);

  const hasExistingImage = Boolean(workbench?.imageInput);
  const canGenerate = Boolean(description.trim() || imageFile || hasExistingImage);
  const hasCurrentDesign = Boolean(workbench?.currentDesign);
  const actionDisabled = working || completing || analyzingImage;
  const completeDisabled = blocked || working || completing || analyzingImage || !hasCurrentDesign;

  async function handleGenerate() {
    if (!canGenerate || actionDisabled) return;
    const descriptionChanged = description.trim() !== (workbench?.description ?? "");
    const shouldSaveInput = Boolean(imageFile || descriptionChanged || !workbench);
    let shouldAnalyze = Boolean(imageFile);
    if (shouldSaveInput) {
      await saveInput(projectId, description, imageFile);
    }
    if (useDesignWorkbenchStore.getState().error) return;

    const latest = useDesignWorkbenchStore.getState().workbench;
    if (latest?.imageInput && !latest.imageAnalysis) {
      shouldAnalyze = true;
    }
    if (shouldAnalyze) {
      const analyzed = await analyzeImage(projectId);
      if (!analyzed) return;
    }

    setImageFile(null);
    setImageInputKey((current) => current + 1);
    await generate(projectId);
  }

  const generateLabel = hasCurrentDesign ? "Neu generieren" : "Design generieren";

  return (
    <aside className="flex min-h-[360px] flex-col overflow-hidden rounded-md border border-border bg-card">
      <div className="flex h-11 shrink-0 items-center gap-2 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileText size={15} className="shrink-0 text-muted-foreground" />
          <h2 className="truncate text-xs font-semibold text-foreground">Design Generator</h2>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto p-3">
        <div className="grid gap-1.5">
          <Label htmlFor="design-description">Designbeschreibung</Label>
          <Textarea
            id="design-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Visuelle Richtung, Zielgruppe, Tonalitaet oder wichtige Inhalte..."
            disabled={actionDisabled}
            className="min-h-36 resize-none text-sm"
          />
        </div>

        <div className="grid gap-1.5">
          <Label htmlFor="design-image">Bildreferenz</Label>
          <Input
            id="design-image"
            key={imageInputKey}
            type="file"
            accept="image/*"
            disabled={actionDisabled}
            onChange={(event) => setImageFile(event.target.files?.[0] ?? null)}
            className="h-9 text-xs"
          />
          {(imageFile || workbench?.imageInput) && (
            <div className="flex min-h-7 items-center gap-2 rounded-md bg-secondary px-2 text-[11px] text-muted-foreground">
              <ImagePlus size={12} className="shrink-0" />
              <span className="min-w-0 truncate">
                {imageFile?.name ?? workbench?.imageInput?.originalName}
              </span>
            </div>
          )}
        </div>

        {analyzingImage && (
          <div className="rounded-md border border-border bg-background p-3 text-xs text-muted-foreground">
            Bild wird analysiert...
          </div>
        )}

        {workbench?.imageAnalysis && !analyzingImage && (
          <div className="grid gap-2 rounded-md border border-border bg-background p-3 text-xs">
            <div className="min-w-0">
              <div className="mb-1 font-medium text-foreground">Bildanalyse</div>
              <p className="line-clamp-3 break-words text-muted-foreground">
                {workbench.imageAnalysis.summary}
              </p>
            </div>
            {workbench.imageAnalysis.palette.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {workbench.imageAnalysis.palette.slice(0, 6).map((color) => (
                  <span
                    key={`${color.hex}-${color.role}`}
                    title={`${color.role}: ${color.hex}`}
                    className="h-5 w-5 shrink-0 rounded border border-border"
                    style={{ backgroundColor: color.hex }}
                  />
                ))}
              </div>
            )}
            {workbench.imageAnalysis.layoutHierarchy.length > 0 && (
              <div className="min-w-0 text-[11px] leading-4 text-muted-foreground">
                <span className="font-medium text-foreground/80">Layout: </span>
                <span className="break-words">
                  {workbench.imageAnalysis.layoutHierarchy.slice(0, 3).map((region) => region.name).join(", ")}
                </span>
              </div>
            )}
          </div>
        )}

        {workbench?.imageAnalysisError && !analyzingImage && (
          <div className="grid gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive">
            <p className="break-words">{workbench.imageAnalysisError}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => analyzeImage(projectId)}
              disabled={actionDisabled}
              className="min-w-0 justify-center whitespace-normal text-xs"
            >
              Analyse erneut versuchen
            </Button>
          </div>
        )}

        {workbench?.analysis && (
          <div className="grid gap-2 rounded-md border border-border bg-background p-3 text-xs">
            <div>
              <div className="mb-1 font-medium text-foreground">Analyse</div>
              <p className="line-clamp-3 text-muted-foreground">{workbench.analysis.summary}</p>
            </div>
            <div className="grid gap-1 text-[11px] leading-4 text-muted-foreground">
              <p className="line-clamp-2">
                <span className="font-medium text-foreground/80">Richtung: </span>
                {workbench.analysis.visualDirection}
              </p>
              <p className="line-clamp-2">
                <span className="font-medium text-foreground/80">Grund: </span>
                {workbench.analysis.rationale}
              </p>
            </div>
          </div>
        )}

        <div className="mt-auto grid gap-2 pt-1">
          <Button
            type="button"
            size="sm"
            onClick={handleGenerate}
            disabled={actionDisabled || !canGenerate}
            className="w-full gap-1.5"
          >
            {working || analyzingImage ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
            {generateLabel}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onComplete}
            disabled={completeDisabled}
            className="w-full gap-1.5"
          >
            {completing ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />}
            Design übernehmen
            <ArrowRight size={13} className="ml-auto" />
          </Button>
        </div>
      </div>
    </aside>
  );
}
