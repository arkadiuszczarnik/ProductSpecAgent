import { create } from "zustand";
import type {
  Project,
  FlowState,
  StepType,
  ChatResponse,
} from "@/lib/api";
import { getProject, getFlowState, sendChatMessage } from "@/lib/api";

export type ChatMessageRole = "user" | "agent" | "system";

export interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  content: string;
  timestamp: number;
}

interface ProjectState {
  project: Project | null;
  projectLoading: boolean;
  projectError: string | null;
  flowState: FlowState | null;
  flowLoading: boolean;
  selectedStep: StepType | null;
  messages: ChatMessage[];
  chatSending: boolean;

  loadProject: (id: string) => Promise<void>;
  setProject: (project: Project) => void;
  loadFlowState: (projectId: string) => Promise<void>;
  selectStep: (key: StepType | null) => void;
  sendMessage: (projectId: string, message: string) => Promise<void>;
  reset: () => void;
}

let msgCounter = 0;
function makeId(): string {
  return `msg-${Date.now()}-${++msgCounter}`;
}

export const useProjectStore = create<ProjectState>((set, get) => ({
  project: null,
  projectLoading: false,
  projectError: null,
  flowState: null,
  flowLoading: false,
  selectedStep: null,
  messages: [],
  chatSending: false,

  loadProject: async (id: string) => {
    set({ projectLoading: true, projectError: null });
    try {
      const resp = await getProject(id);
      set({ project: resp.project, flowState: resp.flowState, projectLoading: false });
    } catch (err) {
      set({ projectError: err instanceof Error ? err.message : "Failed to load", projectLoading: false });
    }
  },

  setProject: (project) => set({ project }),

  loadFlowState: async (projectId: string) => {
    set({ flowLoading: true });
    try {
      const flowState = await getFlowState(projectId);
      set({ flowState, flowLoading: false });
    } catch {
      set({ flowLoading: false });
    }
  },

  selectStep: (key) => set({ selectedStep: key }),

  sendMessage: async (projectId: string, message: string) => {
    const userMsg: ChatMessage = {
      id: makeId(),
      role: "user",
      content: message,
      timestamp: Date.now(),
    };
    set((s) => ({ messages: [...s.messages, userMsg], chatSending: true }));

    try {
      const locale = typeof navigator !== "undefined" ? navigator.language : "de";
      const resp: ChatResponse = await sendChatMessage(projectId, { message, locale });
      const agentMsg: ChatMessage = {
        id: makeId(),
        role: "agent",
        content: resp.message,
        timestamp: Date.now(),
      };
      set((s) => ({
        messages: [...s.messages, agentMsg],
        chatSending: false,
      }));

      // Reload flow state if it changed
      if (resp.flowStateChanged) {
        const flowState = await getFlowState(projectId);
        set({ flowState });
      }

      // If a decision was triggered, fetch it
      if (resp.decisionId) {
        const { getDecision } = await import("@/lib/api");
        const decision = await getDecision(projectId, resp.decisionId);
        const { useDecisionStore } = await import("@/lib/stores/decision-store");
        useDecisionStore.getState().addDecision(decision);
      }

      if (resp.clarificationId) {
        const { getClarification } = await import("@/lib/api");
        const clarification = await getClarification(projectId, resp.clarificationId);
        const { useClarificationStore } = await import("@/lib/stores/clarification-store");
        useClarificationStore.getState().addClarification(clarification);
      }
    } catch (err) {
      const errMsg: ChatMessage = {
        id: makeId(),
        role: "system",
        content: `Error: ${err instanceof Error ? err.message : "Failed to send"}`,
        timestamp: Date.now(),
      };
      set((s) => ({ messages: [...s.messages, errMsg], chatSending: false }));
    }
  },

  reset: () => set({
    project: null, projectLoading: false, projectError: null,
    flowState: null, flowLoading: false,
    selectedStep: null, messages: [], chatSending: false,
  }),
}));
