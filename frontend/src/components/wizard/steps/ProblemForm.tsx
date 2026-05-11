"use client";
import { FormField } from "../FormField";
import { TagInput } from "../TagInput";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useWizardStore } from "@/lib/stores/wizard-store";

export function ProblemForm({ projectId }: { projectId: string }) {
  void projectId;
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["PROBLEM"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const getTags = (key: string): string[] => (fields[key] as string[]) ?? [];
  const set = (key: string, val: unknown) => updateField("PROBLEM", key, val);

  return (
    <div className="space-y-5">
      <FormField label="Kernproblem" required>
        <Textarea value={get("coreProblem")} onChange={(e) => set("coreProblem", e.target.value)}
          placeholder="Welches Problem loest dein Produkt?" rows={3}
          className="resize-y min-h-[80px]" />
      </FormField>
      <FormField label="Primäre Zielgruppe" required>
        <Input value={get("primaryAudience")} onChange={(e) => set("primaryAudience", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gründer" />
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
