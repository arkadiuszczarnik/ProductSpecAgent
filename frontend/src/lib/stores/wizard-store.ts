import { create } from "zustand";
import type {
  WizardData,
  WizardFeature,
  WizardFeatureEdge,
  WizardFeatureGraph,
  WizardProgressionView,
  StepType,
} from "@/lib/api";
import { getWizardData, getWizardProgression, saveWizardStep, completeWizardStep } from "@/lib/api";
import { wouldCreateCycle } from "@/lib/graph/cycleCheck";
import { formatStepFields } from "@/lib/step-field-labels";
import { useProjectStore } from "@/lib/stores/project-store";
import { getVisibleStepsFromCatalog } from "@/lib/category-step-config";
import { useWizardOptionsStore } from "@/lib/stores/wizard-options-store";

export const WIZARD_STEPS = [
  { key: "IDEA", label: "Idee" },
  { key: "PROBLEM", label: "Problem & Zielgruppe" },
  { key: "FEATURES", label: "Features" },
  { key: "MVP", label: "MVP" },
  { key: "DESIGN", label: "Design" },
  { key: "ARCHITECTURE", label: "Architektur" },
  { key: "BACKEND", label: "Backend" },
  { key: "FRONTEND", label: "Frontend" },
] as const;

interface WizardState {
  data: WizardData | null;
  progression: WizardProgressionView | null;
  activeStep: string;
  loading: boolean;
  saving: boolean;
  chatPending: boolean;

  loadWizard: (projectId: string) => Promise<void>;
  setActiveStep: (step: string) => void;
  updateField: (step: string, field: string, value: unknown) => void;
  saveStep: (projectId: string, step: string) => Promise<void>;
  completeStep: (projectId: string, step: string) => Promise<{ exportTriggered: boolean } | null>;
  goNext: () => void;
  goPrev: () => void;
  reset: () => void;
  getCategory: () => string | undefined;
  visibleSteps: () => typeof WIZARD_STEPS[number][];
  addFeature: (f: Omit<WizardFeature, "id">) => string;
  updateFeature: (id: string, patch: Partial<WizardFeature>) => void;
  removeFeature: (id: string) => void;
  addEdge: (from: string, to: string) => boolean;
  removeEdge: (id: string) => void;
  moveFeature: (id: string, pos: { x: number; y: number }) => void;
  applyProposal: (graph: WizardFeatureGraph) => void;
}

// ---------------------------------------------------------------------------
// Atomic selectors — use with useWizardStore(useShallow(selectFeatures))
// ---------------------------------------------------------------------------

export const selectFeatures = (s: WizardState): WizardFeature[] =>
  (s.data?.steps.FEATURES?.fields.features as WizardFeature[] | undefined) ?? [];

export const selectEdges = (s: WizardState): WizardFeatureEdge[] =>
  (s.data?.steps.FEATURES?.fields.edges as WizardFeatureEdge[] | undefined) ?? [];

export const selectFeatureById = (id: string) => (s: WizardState): WizardFeature | undefined =>
  selectFeatures(s).find((f) => f.id === id);

// ---------------------------------------------------------------------------

let saveTimeout: ReturnType<typeof setTimeout> | null = null;

