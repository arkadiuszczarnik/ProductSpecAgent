// frontend/src/components/asset-bundles/AssetBundlesPage.tsx
"use client";

import { useEffect } from "react";
import { useAssetBundleStore } from "@/lib/stores/asset-bundle-store";
import { BundleList } from "./BundleList";
import { BundleDetail } from "./BundleDetail";
import { MissingBundleList } from "./MissingBundleList";
import { ManifestStubView } from "./ManifestStubView";
import { cn } from "@/lib/utils";

export function AssetBundlesPage() {
  const load = useAssetBundleStore((s) => s.load);
  const bundles = useAssetBundleStore((s) => s.bundles);
  const activeTab = useAssetBundleStore((s) => s.activeTab);
  const setActiveTab = useAssetBundleStore((s) => s.setActiveTab);
  const getMissingTriples = useAssetBundleStore((s) => s.getMissingTriples);

  useEffect(() => {
    load();
  }, [load]);

  const missingCount = getMissingTriples().length;

  return (
    <div className="flex h-screen flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-xl font-semibold">Asset Bundles</h1>
        <p className="text-sm text-muted-foreground">
          Kuratierte Claude-Code Skills, Commands und Agents.
        </p>
      </header>
      <div className="flex items-center gap-2 border-b px-6 py-2">
        <TabPill
          active={activeTab === "uploaded"}
          onClick={() => setActiveTab("uploaded")}
        >
          Hochgeladen ({bundles.length})
        </TabPill>
        <TabPill
          active={activeTab === "missing"}
          onClick={() => setActiveTab("missing")}
        >
          Fehlend ({missingCount})
        </TabPill>
      </div>
      <div className="flex flex-1 min-h-0">
        <aside className="w-96 border-r overflow-hidden">
          {activeTab === "uploaded" ? <BundleList /> : <MissingBundleList />}
        </aside>
        <main className="flex-1 overflow-hidden">
          {activeTab === "uploaded" ? <BundleDetail /> : <ManifestStubView />}
        </main>
      </div>
    </div>
  );
}

function TabPill({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        "rounded-full px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "bg-primary text-primary-foreground"
          : "bg-muted text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
}
