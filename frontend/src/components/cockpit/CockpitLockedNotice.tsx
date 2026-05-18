import Link from "next/link";

interface CockpitLockedNoticeProps {
  projectId: string;
}

export function CockpitLockedNotice({ projectId }: CockpitLockedNoticeProps) {
  return (
    <div className="flex min-h-full items-center justify-center bg-background px-6 py-10">
      <div className="w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-foreground">
          Cockpit erst nach abgeschlossenem Wizard verfuegbar
        </h1>
        <p className="mt-3 text-sm text-muted-foreground">
          Schliesse zuerst den Wizard im normalen Workspace ab. Danach steht dir das Cockpit
          direkt auf dieser Route zur Verfuegung.
        </p>
        <Link
          href={`/projects/${projectId}`}
          className="mt-5 inline-flex text-sm font-medium text-primary hover:underline"
        >
          Zurueck zum Workspace
        </Link>
      </div>
    </div>
  );
}
