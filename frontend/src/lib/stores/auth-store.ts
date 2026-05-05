import { create } from "zustand";
import { authLogin, authLogout, authMe, authRegister, type AuthMe } from "@/lib/api";

type AuthStatus = "loading" | "authenticated" | "guest";

interface AuthState {
  user: AuthMe | null;
  status: AuthStatus;

  initialize: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: "loading",

  initialize: async () => {
    try {
      const user = await authMe();
      set({ user, status: "authenticated" });
    } catch {
      set({ user: null, status: "guest" });
    }
  },

  login: async (email, password) => {
    const user = await authLogin(email, password);
    set({ user, status: "authenticated" });
  },

  register: async (email, password) => {
    const user = await authRegister(email, password);
    set({ user, status: "authenticated" });
  },

  logout: async () => {
    try {
      await authLogout();
    } finally {
      set({ user: null, status: "guest" });
    }
  },

  clear: () => set({ user: null, status: "guest" }),
}));
