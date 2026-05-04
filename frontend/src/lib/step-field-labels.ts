import type { FeatureScope, WizardFeature, WizardFeatureEdge } from "./api";

export const STEP_FIELD_LABELS: Record<string, Record<string, string>> = {
  IDEA: {
    productName: "Produktname",
    vision: "Produktidee / Vision",
    category: "Kategorie",
  },
  PROBLEM: {
    coreProblem: "Kernproblem",
    primaryAudience: "Primäre Zielgruppe",
    painPoints: "Pain Points",
  },
  MVP: {
    mvpGoal: "MVP-Ziel",
    mvpFeatures: "MVP Features",
    successCriteria: "Erfolgskriterien",
  },
  DESIGN: {
    bundleName: "Bundle",
    pageCount: "Pages",
  },
  FEATURES: {
    features: "Feature-Liste",
  },
  ARCHITECTURE: {
    architecture: "System-Architektur",
    database: "Datenbank",
    deployment: "Deployment",
    notes: "Architektur-Notizen",
  },
  BACKEND: {
    framework: "Sprache / Framework",
    apiStyle: "API-Stil",
    auth: "Auth-Methode",
  },
  FRONTEND: {
    framework: "Framework",
    uiLibrary: "UI Library",
    styling: "Styling",
    theme: "Theme",
  },
};

export const SCOPE_FIELD_LABELS: Record<string, string> = {
  uiComponents: "UI-Komponenten",
  screens: "Screens",
  userInteractions: "User-Interaktionen",
  apiEndpoints: "API-Endpunkte",
  dataModel: "Datenmodell",
  sideEffects: "Side-Effects",
  publicApi: "Public API",
  typesExposed: "Exponierte Types",
  examples: "Beispiele",
};

export const SCOPE_FIELDS_BY_SCOPE: Record<FeatureScope | "CORE", string[]> = {
  FRONTEND: ["uiComponents", "screens", "userInteractions"],
  BACKEND: ["apiEndpoints", "dataModel", "sideEffects"],
  CORE: ["publicApi", "typesExposed", "examples"],
};

export function formatStepFields(step: string, fields: Record<string, any>): string {
  const labels = STEP_FIELD_LABELS[step] ?? {};
  const stepLabel: Record<string, string> = {
    IDEA: "Idee", PROBLEM: "Problem & Zielgruppe",
    FEATURES: "Features", MVP: "MVP",
    DESIGN: "Design",
    ARCHITECTURE: "Architektur", BACKEND: "Backend", FRONTEND: "Frontend",
  };

  const lines: string[] = [`**${stepLabel[step] ?? step}**`, ""];

  for (const [key, value] of Object.entries(fields)) {
    if (value === null || value === undefined || value === "") continue;

    if (step === "FEATURES") {
      // edges are redundant in chat — dependsOn is rendered with each feature
      if (key === "edges") continue;
      if (key === "features" && Array.isArray(value)) {
        const block = formatFeaturesList(
          value as WizardFeature[],
          (fields.edges ?? []) as WizardFeatureEdge[],
        );
        if (block) {
          lines.push(`${labels[key] ?? key}:`);
          lines.push(block);
        }
        continue;
      }
    }

    const label = labels[key] ?? key;
    lines.push(`${label}: ${formatFieldValue(value)}`);
  }

  return lines.join("\n");
}

function formatFieldValue(value: unknown): string {
  if (Array.isArray(value)) {
    if (value.every((v) => v === null || typeof v !== "object")) {
      return value.join(", ");
    }
    return value.map((v) => JSON.stringify(v)).join(", ");
  }
  if (value !== null && typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function formatFeaturesList(
  features: WizardFeature[],
  edges: WizardFeatureEdge[],
): string {
  if (features.length === 0) return "";
  const titleById = new Map(features.map((f) => [f.id, f.title]));
  // edge `from → to` means "to depends on from"
  const depsByFeature = new Map<string, string[]>();
  for (const e of edges) {
    const fromTitle = titleById.get(e.from);
    if (!fromTitle || !titleById.has(e.to)) continue;
    const arr = depsByFeature.get(e.to) ?? [];
    arr.push(fromTitle);
    depsByFeature.set(e.to, arr);
  }
  return features
    .map((f) => {
      const deps = depsByFeature.get(f.id);
      const suffix = deps && deps.length > 0 ? ` — depends on: ${deps.join(", ")}` : "";
      return `- ${f.title}${suffix}`;
    })
    .join("\n");
}
