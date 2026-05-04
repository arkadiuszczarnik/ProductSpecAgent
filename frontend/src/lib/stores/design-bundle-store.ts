import { create } from "zustand";
import type { DesignBundle } from "@/lib/api";
import { getDesignBundle, uploadDesignBundle, deleteDesignBundle } from "@/lib/api";

interface DesignBundleState {
  bundle: DesignBundle | null;
  loading: boolean;
  uploading: boolean;
  error: string | null;

  loadBundle: (projectId: string) => Promise<void>;
  uploadBundle: (projectId: string, file: File) => Promise<void>;
  deleteBundle: (projectId: string) => Promise<void>;
  reset: () => void;
}

export const useDesignBundleStore = create<DesignBundleState>((set) => ({
  bundle: null,
  loading: false,
  uploading: false,
  error: null,

  loadBundle: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const bundle = await getDesignBundle(projectId);
      set({ bundle, loading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Failed to load", loading: false });
    }
  },

  uploadBundle: async (projectId, file) => {
    set({ uploading: true, error: null });
    try {
      const bundle = await uploadDesignBundle(projectId, file);
      set({ bundle, uploading: false });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Upload failed", uploading: false });
    }
  },

  deleteBundle: async (projectId) => {
    try {
      await deleteDesignBundle(projectId);
      set({ bundle: null });
    } catch (err) {
      set({ error: err instanceof Error ? err.message : "Delete failed" });
    }
  },

  reset: () => set({ bundle: null, loading: false, uploading: false, error: null }),
}));
