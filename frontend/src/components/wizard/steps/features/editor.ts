import { NodeEditor, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets } from "rete-react-plugin";
import { AutoArrangePlugin, Presets as ArrangePresets } from "rete-auto-arrange-plugin";
import { createRoot } from "react-dom/client";
import { FeatureRNode, FeatureNodeComponent } from "./FeatureNode";
import type { WizardFeature, WizardFeatureEdge } from "@/lib/api";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AreaExtra = any;

export interface FeaturesEditorContext {
  destroy: () => void;
  applyGraph: (features: WizardFeature[], edges: WizardFeatureEdge[]) => Promise<void>;
  autoLayout: () => Promise<void>;
  onNodeSelect: (cb: (featureId: string | null) => void) => void;
  onConnectionCreate: (cb: (from: string, to: string) => boolean) => void;
  onNodeMove: (cb: (featureId: string, x: number, y: number) => void) => void;
}

export async function createFeaturesEditor(
  container: HTMLElement,
): Promise<FeaturesEditorContext> {
  // NOTE: the editor is typed as `any` because the ClassicPreset.Node subclass
  // (FeatureRNode) is not structurally assignable to the generic ClassicScheme
  // the React preset requires. This mirrors the existing `spec-flow/editor.ts`
  // pattern used in the project.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const editor = new NodeEditor<any>();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const area = new AreaPlugin<any, AreaExtra>(container);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const connection = new ConnectionPlugin<any, AreaExtra>();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const render = new ReactPlugin<any, AreaExtra>({ createRoot });
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const arrange = new AutoArrangePlugin<any>();

  render.addPreset(
    ReactPresets.classic.setup({
      customize: {
        node() {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          return FeatureNodeComponent as any;
        },
      },
    }),
  );
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  connection.addPreset((ConnectionPresets.classic.setup as any)());
  arrange.addPreset(ArrangePresets.classic.setup());

  editor.use(area);
  area.use(connection);
  area.use(render);
  area.use(arrange);

  const socket = new ClassicPreset.Socket("feature");
  const nodeById = new Map<string, FeatureRNode>();

  let selectCb: ((id: string | null) => void) | null = null;
  let createCb: ((from: string, to: string) => boolean) | null = null;
  let moveCb: ((id: string, x: number, y: number) => void) | null = null;

  // Cycle prevention: intercept connectioncreate before it lands in the editor state.
  // Returning undefined cancels the event (so the connection is not created).
  editor.addPipe((ctx) => {
    if (ctx.type === "connectioncreate") {
      const data = ctx.data as { source: string; target: string };
      const fromNode = editor.getNode(data.source) as FeatureRNode | undefined;
      const toNode = editor.getNode(data.target) as FeatureRNode | undefined;
      if (fromNode && toNode && createCb) {
        const ok = createCb(fromNode.featureId, toNode.featureId);
        if (!ok) return; // undefined cancels the event
      }
    }
    return ctx;
  });

  area.addPipe((ctx) => {
    if (ctx.type === "nodepicked") {
      const nodeId = (ctx.data as { id: string }).id;
      const node = editor.getNode(nodeId);
      if (node instanceof FeatureRNode) selectCb?.(node.featureId);
    }
    if (ctx.type === "nodetranslated") {
      const d = ctx.data as { id: string; position: { x: number; y: number } };
      const node = editor.getNode(d.id);
      if (node instanceof FeatureRNode) moveCb?.(node.featureId, d.position.x, d.position.y);
    }
    return ctx;
  });

  return {
    destroy: () => area.destroy(),

    applyGraph: async (features, edges) => {
      // Clear current state first so re-applying from the store leaves a clean slate.
      for (const c of editor.getConnections()) await editor.removeConnection(c.id);
      for (const n of editor.getNodes()) await editor.removeNode(n.id);
      nodeById.clear();

      for (const f of features) {
        const node = new FeatureRNode(f.id, f.title, f.scopes);
        node.addInput("in", new ClassicPreset.Input(socket, "depends on"));
        node.addOutput("out", new ClassicPreset.Output(socket, "required by"));
        await editor.addNode(node);
        await area.translate(node.id, { x: f.position.x, y: f.position.y });
        nodeById.set(f.id, node);
      }
      for (const e of edges) {
        const from = nodeById.get(e.from);
        const to = nodeById.get(e.to);
        if (!from || !to) continue;
        await editor.addConnection(new ClassicPreset.Connection(from, "out", to, "in"));
      }
    },

    autoLayout: async () => {
      await arrange.layout();
      await AreaExtensions.zoomAt(area, editor.getNodes());
    },

    onNodeSelect: (cb) => {
      selectCb = cb;
    },
    onConnectionCreate: (cb) => {
      createCb = cb;
    },
    onNodeMove: (cb) => {
      moveCb = cb;
    },
  };
}