export const useWizardStore = create<WizardState>((set, get) => ({
  data: null,
  progression: null,
  activeStep: "IDEA",
  loading: false,
  saving: false,
  chatPending: false,

  loadWizard: async (projectId) => {
    set({ loading: true });
    try {
      const data = await getWizardData(projectId);
      const progression = await getWizardProgression(projectId).catch(() => null);
      const optionsStore = useWizardOptionsStore.getState();
      if (!optionsStore.catalog && !optionsStore.loading) {
        await optionsStore.loadCatalog();
      }
      const catalog = useWizardOptionsStore.getState().catalog;
      const fallbackSteps = getVisibleStepsFromCatalog(catalog, data.steps["IDEA"]?.fields?.category as string | undefined);
      const activeStep = progression?.currentStep
        ?? progression?.steps.find((step) => step.visible)?.step
        ?? fallbackSteps[0]
        ?? "IDEA";
      set({ data, progression, activeStep, loading: false });
    } catch {
      set({ data: { projectId, steps: {} }, progression: null, activeStep: "IDEA", loading: false });
    }
  },

  setActiveStep: (step) => set({ activeStep: step }),

  getCategory: () => {
    const { data } = get();
    return data?.steps["IDEA"]?.fields?.category as string | undefined;
  },

  visibleSteps: () => {
    const progression = get().progression;
    if (progression?.steps.length) {
      return progression.steps
        .filter((step) => step.visible)
        .flatMap((step) => WIZARD_STEPS.find((wizardStep) => wizardStep.key === step.step) ?? []);
    }

    const category = get().getCategory();
    const catalog = useWizardOptionsStore.getState().catalog;
    const visible = getVisibleStepsFromCatalog(catalog, category);
    return WIZARD_STEPS.filter((s) => visible.includes(s.key));
  },

  updateField: (step, field, value) => {
    const { data } = get();
    if (!data) return;

    const stepData = data.steps[step] ?? { fields: {}, completedAt: null };
    const updated: WizardData = {
      ...data,
      steps: {
        ...data.steps,
        [step]: { ...stepData, fields: { ...stepData.fields, [field]: value } },
      },
    };
    set({ data: updated });

    // If category changed, ensure activeStep is still visible
    if (step === "IDEA" && field === "category") {
      set({ progression: null });
      const { activeStep } = get();
      const catalog = useWizardOptionsStore.getState().catalog;
      const visible = getVisibleStepsFromCatalog(catalog, value as string);
      if (!visible.includes(activeStep as StepType)) {
        const wizardVisible = WIZARD_STEPS.filter((s) => visible.includes(s.key));
        set({ activeStep: wizardVisible[wizardVisible.length - 1].key });
      }
    }

    // Auto-save with debounce
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(() => {
      const state = get();
      if (state.data) {
        const sd = state.data.steps[step];
        if (sd) {
          set({ saving: true });
          saveWizardStep(data.projectId, step, sd)
            .then((result) => set({ data: result, saving: false }))
            .catch(() => set({ saving: false }));
        }
      }
    }, 500);
  },

  saveStep: async (projectId, step) => {
    const { data } = get();
    if (!data) return;
    const stepData = data.steps[step];
    if (!stepData) return;
    set({ saving: true });
    try {
      const result = await saveWizardStep(projectId, step, stepData);
      set({ data: result, saving: false });
    } catch {
      set({ saving: false });
    }
  },

  completeStep: async (projectId, step) => {
    const { data, visibleSteps } = get();
    if (!data) return null;

    // DESIGN step: skip generic wizard save, use dedicated endpoint
    if (step === "DESIGN") {
      const { useDesignBundleStore } = await import("@/lib/stores/design-bundle-store");
      const bundle = useDesignBundleStore.getState().bundle;
      const { completeDesignStep } = await import("@/lib/api");

      const chatMessage = bundle
        ? `**Design**\n\nBundle: ${bundle.originalFilename} (${bundle.pages.length} Pages)`
        : `**Design** — übersprungen, kein Bundle hochgeladen`;

      const userMsg = {
        id: `wizard-${Date.now()}`,
        role: "user" as const,
        content: chatMessage,
        timestamp: Date.now(),
      };
      useProjectStore.setState((s) => ({
        messages: [...s.messages, userMsg],
        chatSending: true,
      }));

      set({ chatPending: true });
      let exportTriggered = false;
      try {
        const locale = typeof navigator !== "undefined" ? navigator.language : "de";
        const response = await completeDesignStep(projectId, locale);
        exportTriggered = response.action?.type === "OPEN_EXPORT";
        const agentMsg = {
          id: `wizard-agent-${Date.now()}`,
          role: "agent" as const,
          content: response.message,
          timestamp: Date.now(),
        };
        useProjectStore.setState((s) => ({
          messages: [...s.messages, agentMsg],
          chatSending: false,
        }));

        // Mark DESIGN as completed locally; the backend persists the DESIGN step while
        // storing the generated design summary.
        const completedAt = new Date().toISOString();
        const designStepData = data.steps.DESIGN ?? { fields: {}, completedAt: null };
        const updatedDesign = { ...designStepData, completedAt };
        set({
          data: { ...data, steps: { ...data.steps, DESIGN: updatedDesign } },
        });

        if (response.progression) {
          set({ progression: response.progression });
        }

        if (response.action?.type === "SHOW_STEP" && response.action.step) {
          set({ activeStep: response.action.step });
        } else if (response.nextStep) {
          const visible = get().visibleSteps();
          const nextVisible = visible.find((v) => v.key === response.nextStep);
          if (nextVisible) set({ activeStep: response.nextStep });
        }
      } catch (err) {
        const errMsg = {
          id: `wizard-err-${Date.now()}`,
          role: "system" as const,
          content: `Fehler: ${err instanceof Error ? err.message : "Agent konnte nicht antworten"}`,
          timestamp: Date.now(),
        };
        useProjectStore.setState((s) => ({
          messages: [...s.messages, errMsg],
          chatSending: false,
        }));
      } finally {
        set({ chatPending: false });
      }
      return { exportTriggered };
    }

    const stepData = data.steps[step] ?? { fields: {}, completedAt: null };
    const completed = { ...stepData, completedAt: new Date().toISOString() };

    // Save the step completion locally
    set({ saving: true });
    try {
      const result = await saveWizardStep(projectId, step, completed);
      set({ data: result, saving: false });
    } catch {
      set({ saving: false });
      return null;
    }

    // Format fields as readable chat message
    const fields = stepData.fields ?? {};
    const plainFields: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(fields)) {
      plainFields[k] = typeof v === "object" && v !== null && "value" in v
        ? (v as { value: unknown }).value
        : v;
    }
    const chatMessage = formatStepFields(step, plainFields);

    // Add user message to chat
    const userMsg = {
      id: `wizard-${Date.now()}`,
      role: "user" as const,
      content: chatMessage,
      timestamp: Date.now(),
    };
    useProjectStore.setState((s) => ({
      messages: [...s.messages, userMsg],
      chatSending: true,
    }));

    // Send to backend agent endpoint
    set({ chatPending: true });
    try {
      const locale = typeof navigator !== "undefined" ? navigator.language : "de";
      const response = await completeWizardStep(projectId, { step, fields: plainFields, locale });

      // Add agent response to chat
      const agentMsg = {
        id: `wizard-agent-${Date.now()}`,
        role: "agent" as const,
        content: response.message,
        timestamp: Date.now(),
      };
      useProjectStore.setState((s) => ({
        messages: [...s.messages, agentMsg],
        chatSending: false,
      }));

      if (response.progression) {
        set({ progression: response.progression });
      }

      const action = response.action;
      if (action?.type === "SHOW_STEP" && action.step) {
        const steps = get().visibleSteps();
        const nextVisible = steps.find((s) => s.key === action.step);
        set({
          activeStep: nextVisible ? action.step : get().activeStep,
          chatPending: false,
        });
      } else {
        set({ chatPending: false });
      }

      // Navigate to next step
      if (!action && response.nextStep) {
        const steps = visibleSteps();
        const nextVisible = steps.find((s) => s.key === response.nextStep);
        if (nextVisible) {
          set({ activeStep: response.nextStep, chatPending: false });
        } else {
          set({ chatPending: false });
        }
      } else {
        set({ chatPending: false });
      }

      // If a decision was triggered, fetch it
      const decisionIds = Array.from(new Set([
        ...(response.artifacts?.decisionIds ?? []),
        ...(response.decisionId ? [response.decisionId] : []),
      ]));
      for (const decisionId of decisionIds) {
        const { getDecision } = await import("@/lib/api");
        const decision = await getDecision(projectId, decisionId);
        const { useDecisionStore } = await import("@/lib/stores/decision-store");
        useDecisionStore.getState().addDecision(decision);
      }

      // If a clarification was triggered, fetch it
      const clarificationIds = Array.from(new Set([
        ...(response.artifacts?.clarificationIds ?? []),
        ...(response.clarificationId ? [response.clarificationId] : []),
      ]));
      for (const clarificationId of clarificationIds) {
        const { getClarification } = await import("@/lib/api");
        const clarification = await getClarification(projectId, clarificationId);
        const { useClarificationStore } = await import("@/lib/stores/clarification-store");
        useClarificationStore.getState().addClarification(clarification);
      }

      // Handle export trigger on last step
      const exportTriggered = action?.type === "OPEN_EXPORT" || response.exportTriggered;
      if (exportTriggered) {
        const systemMsg = {
          id: `wizard-export-${Date.now()}`,
          role: "system" as const,
          content: "Spezifikation abgeschlossen. Du kannst das Projekt jetzt exportieren.",
          timestamp: Date.now(),
        };
        useProjectStore.setState((s) => ({
          messages: [...s.messages, systemMsg],
        }));
      }

      return { exportTriggered };
    } catch (err) {
      const errMsg = {
        id: `wizard-err-${Date.now()}`,
        role: "system" as const,
        content: `Fehler: ${err instanceof Error ? err.message : "Agent konnte nicht antworten"}`,
        timestamp: Date.now(),
      };
      useProjectStore.setState((s) => ({
        messages: [...s.messages, errMsg],
        chatSending: false,
      }));
      set({ chatPending: false });
      return null;
    }
  },

  goNext: () => {
    const { activeStep, visibleSteps } = get();
    const steps = visibleSteps();
    const idx = steps.findIndex((s) => s.key === activeStep);
    if (idx < steps.length - 1) set({ activeStep: steps[idx + 1].key });
  },

  goPrev: () => {
    const { activeStep, visibleSteps } = get();
    const steps = visibleSteps();
    const idx = steps.findIndex((s) => s.key === activeStep);
    if (idx > 0) set({ activeStep: steps[idx - 1].key });
  },

  reset: () => set({
    data: null,
    progression: null,
    activeStep: "IDEA",
    loading: false,
    saving: false,
    chatPending: false,
  }),

  addFeature: (f) => {
    const id = crypto.randomUUID();
    const current = selectFeatures(get());
    const feature: WizardFeature = { id, ...f };
    get().updateField("FEATURES", "features", [...current, feature]);
    return id;
  },

  updateFeature: (id, patch) => {
    const next = selectFeatures(get()).map((f) => (f.id === id ? { ...f, ...patch } : f));
    get().updateField("FEATURES", "features", next);
  },

  removeFeature: (id) => {
    const nextFeatures = selectFeatures(get()).filter((f) => f.id !== id);
    const nextEdges = selectEdges(get()).filter((e) => e.from !== id && e.to !== id);
    get().updateField("FEATURES", "features", nextFeatures);
    get().updateField("FEATURES", "edges", nextEdges);
  },

  addEdge: (from, to) => {
    const edges = selectEdges(get());
    if (wouldCreateCycle(edges, from, to)) return false;
    const edge: WizardFeatureEdge = { id: crypto.randomUUID(), from, to };
    get().updateField("FEATURES", "edges", [...edges, edge]);
    return true;
  },

  removeEdge: (id) => {
    get().updateField("FEATURES", "edges", selectEdges(get()).filter((e) => e.id !== id));
  },

  moveFeature: (id, pos) => {
    get().updateFeature(id, { position: pos });
  },

  applyProposal: (graph) => {
    get().updateField("FEATURES", "features", graph.features);
    get().updateField("FEATURES", "edges", graph.edges);
  },
}));
