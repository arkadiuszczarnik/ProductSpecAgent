import { create } from "zustand";
import {
  addDesignImageInput,
  addDesignScreen,
  addDesignSnippetInput,
  addDesignTextInput,
  analyzeDesignInputs,
  applyDesignSuggestion,
  completeDesignWorkbench,
  deleteDesignScreen,
  generateDesignVariant,
  getDesignWorkbench,
  proposeDesignScreens,
  setActiveDesignVariant,
  updateDesignInput,
  updateDesignScreen,
  type DesignWorkbench,
  type UpdateDesignInputRequest,
} from "@/lib/api";

interface DesignWorkbenchState {
  workbench: DesignWorkbench | null;
  selectedScreenId: string | null;
  loading: boolean;
  working: boolean;
  error: string | null;
  load: (projectId: string) => Promise<void>;
  addTextInput: (projectId: string, text: string) => Promise<void>;
  addImageInput: (projectId: string, file: File) => Promise<void>;
  addSnippetInput: (projectId: string, snippet: string, name?: string) => Promise<void>;
  updateInput: (projectId: string, inputId: string, request: UpdateDesignInputRequest) => Promise<void>;
  analyze: (projectId: string) => Promise<void>;
  proposeScreens: (projectId: string) => Promise<void>;
  addScreen: (projectId: string, name: string, purpose: string) => Promise<void>;
  updateScreen: (projectId: string, screenId: string, name: string, purpose: string) => Promise<void>;
  deleteScreen: (projectId: string, screenId: string) => Promise<void>;
  generateVariant: (projectId: string, screenId: string, prompt: string) => Promise<void>;
  applySuggestion: (projectId: string, screenId: string, suggestionId: string) => Promise<void>;
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

  addImageInput: async (projectId, file) => {
    set({ working: true, error: null });
    try {
      const workbench = await addDesignImageInput(projectId, file);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to add design image", working: false });
    }
  },

  addSnippetInput: async (projectId, snippet, name) => {
    set({ working: true, error: null });
    try {
      const workbench = await addDesignSnippetInput(projectId, snippet, name);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to add design snippet", working: false });
    }
  },

  updateInput: async (projectId, inputId, request) => {
    set({ working: true, error: null });
    try {
      const workbench = await updateDesignInput(projectId, inputId, request);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to update design input", working: false });
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

  addScreen: async (projectId, name, purpose) => {
    set({ working: true, error: null });
    try {
      const workbench = await addDesignScreen(projectId, name, purpose);
      set({ workbench, selectedScreenId: workbench.screens[workbench.screens.length - 1]?.id ?? null, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to add screen", working: false });
    }
  },

  updateScreen: async (projectId, screenId, name, purpose) => {
    set({ working: true, error: null });
    try {
      const workbench = await updateDesignScreen(projectId, screenId, name, purpose);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to update screen", working: false });
    }
  },

  deleteScreen: async (projectId, screenId) => {
    set({ working: true, error: null });
    try {
      const workbench = await deleteDesignScreen(projectId, screenId);
      const selectedScreenId = workbench.screens[0]?.id ?? null;
      set({ workbench, selectedScreenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to remove screen", working: false });
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

  applySuggestion: async (projectId, screenId, suggestionId) => {
    set({ working: true, error: null });
    try {
      const workbench = await applyDesignSuggestion(projectId, screenId, suggestionId);
      set({ workbench, selectedScreenId: screenId, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to apply suggestion", working: false });
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
