"use client";

import { useEffect, useRef, useCallback } from "react";
import { useRete } from "rete-react-plugin";
import { createEditor, type EditorContext } from "./editor";
import { FlowNodeComponent } from "./FlowNode";
import type { FlowState, StepType } from "@/lib/api";

interface SpecFlowGraphProps {
  flowState: FlowState | null;
  onSelectStep?: (stepType: StepType) => void;
}

export function SpecFlowGraph({ flowState, onSelectStep }: SpecFlowGraphProps) {
  const ctxRef = useRef<EditorContext | null>(null);
  const onSelectStepRef = useRef(onSelectStep);

  useEffect(() => {
    onSelectStepRef.current = onSelectStep;
  }, [onSelectStep]);

  // Stable factory function — never changes, so useRete won't recreate the editor
  const editorFactory = useCallback(
    (container: HTMLElement) => createEditor(container, FlowNodeComponent),
    []
  );

  const [ref, editor] = useRete(editorFactory);

  // Wire up click callback once when editor is ready
  useEffect(() => {
    if (!editor) return;
    const ctx = editor as unknown as EditorContext;
    ctxRef.current = ctx;
    ctx.onNodeClick((stepType: StepType) => {
      onSelectStepRef.current?.(stepType);
    });
  }, [editor]);

  // Sync node statuses when flowState changes — use ref to avoid re-renders
  const prevFlowRef = useRef<string>("");
  useEffect(() => {
    if (!flowState || !ctxRef.current) return;
    // Only update if flowState actually changed (compare serialized steps)
    const key = flowState.steps.map((s) => `${s.stepType}:${s.status}`).join(",");
    if (key === prevFlowRef.current) return;
    prevFlowRef.current = key;

    const ctx = ctxRef.current;
    for (const step of flowState.steps) {
      ctx.updateNodeStatus(step.stepType, step.status);
    }
  }, [flowState]);

  return <div ref={ref} className="h-full w-full" style={{ background: "var(--color-background)" }} />;
}
