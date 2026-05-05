"use client";

import { LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/stores/auth-store";

export function LogoutButton() {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);
  const user = useAuthStore((s) => s.user);

  async function handle() {
    await logout();
    router.replace("/login");
  }

  return (
    <button
      onClick={handle}
      className="flex h-10 w-10 items-center justify-center rounded-lg text-sidebar-foreground hover:text-zinc-200 transition-colors duration-150"
      title={user ? `Logout (${user.email})` : "Logout"}
    >
      <LogOut size={20} />
    </button>
  );
}
