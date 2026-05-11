// frontend/src/lib/category-step-config.ts

import type { FeatureScope, StepType, WizardOptionCatalog } from "./api";

export type Category = "SaaS" | "Mobile App" | "CLI Tool" | "Library" | "Desktop App" | "API";

export const ALL_STEP_KEYS = [
  "IDEA", "PROBLEM", "FEATURES", "MVP", "DESIGN",
  "ARCHITECTURE", "BACKEND", "FRONTEND", "REVIEW",
] as const;

export const BASE_STEPS = ["IDEA", "PROBLEM", "FEATURES", "MVP"] as const;

function withReviewStep(steps: readonly StepType[]): StepType[] {
  return [...steps.filter((step) => step !== "REVIEW"), "REVIEW"];
}

export type FieldOptions = Record<string, Record<string, string[]>>;

export interface CategoryConfig {
  visibleSteps: StepType[];
  fieldOptions: FieldOptions;
  allowedScopes: FeatureScope[];
}

export type CategoryStepConfig = CategoryConfig;

export const CATEGORY_STEP_CONFIG: Record<Category, CategoryConfig> = {
  "SaaS": {
    visibleSteps: withReviewStep([...BASE_STEPS, "DESIGN", "ARCHITECTURE", "BACKEND", "FRONTEND"]),
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "SQLite", "Redis"],
        deployment: ["Docker", "Vercel+Cloud", "Kubernetes"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "Rust", "DotNet"],
        apiStyle: ["REST", "GraphQL", "gRPC"],
        auth: ["JWT", "Session", "OAuth", "API Key"],
      },
      FRONTEND: {
        framework: ["Next.js+React", "Vue+Nuxt", "Svelte", "Angular", "Stitch"],
        uiLibrary: ["shadcn/ui", "Material UI", "Ant Design", "Custom"],
        styling: ["Tailwind CSS", "CSS Modules", "Styled Components"],
        theme: ["Dark only", "Light only", "Both"],
      },
    },
  },
  "Mobile App": {
    visibleSteps: withReviewStep([...BASE_STEPS, "DESIGN", "ARCHITECTURE", "BACKEND", "FRONTEND"]),
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "SQLite", "Firebase"],
        deployment: ["App Store", "Play Store", "TestFlight"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "DotNet"],
        apiStyle: ["REST", "GraphQL"],
        auth: ["JWT", "OAuth", "API Key"],
      },
      FRONTEND: {
        framework: ["React Native", "Flutter", "SwiftUI", "Kotlin Multiplatform"],
        uiLibrary: ["Native Components", "React Native Paper", "Custom"],
        styling: ["StyleSheet", "NativeWind", "Styled Components"],
        theme: ["System Default", "Dark only", "Light only", "Both"],
      },
    },
  },
  "CLI Tool": {
    visibleSteps: withReviewStep([...BASE_STEPS, "ARCHITECTURE"]),
    allowedScopes: ["BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Single Binary", "Multi-Command"],
        database: ["Filesystem", "SQLite"],
        deployment: ["npm/pip/brew", "Binary Release"],
      },
    },
  },
  "Library": {
    visibleSteps: withReviewStep([...BASE_STEPS]),
    allowedScopes: [],
    fieldOptions: {},
  },
  "Desktop App": {
    visibleSteps: withReviewStep([...BASE_STEPS, "DESIGN", "ARCHITECTURE", "BACKEND", "FRONTEND"]),
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Plugin-basiert"],
        database: ["SQLite", "PostgreSQL", "Filesystem"],
        deployment: ["Installer", "App Store", "Portable"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "DotNet"],
        apiStyle: ["REST", "IPC"],
        auth: ["OAuth", "Local Auth"],
      },
      FRONTEND: {
        framework: ["Electron", "Tauri", "SwiftUI", "WPF"],
        uiLibrary: ["Native Components", "shadcn/ui", "Custom"],
        styling: ["Tailwind CSS", "Native Styling", "CSS Modules"],
        theme: ["System Default", "Dark only", "Light only", "Both"],
      },
    },
  },
  "API": {
    visibleSteps: withReviewStep([...BASE_STEPS, "ARCHITECTURE", "BACKEND"]),
    allowedScopes: ["BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "Redis"],
        deployment: ["Docker", "Vercel+Cloud", "Kubernetes"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "Rust", "DotNet"],
        apiStyle: ["REST", "GraphQL", "gRPC"],
        auth: ["JWT", "OAuth", "API Key"],
      },
    },
  },
};

