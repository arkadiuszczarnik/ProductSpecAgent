"use client";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { getFieldOptionsFromCatalog } from "@/lib/category-step-config";
import { useWizardOptionsStore } from "@/lib/stores/wizard-options-store";

const DEFAULT_OPTIONS = {
  framework: ["Kotlin + Spring Boot", "Node.js + Express", "Python + FastAPI", "Go", "Rust + Actix", "Java + Spring"],
  apiStyle: ["REST", "GraphQL", "gRPC", "WebSockets"],
  auth: ["JWT", "Session", "OAuth 2.0", "API Key", "Keine"],
};

export function BackendForm({ projectId }: { projectId: string }) {
  void projectId;
  const { data, updateField, getCategory } = useWizardStore();
  const catalog = useWizardOptionsStore((state) => state.catalog);
  const fields = data?.steps["BACKEND"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: string | string[]) => updateField("BACKEND", key, val);

  const category = getCategory();
  const options = getFieldOptionsFromCatalog(catalog, category, "BACKEND") ?? DEFAULT_OPTIONS;

  return (
    <div className="space-y-5">
      <FormField label="Sprache / Framework" required>
        <ChipSelect options={options.framework ?? DEFAULT_OPTIONS.framework} value={get("framework")} onChange={(v) => set("framework", v)} />
      </FormField>
      <FormField label="API-Stil" required>
        <ChipSelect options={options.apiStyle ?? DEFAULT_OPTIONS.apiStyle} value={get("apiStyle")} onChange={(v) => set("apiStyle", v)} />
      </FormField>
      <FormField label="Auth-Methode" required>
        <ChipSelect options={options.auth ?? DEFAULT_OPTIONS.auth} value={get("auth")} onChange={(v) => set("auth", v)} />
      </FormField>
    </div>
  );
}
