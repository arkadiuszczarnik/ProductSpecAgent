import type { APIRequestContext } from "@playwright/test";

const API = process.env.TEST_API_URL ?? "http://localhost:8080";

export interface SeededProject {
  projectId: string;
}

/**
 * Creates a minimal SaaS project seeded through IDEA→MVP so the FEATURES
 * step is reachable without triggering any AI/LLM calls.
 *
 * DTO shapes verified against:
 *   - backend/.../domain/ApiModels.kt     → CreateProjectRequest { name, idea }
 *   - backend/.../domain/WizardModels.kt  → WizardStepData { fields, completedAt? }
 *   - backend/.../api/ProjectController.kt  → POST /api/v1/projects → ProjectResponse { project, flowState }
 *   - backend/.../api/WizardController.kt   → PUT /api/v1/projects/{id}/wizard/{step}
 */
export async function seedSaaSProjectToFeatures(
  request: APIRequestContext,
): Promise<SeededProject> {
  // Create project — requires both name AND idea (CreateProjectRequest)
  const createRes = await request.post(`${API}/api/v1/projects`, {
    data: {
      name: `e2e-features-${Date.now()}`,
      idea: "E2E smoke-test SaaS project",
    },
  });
  if (!createRes.ok()) {
    throw new Error(`create project failed: HTTP ${createRes.status()} — ${await createRes.text()}`);
  }
  // ProjectResponse wraps: { project: { id, name, ... }, flowState: { ... } }
  const projectResponse = await createRes.json();
  const projectId = projectResponse.project.id as string;

  // Seed IDEA..MVP via PUT /wizard/{step} — save only, no LLM trigger.
  // WizardStepData shape: { fields: Map<String, JsonElement>, completedAt?: String }
  const steps: Array<[string, Record<string, unknown>]> = [
    ["IDEA",    { idea: "E2E smoke-test project", category: "SaaS" }],
    ["PROBLEM", { coreProblem: "Validate graph editor end-to-end", primaryAudience: "Developers" }],
    ["SCOPE",   { scope: "Smoke only" }],
    ["MVP",     { mvp: "Add/rename/connect nodes + persist" }],
  ];

  for (const [step, fields] of steps) {
    const res = await request.put(`${API}/api/v1/projects/${projectId}/wizard/${step}`, {
      data: { fields, completedAt: new Date().toISOString() },
    });
    if (!res.ok()) {
      throw new Error(`seed step ${step} failed: HTTP ${res.status()} — ${await res.text()}`);
    }
  }

  return { projectId };
}
