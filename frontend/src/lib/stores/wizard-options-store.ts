import { create } from "zustand";
import {
  getAdminWizardOptions,
  getWizardOptions,
  resetAdminWizardOptions,
  saveAdminWizardOptions,
  type WizardOptionCatalog,
} from "@/lib/api";

interface WizardOptionsState {
  catalog: WizardOptionCatalog | null;
  adminCatalog: WizardOptionCatalog | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  loadCatalog: () => Promise<void>;
  loadAdminCatalog: () => Promise<void>;
  saveAdminCatalog: (catalog: WizardOptionCatalog) => Promise<void>;
  resetAdminCatalog: () => Promise<void>;
  setAdminCatalog: (catalog: WizardOptionCatalog) => void;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Wizard options request failed";
}

export const useWizardOptionsStore = create<WizardOptionsState>((set) => {
  let nextRequestId = 0;
  let activeCatalogLoadId = 0;
  let activeAdminLoadId = 0;
  let activeMutationId = 0;
  const activeLoadIds = new Set<number>();

  const cancelActiveLoads = () => {
    activeCatalogLoadId = 0;
    activeAdminLoadId = 0;
    activeLoadIds.clear();
  };

  const startLoad = (kind: "catalog" | "adminCatalog") => {
    const requestId = ++nextRequestId;
    if (kind === "catalog") {
      activeCatalogLoadId = requestId;
    } else {
      activeAdminLoadId = requestId;
    }
    activeLoadIds.add(requestId);
    set({ loading: true, error: null });
    return requestId;
  };

  const isCurrentLoad = (kind: "catalog" | "adminCatalog", requestId: number) =>
    (kind === "catalog" ? activeCatalogLoadId : activeAdminLoadId) === requestId;

  const finishLoad = (requestId: number) => {
    activeLoadIds.delete(requestId);
    return activeLoadIds.size > 0;
  };

  const finishStaleLoad = (requestId: number) => {
    if (activeLoadIds.has(requestId)) {
      set({ loading: finishLoad(requestId) });
    }
  };

  return {
    catalog: null,
    adminCatalog: null,
    loading: false,
    saving: false,
    error: null,

    async loadCatalog() {
      const requestId = startLoad("catalog");
      try {
        const catalog = await getWizardOptions();
        if (!isCurrentLoad("catalog", requestId)) {
          finishStaleLoad(requestId);
          return;
        }
        set({ catalog, loading: finishLoad(requestId) });
      } catch (error) {
        if (!isCurrentLoad("catalog", requestId)) {
          finishStaleLoad(requestId);
          return;
        }
        set({ error: errorMessage(error), loading: finishLoad(requestId) });
      }
    },

    async loadAdminCatalog() {
      const requestId = startLoad("adminCatalog");
      try {
        const adminCatalog = await getAdminWizardOptions();
        if (!isCurrentLoad("adminCatalog", requestId)) {
          finishStaleLoad(requestId);
          return;
        }
        set({ adminCatalog, loading: finishLoad(requestId) });
      } catch (error) {
        if (!isCurrentLoad("adminCatalog", requestId)) {
          finishStaleLoad(requestId);
          return;
        }
        set({ error: errorMessage(error), loading: finishLoad(requestId) });
      }
    },

    async saveAdminCatalog(catalog) {
      const requestId = ++nextRequestId;
      activeMutationId = requestId;
      cancelActiveLoads();
      set({ saving: true, loading: false, error: null });
      try {
        const saved = await saveAdminWizardOptions(catalog);
        if (activeMutationId !== requestId) return;
        cancelActiveLoads();
        set({ adminCatalog: saved, catalog: saved, saving: false, loading: false });
      } catch (error) {
        if (activeMutationId === requestId) {
          set({ error: errorMessage(error), saving: false });
        }
        throw error;
      }
    },

    async resetAdminCatalog() {
      const requestId = ++nextRequestId;
      activeMutationId = requestId;
      cancelActiveLoads();
      set({ saving: true, loading: false, error: null });
      try {
        const reset = await resetAdminWizardOptions();
        if (activeMutationId !== requestId) return;
        cancelActiveLoads();
        set({ adminCatalog: reset, catalog: reset, saving: false, loading: false });
      } catch (error) {
        if (activeMutationId === requestId) {
          set({ error: errorMessage(error), saving: false });
        }
        throw error;
      }
    },

    setAdminCatalog(catalog) {
      set({ adminCatalog: catalog });
    },
  };
});
