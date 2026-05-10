import { create } from "zustand";
import {
  analyzeDesignImage,
  completeDesignWorkbench,
  generateDesign,
  getDesignWorkbench,
  saveDesignInput,
  type DesignWorkbench,
} from "@/lib/api";

interface DesignWorkbenchState {
  workbench: DesignWorkbench | null;
  loading: boolean;
  working: boolean;
  analyzingImage: boolean;
  error: string | null;
  load: (projectId: string) => Promise<void>;
  saveInput: (projectId: string, description: string, file?: File | null) => Promise<void>;
  analyzeImage: (projectId: string) => Promise<boolean>;
  generate: (projectId: string) => Promise<void>;
  complete: (projectId: string) => Promise<void>;
  reset: () => void;
}

export const useDesignWorkbenchStore = create<DesignWorkbenchState>((set) => ({
  workbench: null,
  loading: false,
  working: false,
  analyzingImage: false,
  error: null,

  load: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const workbench = await getDesignWorkbench(projectId);
      set({ workbench, loading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to load design workbench", loading: false });
    }
  },

  saveInput: async (projectId, description, file) => {
    set({ working: true, error: null });
    try {
      const workbench = await saveDesignInput(projectId, description, file);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to save design input", working: false });
    }
  },

  analyzeImage: async (projectId) => {
    set({ analyzingImage: true, error: null });
    try {
      const workbench = await analyzeDesignImage(projectId);
      set({ workbench, analyzingImage: false });
      return true;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to analyze design image";
      set((state) => ({
        error: message,
        analyzingImage: false,
        workbench: state.workbench
          ? { ...state.workbench, imageAnalysisError: message }
          : state.workbench,
      }));
      return false;
    }
  },

  generate: async (projectId) => {
    set({ working: true, error: null });
    try {
      const workbench = await generateDesign(projectId);
      set({ workbench, working: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to generate design", working: false });
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

  reset: () => set({ workbench: null, loading: false, working: false, analyzingImage: false, error: null }),
}));
