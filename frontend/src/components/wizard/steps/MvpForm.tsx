"use client";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { Textarea } from "@/components/ui/textarea";
import { useWizardStore } from "@/lib/stores/wizard-store";

interface FeatureLike {
  id: string;
  title: string;
}

export function MvpForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["MVP"]?.fields ?? {};
  const get = (key: string) => (fields[key] as string) ?? "";
  const set = (key: string, val: unknown) => updateField("MVP", key, val);

  const featuresFields = data?.steps["FEATURES"]?.fields ?? {};
  const features = (featuresFields["features"] as FeatureLike[] | undefined) ?? [];
  const featureOptions = features.map((f) => ({ label: f.title, value: f.id }));

  return (
    <div className="space-y-5">
      <FormField label="MVP-Ziel" required>
        <Textarea value={get("goal")} onChange={(e) => set("goal", e.target.value)}
          placeholder="Was soll das MVP leisten?" rows={3}
          className="resize-y min-h-[80px]" />
      </FormField>
      {featureOptions.length > 0 && (
        <FormField label="MVP Features (aus Feature-Liste)">
          <ChipSelect
            options={featureOptions}
            value={(fields["mvpFeatures"] as string[]) ?? []}
            onChange={(v) => set("mvpFeatures", v)}
            multiSelect
          />
        </FormField>
      )}
      <FormField label="Erfolgskriterien">
        <Textarea value={get("successCriteria")} onChange={(e) => set("successCriteria", e.target.value)}
          placeholder="Woran erkennst du, dass das MVP erfolgreich ist?" rows={2}
          className="resize-y" />
      </FormField>
    </div>
  );
}
