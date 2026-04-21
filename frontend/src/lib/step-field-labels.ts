import type { FeatureScope } from "./api";

export const STEP_FIELD_LABELS: Record<string, Record<string, string>> = {
  IDEA: {
    productName: "Produktname",
    vision: "Produktidee / Vision",
    category: "Kategorie",
  },
  PROBLEM: {
    coreProblem: "Kernproblem",
    affected: "Wer ist betroffen?",
    workarounds: "Aktuelle Workarounds",
  },
  TARGET_AUDIENCE: {
    primaryAudience: "Primaere Zielgruppe",
    painPoints: "Pain Points",
    techLevel: "Technisches Level",
    secondaryAudience: "Sekundaere Zielgruppe",
  },
  SCOPE: {
    inScope: "In Scope",
    outOfScope: "Out of Scope",
  },
  MVP: {
    mvpGoal: "MVP-Ziel",
    mvpFeatures: "MVP Features",
    successCriteria: "Erfolgskriterien",
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
    IDEA: "Idee", PROBLEM: "Problem", TARGET_AUDIENCE: "Zielgruppe",
    SCOPE: "Scope", MVP: "MVP", FEATURES: "Features",
    ARCHITECTURE: "Architektur", BACKEND: "Backend", FRONTEND: "Frontend",
  };

  const lines: string[] = [`**${stepLabel[step] ?? step}**`, ""];

  for (const [key, value] of Object.entries(fields)) {
    if (value === null || value === undefined || value === "") continue;
    const label = labels[key] ?? key;
    const display = Array.isArray(value) ? value.join(", ") : String(value);
    lines.push(`${label}: ${display}`);
  }

  return lines.join("\n");
}
