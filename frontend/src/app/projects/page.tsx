"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus, FolderKanban, Calendar, Loader2, ArrowRight, Trash2 } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { listProjects, getProject, type Project, type FlowState } from "@/lib/api";
import { cockpitHref } from "@/lib/project-cockpit";
import { cn } from "@/lib/utils";
import { DeleteProjectDialog } from "@/components/projects/DeleteProjectDialog";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("de-DE", { day: "2-digit", month: "short", year: "numeric" });
}

interface ProjectWithStats extends Project {
  flowState?: FlowState;
  completedSteps: number;
  totalSteps: number;
}

export default function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectWithStats[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [projectToDelete, setProjectToDelete] = useState<ProjectWithStats | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const list = await listProjects();
        const withStats: ProjectWithStats[] = await Promise.all(
          list.map(async (p) => {
            try {
              const resp = await getProject(p.id);
              const completed = resp.flowState.steps.filter((s) => s.status === "COMPLETED").length;
              return { ...p, flowState: resp.flowState, completedSteps: completed, totalSteps: resp.flowState.steps.length };
            } catch {
              return { ...p, completedSteps: 0, totalSteps: 6 };
            }
          })
        );
        setProjects(withStats);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 size={24} className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="relative isolate min-h-full overflow-hidden">
      <DashboardAmbience />

      <div className="relative z-10 mx-auto max-w-6xl px-6 py-8">
      <div className="mb-6 flex items-start justify-between gap-4 dashboard-card-in">
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-xs font-medium text-primary">
            <FolderKanban size={13} />
            Spec Agent Workspace
          </div>
          <h1 className="text-2xl font-bold tracking-tight">Projekte</h1>
          <p className="text-sm text-muted-foreground">
            {projects.length} {projects.length === 1 ? "Projekt" : "Projekte"} - von der Idee zur umsetzbaren Spezifikation.
          </p>
        </div>
        <Link href="/projects/new" className={cn(buttonVariants(), "gap-2")}>
          <Plus size={16} /> Neues Projekt
        </Link>
      </div>

      <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-3 dashboard-card-in" style={{ animationDelay: "70ms" }}>
        <div className="rounded-lg border border-border bg-card/45 px-4 py-3 shadow-sm backdrop-blur-md">
          <p className="text-[11px] text-muted-foreground">Aktive Projekte</p>
          <p className="mt-1 text-xl font-semibold">{projects.length}</p>
        </div>
        <div className="rounded-lg border border-border bg-card/45 px-4 py-3 shadow-sm backdrop-blur-md">
          <p className="text-[11px] text-muted-foreground">Durchschnittlicher Fortschritt</p>
          <p className="mt-1 text-xl font-semibold">
            {projects.length === 0
              ? "0%"
              : `${Math.round(projects.reduce((sum, p) => sum + (p.totalSteps > 0 ? (p.completedSteps / p.totalSteps) * 100 : 0), 0) / projects.length)}%`}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card/45 px-4 py-3 shadow-sm backdrop-blur-md">
          <p className="text-[11px] text-muted-foreground">Naechster sinnvoller Schritt</p>
          <p className="mt-1 truncate text-sm font-medium">{projects.length === 0 ? "Projekt anlegen" : "Offene Spezifikationen fortsetzen"}</p>
        </div>
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">{error}</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {projects.length === 0 && !error ? (
          <div className="col-span-full flex flex-col items-center justify-center gap-4 rounded-xl border border-dashed bg-card/35 py-20 text-center backdrop-blur-md dashboard-card-in">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary">
              <FolderKanban size={28} />
            </div>
            <div>
              <p className="font-medium">Noch keine Projekte</p>
              <p className="mt-1 text-sm text-muted-foreground">Lege dein erstes Projekt an und starte den Spec-Wizard.</p>
            </div>
            <Link href="/projects/new" className={cn(buttonVariants(), "gap-2")}>
              <Plus size={16} /> Neues Projekt
            </Link>
          </div>
        ) : (
          projects.map((p, idx) => {
            const progress = p.totalSteps > 0 ? Math.round((p.completedSteps / p.totalSteps) * 100) : 0;
            const nextStep = p.flowState?.currentStep?.replace("_", " ") ?? "—";
            const href = cockpitHref(p.id, p.flowState);

            return (
              <div key={p.id} className="relative group">
                <Link href={href} className="block">
                  <Card
                    className="flex h-full flex-col bg-card/45 backdrop-blur-md hover:-translate-y-0.5 hover:border-white/20 hover:shadow-md dashboard-card-in"
                    style={{ animationDelay: `${140 + idx * 55}ms` }}
                  >
                    <CardHeader className="pb-2">
                      <div className="flex items-start justify-between">
                        <div className="flex items-start gap-3">
                          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                            <FolderKanban size={18} />
                          </div>
                          <div className="min-w-0">
                            <CardTitle className="truncate text-base">{p.name}</CardTitle>
                            <CardDescription className="mt-0.5 flex items-center gap-1.5 text-xs">
                              <Calendar size={11} /> {formatDate(p.createdAt)}
                            </CardDescription>
                          </div>
                        </div>
                        <ArrowRight size={14} className="text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity mt-1" />
                      </div>
                    </CardHeader>

                    <CardContent className="flex-1 space-y-3 pb-3">
                      <div>
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="text-[10px] text-muted-foreground">Spec Progress</span>
                          <span className="text-[10px] font-medium">{progress}%</span>
                        </div>
                        <Progress value={progress} />
                      </div>

                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-muted-foreground">Naechster Schritt:</span>
                        <Badge variant="default" className="capitalize text-[9px]">{nextStep}</Badge>
                      </div>
                    </CardContent>

                    <CardFooter className="border-t pt-2.5 pb-2.5">
                      <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                        <span>{p.completedSteps}/{p.totalSteps} Schritte abgeschlossen</span>
                      </div>
                    </CardFooter>
                  </Card>
                </Link>
                <button
                  type="button"
                  onClick={() => setProjectToDelete(p)}
                  aria-label={`Projekt "${p.name}" löschen`}
                  className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity rounded-md p-1.5 bg-background/80 backdrop-blur-sm text-muted-foreground hover:text-destructive hover:bg-destructive/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            );
          })
        )}
      </div>

      <DeleteProjectDialog
        project={projectToDelete ? { id: projectToDelete.id, name: projectToDelete.name } : null}
        onClose={() => setProjectToDelete(null)}
        onDeleted={(id) => setProjects((prev) => prev.filter((p) => p.id !== id))}
      />
      </div>
    </div>
  );
}

