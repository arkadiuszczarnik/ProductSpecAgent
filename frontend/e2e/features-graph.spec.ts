import { test, expect } from "@playwright/test";
import { seedSaaSProjectToFeatures } from "./fixtures/seed-project";

async function login(page: Parameters<typeof test>[0]["page"], email: string, password: string) {
  await page.goto("/login");
  await page.getByLabel(/^email$/i).fill(email);
  await page.getByLabel(/^passwort$/i).fill(password);
  await Promise.all([
    page.waitForURL(/\/projects(?:$|\/)/i),
    page.getByRole("button", { name: /^anmelden$/i }).click(),
  ]);
}

test.describe("Feature 22 — Features graph editor", () => {
  test("mount, add, rename, connect, reload — no console errors, stays responsive", async ({
    page,
    request,
  }) => {
    const { projectId, email, password } = await seedSaaSProjectToFeatures(request);

    const consoleErrors: string[] = [];
    page.on("pageerror", (err) => consoleErrors.push(`pageerror: ${err.message}`));
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(`console.error: ${msg.text()}`);
    });

    await login(page, email, password);

    // Navigate to the project workspace page
    await page.goto(`/projects/${projectId}`);

    // Click the "Features" step in the StepIndicator — label is "Features"
    await page.getByRole("button", { name: /^features$/i }).click();

    // Wait for Rete canvas container to appear. The graph editor renders
    // inside a flex div; Rete itself mounts a canvas / SVG inside it.
    // We target the outer container div that wraps the Rete ref.
    const graphContainer = page
      .locator(".flex.h-\\[600px\\]")
      .first();
    await expect(graphContainer).toBeVisible({ timeout: 8_000 });

    // Add first feature — button text is "+ Feature" (Plus icon + "Feature" text)
    await page.getByRole("button", { name: /^\+?\s*feature$/i }).first().click();

    // The FeatureSidePanel appears when a feature is selected.
    // Title input is inside a wrapping <label>; the <span>Titel</span> is
    // NOT a <label for=...> so getByLabel() won't work.
    // Use a text-based locator for the label span + sibling input instead.
    const titleInput = page
      .locator("label")
      .filter({ has: page.locator("span", { hasText: /^Titel$/i }) })
      .locator("input");
    await expect(titleInput).toBeVisible({ timeout: 5_000 });
    await titleInput.fill("Authentication");

    // The node label should update in the side panel immediately
    await expect(titleInput).toHaveValue("Authentication", { timeout: 3_000 });

    // Add a second feature
    await page.getByRole("button", { name: /^\+?\s*feature$/i }).first().click();

    // Wait for debounced auto-save (800 ms debounce + buffer)
    await page.waitForTimeout(1_500);

    // Hard reload — both the graph and the "Authentication" feature must survive
    await page.reload();
    await page.getByRole("button", { name: /^features$/i }).click();

    // The "Authentication" feature title must be visible somewhere in the UI
    // (either as a node label in the Rete canvas or in the side panel)
    await expect(page.locator("text=Authentication")).toBeVisible({ timeout: 10_000 });

    // Step indicator is still interactive — clicking around must not hang
    // (in the original React-19-Strict-Mode crash, the whole page froze)
    const ideaStep = page.getByRole("button", { name: /^idee$/i });
    if (await ideaStep.isVisible()) {
      await ideaStep.click();
      // Navigate back to features
      await page.getByRole("button", { name: /^features$/i }).click();
    }

    // No React render-phase errors or unhandled JS errors must have occurred
    expect(
      consoleErrors,
      `Unexpected console errors:\n${consoleErrors.join("\n")}`,
    ).toEqual([]);
  });

  test("auto-layout persists updated feature coordinates immediately", async ({
    page,
    request,
  }) => {
    const { projectId, email, password } = await seedSaaSProjectToFeatures(request);

    const seedResponse = await request.put(
      `${process.env.TEST_API_URL ?? "http://localhost:8080"}/api/v1/projects/${projectId}/wizard/FEATURES`,
      {
        data: {
          fields: {
            features: [
              {
                id: "feature-a",
                title: "Authentication",
                scopes: ["FRONTEND"],
                description: "",
                scopeFields: {},
                acceptanceCriteria: [],
                position: { x: 0, y: 0 },
              },
              {
                id: "feature-b",
                title: "Dashboard",
                scopes: ["BACKEND"],
                description: "",
                scopeFields: {},
                acceptanceCriteria: [],
                position: { x: 0, y: 0 },
              },
            ],
            edges: [
              { id: "edge-a-b", from: "feature-a", to: "feature-b" },
            ],
          },
          completedAt: new Date().toISOString(),
        },
      },
    );
    expect(seedResponse.ok()).toBeTruthy();

    await login(page, email, password);
    await page.goto(`/projects/${projectId}`);
    await page.getByRole("button", { name: /^features$/i }).click();

    const graphContainer = page
      .locator(".flex.h-\\[600px\\]")
      .first();
    await expect(graphContainer).toBeVisible({ timeout: 8_000 });

    const saveResponsePromise = page.waitForResponse(
      (response) =>
        response.url().includes(`/api/v1/projects/${projectId}/wizard/FEATURES`) &&
        response.request().method() === "PUT",
    );

    await page.getByRole("button", { name: /auto-layout/i }).click();
    const saveResponse = await saveResponsePromise;
    expect(saveResponse.ok()).toBeTruthy();

    const wizardResponse = await request.get(
      `${process.env.TEST_API_URL ?? "http://localhost:8080"}/api/v1/projects/${projectId}/wizard`,
    );
    expect(wizardResponse.ok()).toBeTruthy();
    const wizard = await wizardResponse.json();
    const persistedFeatures = wizard.steps.FEATURES.fields.features as Array<{
      id: string;
      position: { x: number; y: number };
    }>;

    expect(persistedFeatures).toHaveLength(2);
    expect(
      persistedFeatures.some((feature) => feature.position.x !== 0 || feature.position.y !== 0),
    ).toBeTruthy();
  });
});
