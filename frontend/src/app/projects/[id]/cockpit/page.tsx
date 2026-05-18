"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { CockpitLockedNotice } from "@/components/cockpit/CockpitLockedNotice";
import { ProjectCockpitPrototype } from "@/components/cockpit/ProjectCockpitPrototype";
import { isCockpitReady } from "@/lib/project-cockpit";
import { useProjectStore } from "@/lib/stores/project-store";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function ProjectCockpitPage({ params }: PageProps) {
  const { id } = use(params);

  return <ProjectCockpitPageContent key={id} id={id} />;
}

interface ProjectCockpitPageContentProps {
  id: string;
}

function ProjectCockpitPageContent({ id }: ProjectCockpitPageContentProps) {
  const { projectLoading, projectError, flowState, loadProject, reset } = useProjectStore();
  const [initialLoadDone, setInitialLoadDone] = useState(false);

  useEffect(() => {
    let cancelled = false;
    reset();
    void loadProject(id).finally(() => {
      if (!cancelled) {
        setInitialLoadDone(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [id, loadProject, reset]);

  if (!initialLoadDone || projectLoading) {
    return (
      <div className="flex h-full items-center justify-center bg-background">
        <Loader2 size={24} className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (projectError) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4 bg-background px-6 text-center">
        <p className="text-sm text-destructive">{projectError}</p>
        <Link href="/projects" className="text-sm text-primary hover:underline">
          Zurueck zu Projekten
        </Link>
      </div>
    );
  }

  if (!isCockpitReady(flowState)) {
    return <CockpitLockedNotice projectId={id} />;
  }

  return <ProjectCockpitPrototype projectId={id} />;
}
