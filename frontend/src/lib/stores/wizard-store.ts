import { create } from "zustand";
import type {
  WizardData,
  WizardStepData,
  WizardFeature,
  WizardFeatureEdge,
  WizardFeatureGraph,
} from "@/lib/api";
import { getWizardData, saveWizardStep, completeWizardStep } from "@/lib/api";
import { formatStepFields } from "@/lib/step-field-labels";
import { useProjectStore } from "@/lib/stores/project-store";
import { getVisibleSteps } from "@/lib/category-step-config";
import { wouldCreateCycle } from "@/lib/graph/cycleCheck";

export const WIZARD_STEPS = [
  { key: "IDEA", label: "Idee" },
  { key: "PROBLEM", label: "Problem" },
  { key: "TARGET_AUDIENCE", label: "Zielgruppe" },
  { key: "SCOPE", label: "Scope" },
  { key: "MVP", label: "MVP" },
  { key: "FEATURES", label: "Features" },
  { key: "ARCHITECTURE", label: "Architektur" },
  { key: "BACKEND", label: "Backend" },
  { key: "FRONTEND", label: "Frontend" },
] as const;

interface WizardState {
  data: WizardData | null;
  activeStep: string;
  loading: boolean;
  saving: boolean;
  chatPending: boolean;

  loadWizard: (projectId: string) => Promise<void>;
  setActiveStep: (step: string) => void;
  updateField: (step: string, field: string, value: any) => void;
  saveStep: (projectId: string, step: string) => Promise<void>;
  completeStep: (projectId: string, step: string) => Promise<void>;
  goNext: () => void;
  goPrev: () => void;
  reset: () => void;
  getCategory: () => string | undefined;
  visibleSteps: () => typeof WIZARD_STEPS[number][];

  // Feature graph actions (FEATURES step)
  getFeatures: () => WizardFeature[];
  getEdges: () => WizardFeatureEdge[];
  addFeature: (draft: Omit<WizardFeature, "id">) => string;
  updateFeature: (id: string, patch: Partial<WizardFeature>) => void;
  removeFeature: (id: string) => void;
  addEdge: (from: string, to: string) => boolean;
  removeEdge: (id: string) => void;
  moveFeature: (id: string, pos: { x: number; y: number }) => void;
  applyProposal: (graph: WizardFeatureGraph) => void;
}

let saveTimeout: ReturnType<typeof setTimeout> | null = null;

export const useWizardStore = create<WizardState>((set, get) => ({
  data: null,
  activeStep: "IDEA",
  loading: false,
  saving: false,
  chatPending: false,

  loadWizard: async (projectId) => {
    set({ loading: true });
    try {
      const data = await getWizardData(projectId);
      set({ data, loading: false });
    } catch {
      set({ data: { projectId, steps: {} }, loading: false });
    }
  },

  setActiveStep: (step) => set({ activeStep: step }),

  getCategory: () => {
    const { data } = get();
    return data?.steps["IDEA"]?.fields?.category as string | undefined;
  },

  visibleSteps: () => {
    const category = get().getCategory();
    const visible = getVisibleSteps(category);
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
      const { activeStep } = get();
      const visible = getVisibleSteps(value as string);
      if (!visible.includes(activeStep)) {
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
    if (!data) return;
    const stepData = data.steps[step] ?? { fields: {}, completedAt: null };
    const completed = { ...stepData, completedAt: new Date().toISOString() };

    // Save the step completion locally
    set({ saving: true });
    try {
      const result = await saveWizardStep(projectId, step, completed);
      set({ data: result, saving: false });
    } catch {
      set({ saving: false });
      return;
    }

    // Format fields as readable chat message
    const fields = stepData.fields ?? {};
    const plainFields: Record<string, any> = {};
    for (const [k, v] of Object.entries(fields)) {
      plainFields[k] = typeof v === "object" && v !== null && "value" in v
        ? (v as any).value
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

      // Reload flow state so the Explorer and StepIndicator reflect the
      // backend-side progression (and so the Explorer re-fetches files when
      // completedSteps changes — its useEffect depends on that count).
      await useProjectStore.getState().loadFlowState(projectId);

      // Navigate to next step
      if (response.nextStep) {
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
      if (response.decisionId) {
        const { getDecision } = await import("@/lib/api");
        const decision = await getDecision(projectId, response.decisionId);
        const { useDecisionStore } = await import("@/lib/stores/decision-store");
        useDecisionStore.getState().addDecision(decision);
      }

      // If a clarification was triggered, fetch it
      if (response.clarificationId) {
        const { getClarification } = await import("@/lib/api");
        const clarification = await getClarification(projectId, response.clarificationId);
        const { useClarificationStore } = await import("@/lib/stores/clarification-store");
        useClarificationStore.getState().addClarification(clarification);
      }

      // Handle export trigger on last step
      if (response.exportTriggered) {
        const systemMsg = {
          id: `wizard-export-${Date.now()}`,
          role: "system" as const,
          content: "Export wurde gestartet. Das Projekt wird jetzt exportiert...",
          timestamp: Date.now(),
        };
        useProjectStore.setState((s) => ({
          messages: [...s.messages, systemMsg],
        }));
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
      set({ chatPending: false });
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

  reset: () => set({ data: null, activeStep: "IDEA", loading: false, saving: false, chatPending: false }),

  // --- Feature graph actions ---

  getFeatures: () =>
    (get().data?.steps.FEATURES?.fields.features as WizardFeature[] | undefined) ?? [],

  getEdges: () =>
    (get().data?.steps.FEATURES?.fields.edges as WizardFeatureEdge[] | undefined) ?? [],

  addFeature: (draft) => {
    const id =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `feat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const feature: WizardFeature = { id, ...draft };
    get().updateField("FEATURES", "features", [...get().getFeatures(), feature]);
    return id;
  },

  updateFeature: (id, patch) => {
    const next = get().getFeatures().map((f) => (f.id === id ? { ...f, ...patch } : f));
    get().updateField("FEATURES", "features", next);
  },

  removeFeature: (id) => {
    get().updateField(
      "FEATURES",
      "features",
      get().getFeatures().filter((f) => f.id !== id),
    );
    get().updateField(
      "FEATURES",
      "edges",
      get().getEdges().filter((e) => e.from !== id && e.to !== id),
    );
  },

  addEdge: (from, to) => {
    if (wouldCreateCycle(get().getEdges(), from, to)) return false;
    const id =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `edge-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const edge: WizardFeatureEdge = { id, from, to };
    get().updateField("FEATURES", "edges", [...get().getEdges(), edge]);
    return true;
  },

  removeEdge: (id) => {
    get().updateField("FEATURES", "edges", get().getEdges().filter((e) => e.id !== id));
  },

  moveFeature: (id, pos) => {
    get().updateFeature(id, { position: pos });
  },

  applyProposal: (graph) => {
    get().updateField("FEATURES", "features", graph.features);
    get().updateField("FEATURES", "edges", graph.edges);
  },
}));
