// frontend/src/lib/category-step-config.ts

import type { FeatureScope } from "./api";

export type Category = "SaaS" | "Mobile App" | "CLI Tool" | "Library" | "Desktop App" | "API";

export const ALL_STEP_KEYS = [
  "IDEA", "PROBLEM", "SCOPE", "MVP",
  "FEATURES", "ARCHITECTURE", "BACKEND", "FRONTEND",
] as const;

const BASE_STEPS = ["IDEA", "PROBLEM", "SCOPE", "MVP", "FEATURES"] as const;

export type FieldOptions = Record<string, Record<string, string[]>>;

export interface CategoryConfig {
  visibleSteps: string[];
  fieldOptions: FieldOptions;
  allowedScopes: FeatureScope[];
}

export const CATEGORY_STEP_CONFIG: Record<Category, CategoryConfig> = {
  "SaaS": {
    visibleSteps: [...BASE_STEPS, "ARCHITECTURE", "BACKEND", "FRONTEND"],
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "SQLite", "Redis"],
        deployment: ["Docker", "Vercel+Cloud", "Kubernetes"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "Rust"],
        apiStyle: ["REST", "GraphQL", "gRPC"],
        auth: ["JWT", "Session", "OAuth", "API Key"],
      },
      FRONTEND: {
        framework: ["Next.js+React", "Vue+Nuxt", "Svelte", "Angular"],
        uiLibrary: ["shadcn/ui", "Material UI", "Ant Design", "Custom"],
        styling: ["Tailwind CSS", "CSS Modules", "Styled Components"],
        theme: ["Dark only", "Light only", "Both"],
      },
    },
  },
  "Mobile App": {
    visibleSteps: [...BASE_STEPS, "ARCHITECTURE", "BACKEND", "FRONTEND"],
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "SQLite", "Firebase"],
        deployment: ["App Store", "Play Store", "TestFlight"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go"],
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
    visibleSteps: [...BASE_STEPS, "ARCHITECTURE"],
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
    visibleSteps: [...BASE_STEPS],
    allowedScopes: [],
    fieldOptions: {},
  },
  "Desktop App": {
    visibleSteps: [...BASE_STEPS, "ARCHITECTURE", "BACKEND", "FRONTEND"],
    allowedScopes: ["FRONTEND", "BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Plugin-basiert"],
        database: ["SQLite", "PostgreSQL", "Filesystem"],
        deployment: ["Installer", "App Store", "Portable"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI"],
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
    visibleSteps: [...BASE_STEPS, "ARCHITECTURE", "BACKEND"],
    allowedScopes: ["BACKEND"],
    fieldOptions: {
      ARCHITECTURE: {
        architecture: ["Monolith", "Microservices", "Serverless"],
        database: ["PostgreSQL", "MongoDB", "Redis"],
        deployment: ["Docker", "Vercel+Cloud", "Kubernetes"],
      },
      BACKEND: {
        framework: ["Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "Rust"],
        apiStyle: ["REST", "GraphQL", "gRPC"],
        auth: ["JWT", "OAuth", "API Key"],
      },
    },
  },
};

/** Default: all steps visible (no category selected) */
export const DEFAULT_VISIBLE_STEPS: string[] = [...ALL_STEP_KEYS];

export function getVisibleSteps(category: string | undefined): string[] {
  if (!category) return [...DEFAULT_VISIBLE_STEPS];
  const config = CATEGORY_STEP_CONFIG[category as Category];
  return config ? config.visibleSteps : [...DEFAULT_VISIBLE_STEPS];
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
