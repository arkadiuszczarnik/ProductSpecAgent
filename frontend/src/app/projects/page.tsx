"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus, FolderKanban, Calendar, Loader2, ArrowRight, Trash2 } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { listProjects, getProject, type Project, type FlowState } from "@/lib/api";
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
    <div className="mx-auto max-w-5xl px-6 py-8">
      <div className="mb-8 flex items-center justify-between animate-fade-in-up">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {projects.length} project{projects.length !== 1 ? "s" : ""} — turn ideas into specs.
          </p>
        </div>
        <Link href="/projects/new" className={cn(buttonVariants(), "gap-2")}>
          <Plus size={16} /> New Project
        </Link>
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-sm text-destructive">{error}</div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {projects.length === 0 && !error ? (
          <div className="col-span-full flex flex-col items-center justify-center gap-4 rounded-xl border border-dashed py-20 text-center animate-fade-in-up">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10 text-primary">
              <FolderKanban size={28} />
            </div>
            <div>
              <p className="font-medium">No projects yet</p>
              <p className="mt-1 text-sm text-muted-foreground">Create your first project.</p>
            </div>
            <Link href="/projects/new" className={cn(buttonVariants(), "gap-2")}>
              <Plus size={16} /> New Project
            </Link>
          </div>
        ) : (
          projects.map((p, idx) => {
            const progress = p.totalSteps > 0 ? Math.round((p.completedSteps / p.totalSteps) * 100) : 0;
            const nextStep = p.flowState?.currentStep?.replace("_", " ") ?? "—";

            return (
              <div key={p.id} className="relative group">
                <Link href={`/projects/${p.id}`} className="block">
                  <Card
                    className="flex flex-col h-full hover:-translate-y-0.5 hover:shadow-md animate-fade-in-up"
                    style={{ animationDelay: `${idx * 50}ms` }}
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
                        <span className="text-[10px] text-muted-foreground">Next:</span>
                        <Badge variant="default" className="capitalize text-[9px]">{nextStep}</Badge>
                      </div>
                    </CardContent>

                    <CardFooter className="border-t pt-2.5 pb-2.5">
                      <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                        <span>{p.completedSteps}/{p.totalSteps} steps</span>
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
  );
}
