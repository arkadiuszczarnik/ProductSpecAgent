// frontend/src/lib/asset-bundles/possible-triples.ts
import type { StepType, AssetBundleManifest } from "@/lib/api";
import { CATEGORY_STEP_CONFIG } from "@/lib/category-step-config";

export type RelevantStep = "ARCHITECTURE" | "BACKEND" | "FRONTEND";

export type BundleTriple = {
  step: RelevantStep;
  field: string;
  value: string;
};

export type GroupedTriples = Record<RelevantStep, Record<string, BundleTriple[]>>;

export const RELEVANT_STEPS: ReadonlyArray<RelevantStep> = ["ARCHITECTURE", "BACKEND", "FRONTEND"];

/** Slugify identisch zur Backend-Regel (`assetBundleSlug` in domain/AssetBundle.kt). */
export function slugifyBundleValue(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

/** Deterministische Bundle-ID — match zum Backend-`assetBundleId`. */
export function bundleId(step: RelevantStep, field: string, value: string): string {
  return `${step.toLowerCase()}.${field}.${slugifyBundleValue(value)}`;
}

/** Aggregiert alle (step, field, value)-Triples aus allen Kategorien, dedupliziert über bundleId. */
export function getAllPossibleTriples(): BundleTriple[] {
  const seen = new Map<string, BundleTriple>();
  for (const config of Object.values(CATEGORY_STEP_CONFIG)) {
    for (const step of RELEVANT_STEPS) {
      const fields = config.fieldOptions[step];
      if (!fields) continue;
      for (const [field, values] of Object.entries(fields)) {
        for (const value of values) {
          const id = bundleId(step, field, value);
          if (!seen.has(id)) {
            seen.set(id, { step, field, value });
          }
        }
      }
    }
  }
  return Array.from(seen.values());
}

/** Filtert die Triples raus, deren bundleId bereits unter den hochgeladenen Bundles ist. */
export function diffMissingTriples(
  possible: BundleTriple[],
  uploaded: ReadonlyArray<{ id: string }>,
): BundleTriple[] {
  const uploadedIds = new Set(uploaded.map((b) => b.id));
  return possible.filter((t) => !uploadedIds.has(bundleId(t.step, t.field, t.value)));
}

/** Gruppiert Triples nach step → field. Values innerhalb eines Felds alphabetisch sortiert. */
export function groupByStepAndField(triples: BundleTriple[]): GroupedTriples {
  const out: GroupedTriples = { ARCHITECTURE: {}, BACKEND: {}, FRONTEND: {} };
  for (const t of triples) {
    const byField = out[t.step];
    (byField[t.field] ??= []).push(t);
  }
  for (const step of RELEVANT_STEPS) {
    for (const field of Object.keys(out[step])) {
      out[step][field].sort((a, b) => a.value.localeCompare(b.value));
    }
  }
  return out;
}

/** Erzeugt einen vollständigen, schemakonformen manifest.json-Stub mit Defaults. */
export function buildManifestStub(triple: BundleTriple, now: Date): AssetBundleManifest {
  const iso = now.toISOString();
  return {
    id: bundleId(triple.step, triple.field, triple.value),
    step: triple.step as StepType,
    field: triple.field,
    value: triple.value,
    version: "1.0.0",
    title: `${triple.value} Bundle`,
    description: `Skills, Commands und Agents für ${triple.value}`,
    createdAt: iso,
    updatedAt: iso,
  };
}
