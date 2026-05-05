"use client";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { Textarea } from "@/components/ui/textarea";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { getFieldOptions } from "@/lib/category-step-config";

const DEFAULT_OPTIONS = {
  architecture: ["Monolith", "Microservices", "Serverless", "Hybrid"],
  database: ["PostgreSQL", "MongoDB", "SQLite", "MySQL", "Filesystem", "Redis"],
  deployment: ["Docker Compose", "Vercel + Cloud", "Self-hosted", "Kubernetes", "AWS"],
};

export function ArchitectureForm({ projectId }: { projectId: string }) {
  const { data, updateField, getCategory } = useWizardStore();
  const fields = data?.steps["ARCHITECTURE"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: any) => updateField("ARCHITECTURE", key, val);

  const category = getCategory();
  const options = getFieldOptions(category, "ARCHITECTURE") ?? DEFAULT_OPTIONS;

  return (
    <div className="space-y-5">
      <FormField label="System-Architektur" required>
        <ChipSelect options={options.architecture ?? DEFAULT_OPTIONS.architecture} value={get("architecture")} onChange={(v) => set("architecture", v)} />
      </FormField>
      <FormField label="Datenbank" required>
        <ChipSelect options={options.database ?? DEFAULT_OPTIONS.database} value={get("database")} onChange={(v) => set("database", v)} />
      </FormField>
      <FormField label="Deployment" required>
        <ChipSelect options={options.deployment ?? DEFAULT_OPTIONS.deployment} value={get("deployment")} onChange={(v) => set("deployment", v)} />
      </FormField>
      <FormField label="Architektur-Notizen">
        <Textarea value={get("notes")} onChange={(e) => set("notes", e.target.value)}
          placeholder="Zusaetzliche Architektur-Details..." rows={3}
          className="resize-y" />
      </FormField>
    </div>
  );
}
