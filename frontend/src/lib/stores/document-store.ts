import { create } from "zustand";
import {
  listDocuments as apiListDocuments,
  uploadDocument as apiUploadDocument,
  deleteDocument as apiDeleteDocument,
  type ProjectDocument,
} from "@/lib/api";

const TERMINAL_STATES: ProjectDocument["state"][] = ["EXTRACTED", "FAILED"];
const POLL_INTERVAL_MS = 3000;

interface DocumentState {
  documents: ProjectDocument[];
  loading: boolean;
  uploading: boolean;
  error: string | null;
  pollingTimer: number | null;

  loadDocuments: (projectId: string) => Promise<void>;
  uploadDocument: (projectId: string, file: File) => Promise<void>;
  deleteDocument: (projectId: string, documentId: string) => Promise<void>;
  startPolling: (projectId: string) => void;
  stopPolling: () => void;
  reset: () => void;
}

export const useDocumentStore = create<DocumentState>((set, get) => ({
  documents: [],
  loading: false,
  uploading: false,
  error: null,
  pollingTimer: null,

  loadDocuments: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const docs = await apiListDocuments(projectId);
      set({ documents: docs, loading: false });
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Load failed", loading: false });
    }
  },

  uploadDocument: async (projectId, file) => {
    set({ uploading: true, error: null });
    try {
      const doc = await apiUploadDocument(projectId, file);
      set((s) => ({ documents: [...s.documents, doc], uploading: false }));
      get().startPolling(projectId);
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Upload failed", uploading: false });
    }
  },

  deleteDocument: async (projectId, documentId) => {
    try {
      await apiDeleteDocument(projectId, documentId);
      set((s) => ({ documents: s.documents.filter((d) => d.id !== documentId) }));
    } catch (e) {
      set({ error: e instanceof Error ? e.message : "Delete failed" });
    }
  },

  startPolling: (projectId) => {
    const { pollingTimer } = get();
    if (pollingTimer != null) return;
    const tick = async () => {
      if (typeof document !== "undefined" && document.hidden) return;
      try {
        const docs = await apiListDocuments(projectId);
        set({ documents: docs });
        const allTerminal = docs.every((d) => TERMINAL_STATES.includes(d.state));
        if (allTerminal) get().stopPolling();
      } catch {
        // silently ignore poll errors; keep polling
      }
    };
    const timer = window.setInterval(tick, POLL_INTERVAL_MS);
    set({ pollingTimer: timer });
  },

  stopPolling: () => {
    const { pollingTimer } = get();
    if (pollingTimer != null) {
      window.clearInterval(pollingTimer);
      set({ pollingTimer: null });
    }
  },

  reset: () => {
    get().stopPolling();
    set({ documents: [], loading: false, uploading: false, error: null });
  },
}));
