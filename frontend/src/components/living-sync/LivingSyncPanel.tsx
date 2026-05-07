"use client";

import { useEffect, useState } from "react";
import { Activity, Bot, CheckCircle2, FileCode2, Loader2, RefreshCw, TestTube2, Zap } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getLivingSyncSummary, type LivingSyncFeatureStatus, type LivingSyncSummary } from "@/lib/api";
import { cn } from "@/lib/utils";

const STATUS_STYLE: Record<LivingSyncFeatureStatus, string> = {
  PLANNED: "bg-muted text-muted-foreground",
  IN_PROGRESS: "bg-primary text-primary-foreground",
  BLOCKED: "bg-destructive text-destructive-foreground",
  DONE: "bg-[oklch(0.65_0.15_160)] text-black",
};

interface LivingSyncPanelProps {
  projectId: string;
}

export function LivingSyncPanel({ projectId }: LivingSyncPanelProps) {
  const [summary, setSummary] = useState<LivingSyncSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadSummary(ignoreResult?: () => boolean) {
    setLoading(true);
    setError(null);
    try {
      const next = await getLivingSyncSummary(projectId);
      if (!ignoreResult?.()) setSummary(next);
    } catch (err) {
      if (!ignoreResult?.()) setError(err instanceof Error ? err.message : "Living Sync konnte nicht geladen werden.");
    } finally {
      if (!ignoreResult?.()) setLoading(false);
    }
  }

  useEffect(() => {
    let ignore = false;
    loadSummary(() => ignore);
    return () => {
      ignore = true;
    };
  }, [projectId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading && !summary) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <Loader2 size={20} className="animate-spin" />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto">
      <div className="flex items-center justify-between border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <Activity size={15} className="text-primary" />
          <span className="text-sm font-semibold">Living Sync</span>
        </div>
        <Button size="xs" variant="ghost" onClick={() => loadSummary()} disabled={loading}>
          {loading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          Refresh
        </Button>
      </div>

      {error ? (
        <div className="p-4 text-sm text-destructive">{error}</div>
      ) : !summary ? (
        <EmptyState />
      ) : (
        <div className="space-y-3 p-3">
          <MetricGrid summary={summary} />
          <FeatureStatuses summary={summary} />
          <ChangedFiles summary={summary} />
          <SyncNotes summary={summary} />
          <RecentEvents summary={summary} />
        </div>
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center gap-2 p-8 text-center text-muted-foreground">
      <Bot size={24} className="opacity-30" />
      <p className="text-sm">Noch keine Living-Sync-Daten.</p>
      <p className="text-xs">Der Coding Agent meldet Fortschritt ueber die MCP-Reporting-Endpunkte.</p>
    </div>
  );
}

function MetricGrid({ summary }: { summary: LivingSyncSummary }) {
  return (
    <div className="grid grid-cols-3 gap-2">
      <Metric icon={<CheckCircle2 size={14} />} label="Tests" value={`${summary.tests.passed}/${summary.tests.failed}`} />
      <Metric icon={<Zap size={14} />} label="Tokens" value={summary.tokens.totalTokens.toLocaleString()} />
      <Metric icon={<FileCode2 size={14} />} label="Files" value={summary.changedFiles.length.toString()} />
    </div>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-md border bg-card px-3 py-2">
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        {icon}
        {label}
      </div>
      <div className="mt-1 text-sm font-semibold">{value}</div>
    </div>
  );
}

function FeatureStatuses({ summary }: { summary: LivingSyncSummary }) {
  if (summary.features.length === 0) return <Section title="Feature Status" empty="Keine Feature-Meldungen." />;

  return (
    <Section title="Feature Status">
      <div className="space-y-2">
        {summary.features.map((feature) => (
          <div key={feature.featureId} className="rounded-md border bg-card p-2">
            <div className="flex items-center justify-between gap-2">
              <span className="truncate text-sm font-medium">{feature.featureId}</span>
              <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-medium", STATUS_STYLE[feature.status])}>
                {feature.status}
              </span>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">{feature.summary}</p>
          </div>
        ))}
      </div>
    </Section>
  );
}

function ChangedFiles({ summary }: { summary: LivingSyncSummary }) {
  if (summary.changedFiles.length === 0 && summary.commits.length === 0) {
    return <Section title="Code Changes" empty="Keine Code-Aenderungen gemeldet." />;
  }

  return (
    <Section title="Code Changes">
      <div className="space-y-2 text-xs">
        {summary.changedFiles.slice(0, 8).map((file) => (
          <div key={file} className="truncate rounded-md bg-muted px-2 py-1 font-mono text-muted-foreground">{file}</div>
        ))}
        {summary.commits.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {summary.commits.map((commit) => <Badge key={commit} variant="ghost">{commit}</Badge>)}
          </div>
        )}
      </div>
    </Section>
  );
}

function SyncNotes({ summary }: { summary: LivingSyncSummary }) {
  if (summary.notes.length === 0) return <Section title="Notes" empty="Keine Blocker oder Abweichungen." />;

  return (
    <Section title="Notes">
      <div className="space-y-2">
        {summary.notes.slice(0, 5).map((note) => (
          <div key={note.id} className="rounded-md border bg-card p-2 text-xs text-muted-foreground">
            {note.summary}
          </div>
        ))}
      </div>
    </Section>
  );
}

function RecentEvents({ summary }: { summary: LivingSyncSummary }) {
  if (summary.recentEvents.length === 0) return <Section title="Recent Events" empty="Noch keine Events." />;

  return (
    <Section title="Recent Events">
      <div className="space-y-1.5">
        {summary.recentEvents.slice(0, 8).map((event) => (
          <div key={event.id} className="flex items-start gap-2 text-xs">
            <TestTube2 size={12} className="mt-0.5 text-muted-foreground" />
            <div className="min-w-0">
              <div className="font-medium">{event.type}</div>
              <div className="truncate text-muted-foreground">{event.summary}</div>
            </div>
          </div>
        ))}
      </div>
    </Section>
  );
}

function Section({ title, empty, children }: { title: string; empty?: string; children?: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-xs font-semibold uppercase tracking-normal text-muted-foreground">{title}</h3>
      {children ?? <p className="rounded-md border border-dashed p-3 text-xs text-muted-foreground">{empty}</p>}
    </section>
  );
}
