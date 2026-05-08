import { create } from "zustand";
import type { SpecTask, CoverageMap } from "@/lib/api";
import {
  listTasks as apiList,
  generatePlan as apiGenerate,
  updateTask as apiUpdate,
  deleteTask as apiDelete,
  getTaskCoverage as apiCoverage,
} from "@/lib/api";

interface TaskState {
  tasks: SpecTask[];
  coverage: CoverageMap;
  loading: boolean;
  generating: boolean;
  selectedTaskId: string | null;

  loadTasks: (projectId: string) => Promise<void>;
  generatePlan: (projectId: string) => Promise<void>;
  updateTask: (projectId: string, taskId: string, title?: string, status?: string) => Promise<void>;
  deleteTask: (projectId: string, taskId: string) => Promise<void>;
  loadCoverage: (projectId: string) => Promise<void>;
  selectTask: (id: string | null) => void;
  reset: () => void;
}

export const useTaskStore = create<TaskState>((set) => ({
  tasks: [],
  coverage: {},
  loading: false,
  generating: false,
  selectedTaskId: null,

  loadTasks: async (projectId) => {
    set({ loading: true });
    try {
      const tasks = await apiList(projectId);
      set({ tasks, loading: false });
    } catch {
      set({ loading: false });
    }
  },

  generatePlan: async (projectId) => {
    set({ generating: true });
    try {
      const tasks = await apiGenerate(projectId);
      set({ tasks, generating: false });
    } catch {
      set({ generating: false });
    }
  },

  updateTask: async (projectId, taskId, title, status) => {
    const data: Record<string, unknown> = {};
    if (title !== undefined) data.title = title;
    if (status !== undefined) data.status = status;
    const updated = await apiUpdate(projectId, taskId, data);
    set((s) => ({ tasks: s.tasks.map((t) => (t.id === taskId ? updated : t)) }));
  },

  deleteTask: async (projectId, taskId) => {
    await apiDelete(projectId, taskId);
    set((s) => ({ tasks: s.tasks.filter((t) => t.id !== taskId) }));
  },

  loadCoverage: async (projectId) => {
    try {
      const coverage = await apiCoverage(projectId);
      set({ coverage });
    } catch {
      set({ coverage: {} });
    }
  },

  selectTask: (id) => set({ selectedTaskId: id }),
  reset: () => set({ tasks: [], coverage: {}, loading: false, generating: false, selectedTaskId: null }),
}));
