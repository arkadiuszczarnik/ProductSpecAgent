import { create } from "zustand";
import {
  addDesignTextInput,
  analyzeDesignInputs,
  completeDesignWorkbench,
  generateDesignVariant,
  getDesignWorkbench,
  proposeDesignScreens,
  setActiveDesignVariant,
  type DesignWorkbench,
} from "@/lib/api";

interface DesignWorkbenchState {
  workbench: DesignWorkbench | null;
  selectedScreenId: string | null;
  loading: boolean;
  working: boolean;
  error: string | null;
  load: (projectId: string) => Promise<void>;
  addTextInput: (projectId: string, text: string) => Promise<void>;
  analyze: (projectId: string) => Promise<void>;
  proposeScreens: (projectId: string) => Promise<void>;
  generateVariant: (projectId: string, screenId: string, prompt: string) => Promise<void>;
  setActiveVariant: (projectId: string, screenId: string, variantId: string) => Promise<void>;
  complete: (projectId: string) => Promise<void>;
  selectScreen: (screenId: string | null) => void;
  reset: () => void;
}

export const useDesignWorkbenchStore = create<DesignWorkbenchState>((set) => ({
  workbench: null,
  selectedScreenId: null,
  loading: false,
  working: false,
  error: null,

  load: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const workbench = await getDesignWorkbench(projectId);
      set({ workbench, selectedScreenId: workbench.screens[0]?.id ?? null, loading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to load design workbench", loading: false });
    }
  },

  addTextInput: async (projectId, text) => {
    set({ working: true, error: null });
    try {
      const workbench = await addDesignTextInput(projectId, text);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to add design input", working: false });
    }
  },

  analyze: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await analyzeDesignInputs(projectId);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to analyze references", working: false });
    }
  },

  proposeScreens: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await proposeDesignScreens(projectId);
      set({ workbench, selectedScreenId: workbench.screens[0]?.id ?? null, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to propose screens", working: false });
    }
  },

  generateVariant: async (projectId, screenId, prompt) => {
    set({ working: true, error: null });
    try {
      const workbench = await generateDesignVariant(projectId, screenId, prompt);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to generate variant", working: false });
    }
  },

  setActiveVariant: async (projectId, screenId, variantId) => {
    set({ working: true, error: null });
    try {
      const workbench = await setActiveDesignVariant(projectId, screenId, variantId);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to activate variant", working: false });
    }
  },

  complete: async (projectId) => {
    set({ working: true, error: null });
    try {
      await completeDesignWorkbench(projectId);
      set({ working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to complete design step", working: false });
    }
  },

  selectScreen: (screenId) => set({ selectedScreenId: screenId }),
  reset: () => set({ workbench: null, selectedScreenId: null, loading: false, working: false, error: null }),
}));
