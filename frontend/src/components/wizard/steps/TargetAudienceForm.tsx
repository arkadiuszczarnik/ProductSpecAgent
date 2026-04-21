"use client";
import { FormField } from "../FormField";
import { TagInput } from "../TagInput";
import { useWizardStore } from "@/lib/stores/wizard-store";

export function TargetAudienceForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["TARGET_AUDIENCE"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const getTags = (key: string): string[] => (fields[key] as string[]) ?? [];
  const set = (key: string, val: any) => updateField("TARGET_AUDIENCE", key, val);

  return (
    <div className="space-y-5">
      <FormField label="Primaere Zielgruppe" required>
        <input value={get("primary")} onChange={(e) => set("primary", e.target.value)}
          placeholder="z.B. Product Owner, Startup-Gruender"
          className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
      </FormField>
      <FormField label="Pain Points">
        <TagInput tags={getTags("painPoints")} onAdd={(t) => set("painPoints", [...getTags("painPoints"), t])} onRemove={(t) => set("painPoints", getTags("painPoints").filter((x: string) => x !== t))} placeholder="Pain Point eingeben + Enter" />
      </FormField>
      <FormField label="Sekundaere Zielgruppe">
        <input value={get("secondary")} onChange={(e) => set("secondary", e.target.value)}
          placeholder="Optional"
          className="w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
      </FormField>
    </div>
  );
}
