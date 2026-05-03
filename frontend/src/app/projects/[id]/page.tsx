"use client";

import { useEffect, useState, use } from "react";
import Link from "next/link";
import { ArrowLeft, ChevronRight, Loader2, Scale, MessageSquare, HelpCircle, Layers, Download, ShieldCheck, Bot, FolderTree, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ExportDialog } from "@/components/export/ExportDialog";
import { HandoffDialog } from "@/components/handoff/HandoffDialog";
import { ChatPanel } from "@/components/chat/ChatPanel";
import { DecisionLog } from "@/components/decisions/DecisionLog";
import { ClarificationList } from "@/components/clarifications/ClarificationList";
import { useProjectStore } from "@/lib/stores/project-store";
import { useDecisionStore } from "@/lib/stores/decision-store";
import { useClarificationStore } from "@/lib/stores/clarification-store";
import { useTaskStore } from "@/lib/stores/task-store";
import { TaskTree } from "@/components/tasks/TaskTree";
import { CheckResultsPanel } from "@/components/checks/CheckResultsPanel";
import { DocumentsPanel } from "@/components/documents/DocumentsPanel";
import { GraphMeshToggle } from "@/components/workspace/GraphMeshToggle";
import { ExplorerPanel } from "@/components/explorer/ExplorerPanel";
import { StepIndicator } from "@/components/wizard/StepIndicator";
import { WizardForm } from "@/components/wizard/WizardForm";
import { useWizardStore } from "@/lib/stores/wizard-store";
import { cn } from "@/lib/utils";
import { useResizable } from "@/lib/hooks/use-resizable";
import { ResizeHandle } from "@/components/layout/ResizeHandle";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function ProjectWorkspacePage({ params }: PageProps) {
  const { id } = use(params);
  const {
    project, projectLoading, projectError,
    flowState,
    loadProject, reset, setProject,
  } = useProjectStore();

  const [showExplorer, setShowExplorer] = useState(false);
  const [showExport, setShowExport] = useState(false);
  const [showHandoff, setShowHandoff] = useState(false);
  const [rightTab, setRightTab] = useState<"chat" | "decisions" | "clarifications" | "tasks" | "checks" | "documents">("chat");
  const { decisions, loadDecisions: loadDecs, reset: resetDecs } = useDecisionStore();
  const pendingCount = decisions.filter((d) => d.status === "PENDING").length;
  const { clarifications: clars, loadClarifications: loadClars, reset: resetClars } = useClarificationStore();
  const openClarCount = clars.filter((c) => c.status === "OPEN").length;
  const { tasks, loadTasks: loadTsks, loadCoverage, reset: resetTasks } = useTaskStore();

  useEffect(() => {
    reset();
    resetDecs();
    resetClars();
    resetTasks();
    useWizardStore.getState().reset();
    loadProject(id);
    loadDecs(id);
    loadClars(id);
    loadTsks(id);
    loadCoverage(id);
    useWizardStore.getState().loadWizard(id);
  }, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  const { width: sidebarWidth, isDragging, handleProps } = useResizable({
    initialWidth: 600,
    minWidth: 280,
    maxWidth: 900,
  });

  if (projectLoading) {
    return (
      <div className="flex h-full items-center justify-center bg-background">
        <Loader2 size={24} className="animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (projectError) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4 bg-background">
        <p className="text-sm text-destructive">{projectError}</p>
        <Link href="/projects" className="text-sm text-primary hover:underline">Back to Projects</Link>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col bg-background overflow-hidden">
      <header className="flex shrink-0 items-center gap-3 border-b border-border bg-card px-4 py-2.5">
        <Link href="/projects" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft size={14} />
          Projects
        </Link>
        <ChevronRight size={14} className="text-muted-foreground" />
        <span className="text-sm font-medium truncate max-w-xs">{project?.name ?? "..."}</span>
        <div className="ml-auto flex items-center gap-2">
          {project && <GraphMeshToggle project={project} onProjectUpdate={setProject} />}
          <Button variant="ghost" size="sm" onClick={() => setShowExplorer(!showExplorer)} className="gap-1.5" title="Toggle Explorer">
            <FolderTree size={14} />
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setShowHandoff(true)} className="gap-1.5">
            <Bot size={14} /> Handoff
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setShowExport(true)} className="gap-1.5">
            <Download size={14} /> Export
          </Button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Explorer Panel */}
        {showExplorer && (
          <div className="w-60 shrink-0 border-r border-border bg-card overflow-hidden animate-slide-in-left">
            <ExplorerPanel projectId={id} flowState={flowState} />
          </div>
        )}

        {/* Wizard area */}
        <div className="flex flex-1 flex-col overflow-hidden border-r">
          <StepIndicator />
          <div className="flex-1 overflow-hidden">
            <WizardForm
              projectId={id}
              onBlockerClick={(tab) => setRightTab(tab)}
              onExportClick={() => setShowExport(true)}
            />
          </div>
        </div>
        <div className="shrink-0 overflow-hidden flex flex-row" style={{ width: sidebarWidth }}>
          <ResizeHandle isDragging={isDragging} onMouseDown={handleProps.onMouseDown} />
          <div className="flex-1 overflow-hidden flex flex-col border-l border-border">
          {/* Tab buttons */}
          <div className="flex border-b border-border bg-card">
            <button
              onClick={() => setRightTab("chat")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "chat" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <MessageSquare size={13} /> Chat
            </button>
            <button
              onClick={() => setRightTab("decisions")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "decisions" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <Scale size={13} /> Decisions
              {pendingCount > 0 && (
                <span className="rounded-full bg-primary px-1.5 py-0.5 text-[10px] text-primary-foreground">{pendingCount}</span>
              )}
            </button>
            <button
              onClick={() => setRightTab("clarifications")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "clarifications" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <HelpCircle size={13} /> Clarifications
              {openClarCount > 0 && (
                <span className="rounded-full bg-amber-500/20 px-1.5 py-0.5 text-[10px] text-amber-400">{openClarCount}</span>
              )}
            </button>
            <button
              onClick={() => setRightTab("tasks")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "tasks" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <Layers size={13} /> Tasks
              {tasks.length > 0 && (
                <span className="rounded-full bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{tasks.length}</span>
              )}
            </button>
            <button
              onClick={() => setRightTab("checks")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "checks" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <ShieldCheck size={13} /> Checks
            </button>
            <button
              onClick={() => setRightTab("documents")}
              className={cn(
                "flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 text-xs font-medium transition-colors",
                rightTab === "documents" ? "border-b-2 border-primary text-primary" : "text-muted-foreground hover:text-foreground"
              )}
            >
              <FileText size={13} /> Documents
            </button>
          </div>
          {/* Tab content */}
          <div className="flex-1 overflow-hidden">
            {rightTab === "chat" ? (
              <ChatPanel projectId={id} />
            ) : rightTab === "decisions" ? (
              <DecisionLog projectId={id} />
            ) : rightTab === "clarifications" ? (
              <ClarificationList projectId={id} />
            ) : rightTab === "tasks" ? (
              <TaskTree projectId={id} />
            ) : rightTab === "checks" ? (
              <CheckResultsPanel projectId={id} />
            ) : (
              <DocumentsPanel projectId={id} />
            )}
          </div>
          </div>
        </div>
      </div>
      <ExportDialog
        projectId={id}
        projectName={project?.name ?? "Project"}
        open={showExport}
        onClose={() => setShowExport(false)}
      />
      <HandoffDialog
        projectId={id}
        projectName={project?.name ?? "Project"}
        open={showHandoff}
        onClose={() => setShowHandoff(false)}
      />
    </div>
  );
}
