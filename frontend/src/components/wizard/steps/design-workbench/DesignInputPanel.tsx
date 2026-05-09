"use client";

import { useState } from "react";
import { Code2, FileText, ImagePlus, Loader2, Plus, Save, ScanLine } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useDesignWorkbenchStore } from "@/lib/stores/design-workbench-store";
import type { DesignInput, DesignInputCategory, DesignWorkbench } from "@/lib/api";

interface DesignInputPanelProps {
  projectId: string;
  workbench: DesignWorkbench | null;
  working: boolean;
}

function inputLabel(input: DesignInput) {
  if (input.userLabel) return input.userLabel;
  if (input.originalName) return input.originalName;
  if (input.kind === "TEXT") return "Textbeschreibung";
  if (input.kind === "IMAGE") return "Bildreferenz";
  return "HTML/CSS Referenz";
}

const inputCategories: DesignInputCategory[] = [
  "REFERENCE_IMAGE",
  "ASSET_IMAGE",
  "HTML_CSS_REFERENCE",
  "UNCLEAR",
];

function DesignInputCard({ projectId, input, working }: { projectId: string; input: DesignInput; working: boolean }) {
  const updateInput = useDesignWorkbenchStore((s) => s.updateInput);
  const [label, setLabel] = useState(input.userLabel ?? "");
  const [category, setCategory] = useState<DesignInputCategory>(input.classification?.category ?? "UNCLEAR");

  async function handleSave() {
    await updateInput(projectId, input.id, {
      userLabel: label.trim() || null,
      category,
      summary: input.classification?.summary ?? "User supplied classification",
      suggestedUse: input.classification?.suggestedUse ?? "Use as a manually curated design reference.",
      confidence: input.classification?.confidence ?? 1,
    });
  }

  return (
    <div className="rounded-md border border-border bg-background p-2">
      <div className="mb-2 flex items-center justify-between gap-2">
        <span className="min-w-0 truncate text-xs font-medium text-foreground">{inputLabel(input)}</span>
        <Badge variant="ghost" className="shrink-0 text-[10px]">
          {input.kind}
        </Badge>
      </div>
      <div className="grid gap-1.5 sm:grid-cols-[1fr_10rem_auto]">
        <Input
          aria-label="Input label"
          value={label}
          onChange={(event) => setLabel(event.target.value)}
          placeholder="Label"
          disabled={working}
          className="h-8 text-xs"
        />
        <select
          aria-label="Input category"
          value={category}
          onChange={(event) => setCategory(event.target.value as DesignInputCategory)}
          disabled={working}
          className="h-8 rounded-md border border-input bg-background px-2 text-xs text-foreground shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        >
          {inputCategories.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
        <Button
          type="button"
          variant="outline"
          size="xs"
          onClick={handleSave}
          disabled={working}
          className="h-8 gap-1"
        >
          <Save size={12} />
          Save
        </Button>
      </div>
      {input.classification ? (
        <div className="mt-2 space-y-1 text-[11px] leading-4 text-muted-foreground">
          <p className="line-clamp-2">{input.classification.summary}</p>
          <p className="line-clamp-2 text-foreground/80">{input.classification.suggestedUse}</p>
        </div>
      ) : (
        <p className="mt-2 text-[11px] text-muted-foreground">Noch nicht analysiert.</p>
      )}
    </div>
  );
}

export function DesignInputPanel({ projectId, workbench, working }: DesignInputPanelProps) {
  const addTextInput = useDesignWorkbenchStore((s) => s.addTextInput);
  const addImageInput = useDesignWorkbenchStore((s) => s.addImageInput);
  const addSnippetInput = useDesignWorkbenchStore((s) => s.addSnippetInput);
  const analyze = useDesignWorkbenchStore((s) => s.analyze);
  const [text, setText] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);
  const [snippetName, setSnippetName] = useState("");
  const [snippet, setSnippet] = useState("");

  async function handleAddText() {
    const trimmed = text.trim();
    if (!trimmed) return;
    await addTextInput(projectId, trimmed);
    if (!useDesignWorkbenchStore.getState().error) setText("");
  }

  async function handleAddImage() {
    if (!imageFile) return;
    await addImageInput(projectId, imageFile);
    if (!useDesignWorkbenchStore.getState().error) {
      setImageFile(null);
      setImageInputKey((current) => current + 1);
    }
  }

  async function handleAddSnippet() {
    const trimmed = snippet.trim();
    if (!trimmed) return;
    await addSnippetInput(projectId, trimmed, snippetName.trim() || undefined);
    if (!useDesignWorkbenchStore.getState().error) {
      setSnippet("");
      setSnippetName("");
    }
  }

  return (
    <aside className="flex min-h-[280px] flex-col overflow-hidden rounded-md border border-border bg-card">
      <div className="flex h-11 shrink-0 items-center justify-between gap-2 border-b border-border px-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileText size={15} className="shrink-0 text-muted-foreground" />
          <h2 className="truncate text-xs font-semibold text-foreground">Inputs</h2>
        </div>
        <Button
          type="button"
          variant="outline"
          size="xs"
          onClick={() => analyze(projectId)}
          disabled={working || !workbench?.inputs.length}
          className="gap-1.5"
        >
          {working ? <Loader2 size={12} className="animate-spin" /> : <ScanLine size={12} />}
          Analysieren
        </Button>
      </div>

      <div className="flex shrink-0 flex-col gap-2 border-b border-border p-3">
        <Textarea
          aria-label="Designbeschreibung"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="UI-Richtung, Referenzen, gewuenschte Screens..."
          disabled={working}
          className="min-h-24 resize-none text-sm"
        />
        <Button
          type="button"
          size="sm"
          onClick={handleAddText}
          disabled={working || !text.trim()}
          className="w-full gap-1.5"
        >
          {working ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
          Hinzufuegen
        </Button>
        <div className="grid gap-2 sm:grid-cols-[1fr_auto]">
          <Input
            key={imageInputKey}
            aria-label="Bildreferenz"
            type="file"
            accept="image/*"
            disabled={working}
            onChange={(event) => setImageFile(event.target.files?.[0] ?? null)}
            className="h-9 text-xs"
          />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleAddImage}
            disabled={working || !imageFile}
            className="gap-1.5"
          >
            <ImagePlus size={14} />
            Bild
          </Button>
        </div>
        <div className="grid gap-2 sm:grid-cols-[10rem_1fr_auto]">
          <Input
            aria-label="Snippet name"
            value={snippetName}
            onChange={(event) => setSnippetName(event.target.value)}
            placeholder="snippet.html"
            disabled={working}
            className="h-9 text-xs"
          />
          <Textarea
            aria-label="HTML CSS snippet"
            value={snippet}
            onChange={(event) => setSnippet(event.target.value)}
            placeholder="<style>...</style><main>...</main>"
            disabled={working}
            className="min-h-9 resize-y text-xs"
          />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleAddSnippet}
            disabled={working || !snippet.trim()}
            className="gap-1.5"
          >
            <Code2 size={14} />
            Snippet
          </Button>
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {workbench?.inputs.length ? (
          <div className="flex flex-col gap-2">
            {workbench.inputs.map((input) => (
              <DesignInputCard
                key={`${input.id}:${input.userLabel ?? ""}:${input.classification?.category ?? "UNCLEAR"}`}
                projectId={projectId}
                input={input}
                working={working}
              />
            ))}
          </div>
        ) : (
          <div className="flex h-full min-h-24 items-center justify-center rounded-md border border-dashed border-border px-3 text-center text-xs text-muted-foreground">
            Beschreibe die visuelle Richtung.
          </div>
        )}
      </div>
    </aside>
  );
}
