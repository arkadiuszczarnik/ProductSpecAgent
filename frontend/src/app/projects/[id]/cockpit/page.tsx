"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { CockpitLockedNotice } from "@/components/cockpit/CockpitLockedNotice";
import { ProjectCockpitPrototype } from "@/components/cockpit/ProjectCockpitPrototype";
import { getProject, type FlowState } from "@/lib/api";
import { isCockpitReady } from "@/lib/project-cockpit";

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
  const [projectLoading, setProjectLoading] = useState(true);
  const [projectError, setProjectError] = useState<string | null>(null);
  const [flowState, setFlowState] = useState<FlowState | null>(null);

  useEffect(() => {
    let cancelled = false;
    void getProject(id)
      .then((response) => {
        if (cancelled) return;
        setFlowState(response.flowState);
        setProjectLoading(false);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setProjectError(error instanceof Error ? error.message : "Failed to load");
        if (!cancelled) {
          setProjectLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (projectLoading) {
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