export const DEFAULT_CATEGORY_STEP_CONFIG = CATEGORY_STEP_CONFIG;

/** Default: all steps visible (no category selected) */
export const DEFAULT_VISIBLE_STEPS: StepType[] = withReviewStep(ALL_STEP_KEYS);

export function getVisibleSteps(category: string | undefined): StepType[] {
  if (!category) return [...DEFAULT_VISIBLE_STEPS];
  const config = CATEGORY_STEP_CONFIG[category as Category];
  return config ? withReviewStep(config.visibleSteps) : [...DEFAULT_VISIBLE_STEPS];
}

export function getFieldOptions(category: string | undefined, step: string): Record<string, string[]> | undefined {
  if (!category) return undefined;
  const config = CATEGORY_STEP_CONFIG[category as Category];
  return config?.fieldOptions[step];
}

export function getAllowedScopes(category: string | undefined): FeatureScope[] {
  if (!category) return ["FRONTEND", "BACKEND"];
  return CATEGORY_STEP_CONFIG[category as Category]?.allowedScopes ?? ["FRONTEND", "BACKEND"];
}

function isCategory(value: string): value is Category {
  return value in CATEGORY_STEP_CONFIG;
}

function enabledOptionLabels(options: { label: string; enabled?: boolean }[]): string[] {
  return options.filter((option) => option.enabled !== false).map((option) => option.label);
}

export function catalogToCategoryStepConfig(catalog: WizardOptionCatalog): Record<Category, CategoryStepConfig> {
  const next: Record<Category, CategoryStepConfig> = Object.fromEntries(
    (Object.entries(CATEGORY_STEP_CONFIG) as [Category, CategoryStepConfig][]).map(([category, config]) => [
      category,
      {
        visibleSteps: [...config.visibleSteps],
        allowedScopes: [...config.allowedScopes],
        fieldOptions: Object.fromEntries(
          Object.entries(config.fieldOptions).map(([step, fields]) => [
            step,
            Object.fromEntries(Object.entries(fields).map(([key, values]) => [key, [...values]])),
          ]),
        ),
      },
    ]),
  ) as Record<Category, CategoryStepConfig>;

  for (const category of catalog.categories) {
    if (!isCategory(category.id)) continue;
    const fallback = CATEGORY_STEP_CONFIG[category.id];
    const fieldOptions: FieldOptions = {};

    for (const step of category.visibleSteps) {
      fieldOptions[step] = { ...(fallback.fieldOptions[step] ?? {}) };
    }

    for (const field of category.fields) {
      const labels = enabledOptionLabels(field.options);
      fieldOptions[field.step] = {
        ...(fieldOptions[field.step] ?? fallback.fieldOptions[field.step] ?? {}),
        [field.key]: labels,
      };
    }

    next[category.id] = {
      visibleSteps: withReviewStep(category.visibleSteps.length > 0 ? category.visibleSteps : fallback.visibleSteps),
      allowedScopes: [...category.allowedScopes],
      fieldOptions,
    };
  }

  return next;
}

export function getVisibleStepsFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category | string | undefined,
): StepType[] {
  if (!catalog || !category || !isCategory(category)) return getVisibleSteps(category);
  return withReviewStep(catalog.categories.find((entry) => entry.id === category)?.visibleSteps ?? getVisibleSteps(category));
}

export function getFieldOptionsFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category | string | undefined,
  step: StepType | string,
): Record<string, string[]> | undefined {
  if (!catalog || !category || !isCategory(category)) return getFieldOptions(category, step);
  const catalogCategory = catalog.categories.find((entry) => entry.id === category);
  if (!catalogCategory) return getFieldOptions(category, step);

  const fields = catalogCategory.fields.filter((field) => field.step === step);
  if (fields.length === 0) return getFieldOptions(category, step);

  const fallback = getFieldOptions(category, step) ?? {};
  return fields.reduce<Record<string, string[]>>((acc, field) => {
    acc[field.key] = enabledOptionLabels(field.options);
    return acc;
  }, { ...fallback });
}

export function getAllowedScopesFromCatalog(
  catalog: WizardOptionCatalog | null,
  category: Category | string | undefined,
): FeatureScope[] {
  if (!catalog || !category || !isCategory(category)) return getAllowedScopes(category);
  const catalogCategory = catalog.categories.find((entry) => entry.id === category);
  if (!catalogCategory) return getAllowedScopes(category);
  return [...catalogCategory.allowedScopes];
}
