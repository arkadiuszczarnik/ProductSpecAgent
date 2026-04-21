"use client";
import { FormField } from "../FormField";
import { TagInput } from "../TagInput";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { cn } from "@/lib/utils";

export function ProblemForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["PROBLEM"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const getTags = (key: string): string[] => (fields[key] as string[]) ?? [];
  const set = (key: string, val: any) => updateField("PROBLEM", key, val);

  return (
    <div className="space-y-5">
      <FormField label="Kernproblem" required>
        <textarea value={get("coreProblem")} onChange={(e) => set("coreProblem", e.target.value)}
          placeholder="Welches Problem loest dein Produkt?" rows={3}
          className="w-full resize-y rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]" />
      </FormField>
      <FormField label="Primäre Zielgruppe" required>
        <input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gründer"
          className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
      </FormField>
      <FormField label="Pain Points">
        <TagInput tags={getTags("painPoints")}
          onAdd={(t) => set("painPoints", [...getTags("painPoints"), t])}
          onRemove={(t) => set("painPoints", getTags("painPoints").filter((x: string) => x !== t))}
          placeholder="Pain Point eingeben + Enter" />
      </FormField>
    </div>
  );
}