function DashboardAmbience() {
  return (
    <>
      <div className="dashboard-ambient" aria-hidden="true">
        <svg className="dashboard-ambient__svg" viewBox="0 0 189 163" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="dashboardDocStroke" x1="56" y1="3" x2="159" y2="145" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#6C7CFF" />
              <stop offset="0.42" stopColor="#5177FF" />
              <stop offset="0.74" stopColor="#128FE7" />
              <stop offset="1" stopColor="#10B9F0" />
            </linearGradient>
            <linearGradient id="dashboardSparkFill" x1="25" y1="63" x2="89" y2="132" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#8B6DFF" />
              <stop offset="0.5" stopColor="#7A62F2" />
              <stop offset="1" stopColor="#5E74FF" />
            </linearGradient>
            <linearGradient id="dashboardLineStroke" x1="105" y1="72" x2="162" y2="72" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#8A67FF" />
              <stop offset="1" stopColor="#4777FF" />
            </linearGradient>
          </defs>
          <path className="doc-path" d="M65 57V11C65 6.58 68.58 3 73 3H129L176 50V140C176 144.42 172.42 148 168 148H76C71.58 148 68 144.42 68 140V134" stroke="url(#dashboardDocStroke)" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" />
          <path className="doc-path" d="M130 4V42C130 46.42 133.58 50 138 50H176" stroke="url(#dashboardDocStroke)" strokeWidth="9" strokeLinecap="round" strokeLinejoin="round" />
          <g stroke="url(#dashboardLineStroke)" strokeWidth="9" strokeLinecap="round">
            <path className="spec-line spec-line--1" d="M108 75H156" />
            <path className="spec-line spec-line--2" d="M108 99H156" />
            <path className="spec-line spec-line--3" d="M108 124H156" />
          </g>
          <path className="big-spark" d="M48.5 54.5C55.1 76.9 66.6 88.2 88.5 94.5C66.6 100.8 55.1 112.1 48.5 134.5C41.9 112.1 30.4 100.8 8.5 94.5C30.4 88.2 41.9 76.9 48.5 54.5Z" fill="url(#dashboardSparkFill)" />
          <g fill="#8268F7">
            <path className="diamond d1" d="M4.75 79L9.5 83.75L4.75 88.5L0 83.75L4.75 79Z" />
            <path className="diamond d2" d="M30.4 58L35.2 62.8L30.4 67.6L25.6 62.8L30.4 58Z" />
            <path className="diamond d3" d="M4.75 111L9.5 115.75L4.75 120.5L0 115.75L4.75 111Z" />
            <path className="diamond d4" d="M27.4 126L31.8 130.4L27.4 134.8L23 130.4L27.4 126Z" />
          </g>
        </svg>
      </div>
      <div className="dashboard-particles" aria-hidden="true">
        {Array.from({ length: 12 }, (_, idx) => <span key={idx} />)}
      </div>
    </>
  );
}
