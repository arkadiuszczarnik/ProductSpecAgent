"use client";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";

export function IdeaForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["IDEA"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: any) => updateField("IDEA", key, val);

  return (
    <div className="space-y-5">
      <FormField label="Produktname" required>
        <Input value={get("productName")} onChange={(e) => set("productName", e.target.value)}
          placeholder="z.B. TaskFlow Pro" />
      </FormField>
      <FormField label="Produktidee / Vision" required>
        <Textarea value={get("vision")} onChange={(e) => set("vision", e.target.value)}
          placeholder="Beschreibe deine Produktidee in 2-3 Saetzen..." rows={4}
          className="resize-y min-h-[100px]" />
      </FormField>
      <FormField label="Kategorie">
        <ChipSelect options={["SaaS", "Mobile App", "CLI Tool", "Library", "Desktop App", "API"]} value={get("category")} onChange={(v) => set("category", v)} />
      </FormField>
    </div>
  );
}
