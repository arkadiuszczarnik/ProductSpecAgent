"use client";

import { useState, FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Sparkles, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { createProject } from "@/lib/api";
import { cn } from "@/lib/utils";

export default function NewProjectPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = name.trim().length > 0 && !loading;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    setLoading(true);
    setError(null);
    try {
      const resp = await createProject({ name: name.trim() });
      router.push(`/projects/${resp.project.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create project");
      setLoading(false);
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto max-w-2xl px-4 py-10">
        <Link
          href="/projects"
          className="mb-8 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft size={14} />
          All Projects
        </Link>

        <Card>
          <CardHeader>
            <div className="flex items-center gap-3 mb-1">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Sparkles size={18} />
              </div>
              <CardTitle className="text-xl">New Project</CardTitle>
            </div>
            <CardDescription>
              Gib deinem Projekt einen Namen. Die Idee beschreibst du anschließend im Wizard.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="space-y-1.5">
                <label htmlFor="project-name" className="block text-sm font-medium">
                  Project Name
                </label>
                <input
                  id="project-name"
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. TaskFlow Pro"
                  maxLength={120}
                  disabled={loading}
                  className={cn(
                    "w-full rounded-md border bg-input px-3 py-2 text-sm placeholder:text-muted-foreground",
                    "focus:outline-none focus:ring-2 focus:ring-ring",
                    "disabled:opacity-50"
                  )}
                />
              </div>

              {error && (
                <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-1">
                <Button type="button" variant="ghost" disabled={loading} onClick={() => router.push("/projects")}>
                  Cancel
                </Button>
                <Button type="submit" disabled={!canSubmit}>
                  {loading ? (
                    <><Loader2 size={14} className="animate-spin" /> Creating...</>
                  ) : (
                    <><Sparkles size={14} /> Create Project</>
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
