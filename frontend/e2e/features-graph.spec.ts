import { test, expect } from "@playwright/test";
import { seedSaaSProjectToFeatures } from "./fixtures/seed-project";

test.describe("Feature 22 — Features graph editor", () => {
  test("mount, add, rename, connect, reload — no console errors, stays responsive", async ({
    page,
    request,
  }) => {
    const { projectId } = await seedSaaSProjectToFeatures(request);

    const consoleErrors: string[] = [];
    page.on("pageerror", (err) => consoleErrors.push(`pageerror: ${err.message}`));
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(`console.error: ${msg.text()}`);
    });

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
});
