import { create } from "zustand";
import { getFeatures, type FeatureFlags } from "@/lib/api";

interface FeatureState {
  flags: FeatureFlags | null;
  loading: boolean;
  loadFeatures: () => Promise<void>;
}

export const useFeatureStore = create<FeatureState>((set, get) => ({
  flags: null,
  loading: false,
  loadFeatures: async () => {
    if (get().flags || get().loading) return;
    set({ loading: true });
    try {
      const flags = await getFeatures();
      set({ flags, loading: false });
    } catch {
      set({ flags: { graphmeshEnabled: false }, loading: false });
    }
  },
}));
