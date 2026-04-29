"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleList } from "./BundleList";
import { BundleDetail } from "./BundleDetail";

export function AssetBundlesPage() {
  const { load } = useAssetBundleStore();

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-hidden">
          <BundleList />
        </aside>
        <main className="flex-1 overflow-hidden">
          <BundleDetail />
        </main>
      </div>
    </div>
  );
}
