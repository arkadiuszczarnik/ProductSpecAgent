"use client";
import { useEffect, useState } from "react";
import { FeaturesGraphEditor } from "./features/FeaturesGraphEditor";
import { FeaturesFallbackList } from "./features/FeaturesFallbackList";

export function FeaturesForm({ projectId }: { projectId: string }) {
  const [wide, setWide] = useState(true);

  useEffect(() => {
    const check = () => setWide(window.innerWidth >= 768);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  if (!wide) return <FeaturesFallbackList />;
  return <FeaturesGraphEditor projectId={projectId} />;
}
