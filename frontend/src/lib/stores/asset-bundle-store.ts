import { create } from "zustand";
import {
  listAssetBundles,
  getAssetBundle,
  uploadAssetBundle,
  deleteAssetBundle,
  fetchAssetBundleFile,
  type AssetBundleListItem,
  type AssetBundleDetail,
  type StepType,
} from "@/lib/api";
import {
  diffMissingTriples,
  getAllPossibleTriples,
  type BundleTriple,
} from "@/lib/asset-bundles/possible-triples";

interface LoadedFile {
  path: string;
  contentType: string;
  text?: string;
  blobUrl?: string;
}

interface AssetBundleState {
  bundles: AssetBundleListItem[];
  selectedBundleId: string | null;
  selectedBundle: AssetBundleDetail | null;
  selectedFilePath: string | null;
  loadedFile: LoadedFile | null;
  loading: boolean;
  uploading: boolean;
  error: string | null;
  filterStep: StepType | "ALL";

  load: () => Promise<void>;
  setFilter: (step: StepType | "ALL") => void;
  select: (id: string | null) => Promise<void>;
  selectFile: (relativePath: string | null) => Promise<void>;
  upload: (file: File) => Promise<void>;
  delete: (step: StepType, field: string, value: string) => Promise<void>;
  clearError: () => void;

  // Coverage-View
  activeTab: "uploaded" | "missing";
  selectedMissingTripleId: string | null;

  setActiveTab: (tab: "uploaded" | "missing") => void;
  selectMissingTriple: (id: string | null) => void;
  getMissingTriples: () => BundleTriple[];
}

export const useAssetBundleStore = create<AssetBundleState>((set, get) => ({
  bundles: [],
  selectedBundleId: null,
  selectedBundle: null,
  selectedFilePath: null,
  loadedFile: null,
  loading: false,
  uploading: false,
  error: null,
  filterStep: "ALL",

  async load() {
    set({ loading: true, error: null });
    try {
      const bundles = await listAssetBundles();
      set({ bundles, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  setFilter(step) {
    set({ filterStep: step });
  },

  async select(id) {
    if (id === null) {
      set({ selectedBundleId: null, selectedBundle: null, selectedFilePath: null, loadedFile: null });
      return;
    }
    const bundle = get().bundles.find((b) => b.id === id);
    if (!bundle) return;
    set({ selectedBundleId: id, selectedBundle: null, selectedFilePath: null, loadedFile: null });
    try {
      const detail = await getAssetBundle(bundle.step, bundle.field, bundle.value);
      set({ selectedBundle: detail });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  async selectFile(relativePath) {
    const previousBlob = get().loadedFile?.blobUrl;
    if (previousBlob) URL.revokeObjectURL(previousBlob);

    if (relativePath === null) {
      set({ selectedFilePath: null, loadedFile: null });
      return;
    }
    const bundle = get().selectedBundle;
    if (!bundle) return;
    const fileEntry = bundle.files.find((f) => f.relativePath === relativePath);
    if (!fileEntry) return;

    set({ selectedFilePath: relativePath, loadedFile: null });
    try {
      const res = await fetchAssetBundleFile(
        bundle.manifest.step,
        bundle.manifest.field,
        bundle.manifest.value,
        relativePath,
      );
      if (!res.ok) throw new Error(`Load failed: ${res.status}`);

      const ct = fileEntry.contentType;
      const isText = ct.startsWith("text/") || ct === "application/json"
        || ct === "application/yaml" || ct === "application/typescript"
        || ct === "application/javascript";

      if (isText) {
        const text = await res.text();
        set({ loadedFile: { path: relativePath, contentType: ct, text } });
      } else {
        const blob = await res.blob();
        const blobUrl = URL.createObjectURL(blob);
        set({ loadedFile: { path: relativePath, contentType: ct, blobUrl } });
      }
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  async upload(file) {
    set({ uploading: true, error: null });
    try {
      const result = await uploadAssetBundle(file);
      const bundles = await listAssetBundles();
      set({ bundles, uploading: false });
      await get().select(result.manifest.id);
    } catch (e) {
      set({ error: (e as Error).message, uploading: false });
    }
  },

  async delete(step, field, value) {
    set({ error: null });
    try {
      await deleteAssetBundle(step, field, value);
      const bundles = await listAssetBundles();
      set({ bundles, selectedBundleId: null, selectedBundle: null, selectedFilePath: null, loadedFile: null });
    } catch (e) {
      set({ error: (e as Error).message });
      throw e;
    }
  },

  clearError() {
    set({ error: null });
  },

  // Coverage-View
  activeTab: "uploaded",
  selectedMissingTripleId: null,

  setActiveTab(tab) {
    set({ activeTab: tab });
  },

  selectMissingTriple(id) {
    set({ selectedMissingTripleId: id });
  },

  getMissingTriples() {
    return diffMissingTriples(getAllPossibleTriples(), get().bundles);
  },
}));
