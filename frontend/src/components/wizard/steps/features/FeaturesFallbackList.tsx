"use client";

interface Props {
  projectId: string;
}

// Mobile fallback stub — full implementation follows in T17.
export function FeaturesFallbackList({ projectId: _projectId }: Props) {
  return (
    <div className="p-4 text-sm text-muted-foreground">
      Mobile fallback (T17).
    </div>
  );
}
