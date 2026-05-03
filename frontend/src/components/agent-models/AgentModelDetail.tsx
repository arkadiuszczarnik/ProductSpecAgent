"use client";
import { useEffect, useState } from "react";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import {
  type AgentModelInfo,
  type AgentModelTier,
  listAgentModels,
  resetAgentModel,
  updateAgentModel,
} from "@/lib/api";

interface AgentModelDetailProps {
  agentId: string;
  onChange: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const TIERS: AgentModelTier[] = ["SMALL", "MEDIUM", "LARGE"];

export function AgentModelDetail({ agentId, onChange, onDirtyChange }: AgentModelDetailProps) {
  const [item, setItem] = useState<AgentModelInfo | null>(null);
  const [tier, setTier] = useState<AgentModelTier | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listAgentModels()
      .then((all) => {
        const found = all.find((it) => it.agentId === agentId) ?? null;
        setItem(found);
        setTier(found?.currentTier ?? null);
      })
      .catch((e) => setError(String(e)));
  }, [agentId]);

  useEffect(() => {
    if (!item || !tier) onDirtyChange(false);
    else onDirtyChange(tier !== item.currentTier);
  }, [item, tier, onDirtyChange]);

  if (!item || !tier) return <div className="p-6 text-sm text-muted-foreground">Lädt …</div>;

  const dirty = tier !== item.currentTier;

  const handleSave = async () => {
    if (!dirty) return;
    setSaving(true);
    setError(null);
    try {
      await updateAgentModel(agentId, tier);
      onChange();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm("Auf Default zurücksetzen?")) return;
    setSaving(true);
    setError(null);
    try {
      await resetAgentModel(agentId);
      onChange();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-6 space-y-6 max-w-2xl">
      <div>
        <h2 className="text-lg font-semibold">{item.displayName}</h2>
        <p className="text-sm text-muted-foreground mt-1">
          Wähle das Modell für diesen Agent. Default: <strong>{item.defaultTier}</strong>.
        </p>
      </div>

      {error && (
        <div className="text-sm text-destructive border border-destructive/50 rounded p-3">
          {error}
        </div>
      )}

      <RadioGroup value={tier} onValueChange={(v) => setTier(v as AgentModelTier)}>
        {TIERS.map((t) => (
          <div key={t} className="flex items-center gap-3 py-2">
            <RadioGroupItem id={`tier-${t}`} value={t} />
            <Label htmlFor={`tier-${t}`} className="cursor-pointer">
              <span className="font-medium">{t}</span>
              <span className="ml-2 text-muted-foreground">— {item.tierMapping[t]}</span>
            </Label>
          </div>
        ))}
      </RadioGroup>

      <div className="flex gap-2">
        <Button onClick={handleSave} disabled={!dirty || saving}>
          Speichern
        </Button>
        <Button variant="outline" onClick={handleReset} disabled={!item.isOverridden || saving}>
          Auf Default zurücksetzen
        </Button>
      </div>
    </div>
  );
}
