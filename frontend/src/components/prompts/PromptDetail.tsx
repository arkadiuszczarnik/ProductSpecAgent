"use client";
import { useEffect, useRef, useState } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { markdown, markdownLanguage } from "@codemirror/lang-markdown";
import { languages } from "@codemirror/language-data";
import { basicDark } from "@uiw/codemirror-theme-basic";
import { Button } from "@/components/ui/button";
import {
  ApiError,
  getPrompt,
  resetPrompt,
  savePrompt,
  type PromptDetail as PromptDetailDTO,
} from "@/lib/api";

interface Props {
  id: string;
  onChange: () => void;
}

export function PromptDetail({ id, onChange }: Props) {
  const [detail, setDetail] = useState<PromptDetailDTO | null>(null);
  const [draft, setDraft] = useState("");
  const initialRef = useRef("");
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);

  useEffect(() => {
    setDetail(null);
    setDraft("");
    setErrors([]);
    getPrompt(id).then((d) => {
      setDetail(d);
      setDraft(d.content);
      initialRef.current = d.content;
    });
  }, [id]);

  const isDirty = detail !== null && draft !== initialRef.current;

  async function handleSave() {
    setSaving(true);
    setErrors([]);
    try {
      await savePrompt(id, draft);
      initialRef.current = draft;
      onChange();
      const fresh = await getPrompt(id);
      setDetail(fresh);
    } catch (e) {
      if (
        e instanceof ApiError &&
        e.status === 400 &&
        e.body &&
        typeof e.body === "object" &&
        "errors" in e.body
      ) {
        setErrors((e.body as { errors: string[] }).errors);
      } else {
        setErrors(["Speichern fehlgeschlagen. Bitte versuche es erneut."]);
      }
    } finally {
      setSaving(false);
    }
  }

  async function handleReset() {
    if (!detail?.isOverridden) return;
    if (!window.confirm("Prompt auf Default zurücksetzen?")) return;
    await resetPrompt(id);
    const fresh = await getPrompt(id);
    setDetail(fresh);
    setDraft(fresh.content);
    initialRef.current = fresh.content;
    setErrors([]);
    onChange();
  }

  if (!detail) {
    return <div className="p-8 text-sm text-muted-foreground">Lade…</div>;
  }

  return (
    <div className="p-6 flex flex-col gap-4 h-full">
      <div>
        <h2 className="text-lg font-semibold">{detail.title}</h2>
        <p className="text-xs text-muted-foreground mt-1">{detail.description}</p>
        <p className="text-xs text-muted-foreground mt-0.5">Agent: {detail.agent}</p>
      </div>

      {errors.length > 0 && (
        <div className="rounded-md border border-destructive bg-destructive/10 p-3 text-sm text-destructive">
          <div className="font-semibold mb-1">Speichern fehlgeschlagen — Validierung</div>
          <ul className="list-disc pl-5 space-y-0.5">
            {errors.map((err, i) => (
              <li key={i}>{err}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex-1 min-h-0 border rounded-md overflow-hidden">
        <CodeMirror
          value={draft}
          height="100%"
          theme={basicDark}
          extensions={[markdown({ base: markdownLanguage, codeLanguages: languages })]}
          onChange={(val) => setDraft(val)}
        />
      </div>

      <div className="flex justify-between items-center">
        <Button
          variant="outline"
          size="sm"
          onClick={handleReset}
          disabled={!detail.isOverridden}
          title={detail.isOverridden ? undefined : "Es gibt keinen Override zum Zurücksetzen"}
        >
          Reset auf Default
        </Button>
        <div className="flex gap-2 items-center">
          {isDirty && <span className="text-xs text-muted-foreground">Ungespeicherte Änderungen</span>}
          <Button onClick={handleSave} disabled={saving || !draft.trim()}>
            {saving ? "Speichere…" : "Speichern"}
          </Button>
        </div>
      </div>
    </div>
  );
}
