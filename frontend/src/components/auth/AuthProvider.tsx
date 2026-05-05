"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/stores/auth-store";
import { setUnauthorizedHandler } from "@/lib/api";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const initialize = useAuthStore((s) => s.initialize);
  const clear = useAuthStore((s) => s.clear);
  const router = useRouter();

  useEffect(() => {
    initialize();
  }, [initialize]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clear();
      router.replace("/login");
    });
    return () => setUnauthorizedHandler(null);
  }, [clear, router]);

  return <>{children}</>;
}
