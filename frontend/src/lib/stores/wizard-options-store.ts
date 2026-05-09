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

export const useWizardOptionsStore = create<WizardOptionsState>((set) => ({
  catalog: null,
  adminCatalog: null,
  loading: false,
  saving: false,
  error: null,

  async loadCatalog() {
    set({ loading: true, error: null });
    try {
      const catalog = await getWizardOptions();
      set({ catalog, loading: false });
    } catch (error) {
      set({ error: errorMessage(error), loading: false });
    }
  },

  async loadAdminCatalog() {
    set({ loading: true, error: null });
    try {
      const adminCatalog = await getAdminWizardOptions();
      set({ adminCatalog, loading: false });
    } catch (error) {
      set({ error: errorMessage(error), loading: false });
    }
  },

  async saveAdminCatalog(catalog) {
    set({ saving: true, error: null });
    try {
      const saved = await saveAdminWizardOptions(catalog);
      set({ adminCatalog: saved, catalog: saved, saving: false });
    } catch (error) {
      set({ error: errorMessage(error), saving: false });
      throw error;
    }
  },

  async resetAdminCatalog() {
    set({ saving: true, error: null });
    try {
      const reset = await resetAdminWizardOptions();
      set({ adminCatalog: reset, catalog: reset, saving: false });
    } catch (error) {
      set({ error: errorMessage(error), saving: false });
      throw error;
    }
  },

  setAdminCatalog(catalog) {
    set({ adminCatalog: catalog });
  },
}));
