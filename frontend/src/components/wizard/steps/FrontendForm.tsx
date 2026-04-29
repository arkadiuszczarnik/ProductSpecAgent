"use client";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { getFieldOptions } from "@/lib/category-step-config";

const DEFAULT_OPTIONS = {
  framework: ["Next.js + React", "Vue + Nuxt", "Svelte + SvelteKit", "Angular", "Stitch", "Remix", "Astro"],
  uiLibrary: ["shadcn/ui", "Material UI", "Ant Design", "Chakra UI", "Radix + Custom", "Keine"],
  styling: ["Tailwind CSS", "CSS Modules", "Styled Components", "Emotion", "Vanilla CSS"],
  theme: ["Dark only", "Light only", "Both (Toggle)"],
};

export function FrontendForm({ projectId }: { projectId: string }) {
  const { data, updateField, getCategory } = useWizardStore();
  const fields = data?.steps["FRONTEND"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: any) => updateField("FRONTEND", key, val);

  const category = getCategory();
  const options = getFieldOptions(category, "FRONTEND") ?? DEFAULT_OPTIONS;

  return (
    <div className="space-y-5">
      <FormField label="Framework" required>
        <ChipSelect options={options.framework ?? DEFAULT_OPTIONS.framework} value={get("framework")} onChange={(v) => set("framework", v)} />
      </FormField>
      <FormField label="UI Library">
        <ChipSelect options={options.uiLibrary ?? DEFAULT_OPTIONS.uiLibrary} value={get("uiLibrary")} onChange={(v) => set("uiLibrary", v)} />
      </FormField>
      <FormField label="Styling">
        <ChipSelect options={options.styling ?? DEFAULT_OPTIONS.styling} value={get("styling")} onChange={(v) => set("styling", v)} />
      </FormField>
      <FormField label="Theme">
        <ChipSelect options={options.theme ?? DEFAULT_OPTIONS.theme} value={get("theme")} onChange={(v) => set("theme", v)} />
      </FormField>
    </div>
  );
}
