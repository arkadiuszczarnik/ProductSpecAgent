"use client";
import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { FormField } from "../FormField";
import { ChipSelect } from "../ChipSelect";
import { useWizardStore } from "@/lib/stores/wizard-store";

interface Feature { title: string; description: string; estimate: string; }

export function FeaturesForm({ projectId }: { projectId: string }) {
  const { data, updateField } = useWizardStore();
  const fields = data?.steps["FEATURES"]?.fields ?? {};
  const features: Feature[] = (fields["features"] as Feature[]) ?? [];
  const set = (val: Feature[]) => updateField("FEATURES", "features", val);

  const [newTitle, setNewTitle] = useState("");

  function addFeature() {
    if (!newTitle.trim()) return;
    set([...features, { title: newTitle.trim(), description: "", estimate: "M" }]);
    setNewTitle("");
  }

  function removeFeature(idx: number) { set(features.filter((_, i) => i !== idx)); }
  function updateFeature(idx: number, key: keyof Feature, val: string) {
    set(features.map((f, i) => i === idx ? { ...f, [key]: val } : f));
  }

  return (
    <div className="space-y-5">
      <div className="flex gap-2">
        <input value={newTitle} onChange={(e) => setNewTitle(e.target.value)} onKeyDown={(e) => e.key === "Enter" && addFeature()}
          placeholder="Feature-Name..." className="flex-1 rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
        <Button size="sm" onClick={addFeature} disabled={!newTitle.trim()}><Plus size={14} /> Hinzufuegen</Button>
      </div>
      {features.length === 0 && <p className="text-sm text-muted-foreground py-4 text-center">Noch keine Features. Fuege dein erstes Feature hinzu.</p>}
      <div className="space-y-3">
        {features.map((f, i) => (
          <div key={i} className="rounded-lg border bg-card p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">{i + 1}. {f.title}</span>
              <div className="flex items-center gap-2">
                <ChipSelect options={["XS", "S", "M", "L", "XL"]} value={f.estimate} onChange={(v) => updateFeature(i, "estimate", v as string)} />
                <button onClick={() => removeFeature(i)} className="text-muted-foreground hover:text-destructive"><Trash2 size={13} /></button>
              </div>
            </div>
            <textarea value={f.description} onChange={(e) => updateFeature(i, "description", e.target.value)}
              placeholder="Beschreibung..." rows={2}
              className="w-full resize-none rounded-md border bg-input px-3 py-1.5 text-xs placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring" />
          </div>
        ))}
      </div>
    </div>
  );
}
