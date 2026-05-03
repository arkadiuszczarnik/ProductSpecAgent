import { NodeEditor, GetSchemes, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets } from "rete-react-plugin";
import { AutoArrangePlugin, Presets as ArrangePresets } from "rete-auto-arrange-plugin";
import { createRoot } from "react-dom/client";
import {
  FeatureRNode,
  FeatureNodeComponent,
  FeatureSocketComponent,
  FeatureConnectionComponent,
} from "./FeatureNode";
import type { WizardFeature, WizardFeatureEdge, FeatureScope } from "@/lib/api";

// NOTE on types: Rete's plugins expose `ClassicScheme` over `Classic.Node` + `Classic.Connection<Node, Node>`
// plus optional extras (`isLoop?` in rete-react-plugin, `ConnectionExtra` in rete-connection-plugin).
// We keep the Connection parameterised with the base `Classic.Node` so our Schemes stay assignable
// to both plugin variants, and cast to `FeatureRNode` where we need the wizard-specific fields.
type Schemes = GetSchemes<
  FeatureRNode,
  ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node> & {
    isLoop?: boolean;
    isPseudo?: boolean;
  }
>;

// React plugin's signal extras: listen to / emit classic React area signals.
type AreaExtra = import("rete-react-plugin").ReactArea2D<Schemes>;

export interface FeaturesEditorContext {
  destroy: () => void;
  applyGraph: (features: WizardFeature[], edges: WizardFeatureEdge[]) => Promise<void>;
  autoLayout: () => Promise<void>;
  onNodeDoubleClick: (cb: (featureId: string) => void) => void;
  onConnectionCreate: (cb: (from: string, to: string) => boolean) => void; // return false to reject
  onCycleRejected: (cb: () => void) => void;
  onNodeMove: (cb: (featureId: string, x: number, y: number) => void) => void;
  onConnectionRemove: (cb: (edgeId: string) => void) => void;
}

interface RenderedFeature {
  title: string;
  description: string;
  scopesKey: string; // sorted join of scopes for change detection
  x: number;
  y: number;
}

const DOUBLE_CLICK_MS = 350;

const scopesKey = (scopes: FeatureScope[]): string => [...scopes].sort().join(",");

export async function createFeaturesEditor(
  container: HTMLElement,
): Promise<FeaturesEditorContext> {
  const editor = new NodeEditor<Schemes>();
  const area = new AreaPlugin<Schemes, AreaExtra>(container);
  const connection = new ConnectionPlugin<Schemes, AreaExtra>();
  const render = new ReactPlugin<Schemes, AreaExtra>({ createRoot });
  const arrange = new AutoArrangePlugin<Schemes>();

  render.addPreset(
    ReactPresets.classic.setup({
      customize: {
        node() {
          return FeatureNodeComponent as unknown as React.ComponentType;
        },
        socket() {
          return FeatureSocketComponent as unknown as React.ComponentType;
        },
        connection() {
          return FeatureConnectionComponent as unknown as React.ComponentType;
        },
      },
    }),
  );
  connection.addPreset(ConnectionPresets.classic.setup());
  arrange.addPreset(ArrangePresets.classic.setup());

  editor.use(area);
  area.use(connection);
  area.use(render);
  area.use(arrange);

  const socket = new ClassicPreset.Socket("feature");

  // State held in the editor closure — never surfaced to React.
  const renderedFeatures = new Map<string, RenderedFeature>(); // featureId -> last known rendered shape
  const nodeIdByFeatureId = new Map<string, string>(); // wizard feature id -> rete node id
  const edgeIdByConnectionId = new Map<string, string>(); // rete conn id -> wizard edge id
  const connectionIdByEdgeId = new Map<string, string>(); // wizard edge id -> rete conn id

  let doubleClickCb: ((featureId: string) => void) | null = null;
  let createCb: ((from: string, to: string) => boolean) | null = null;
  let cycleCb: (() => void) | null = null;
  let moveCb: ((id: string, x: number, y: number) => void) | null = null;
  let removeCb: ((edgeId: string) => void) | null = null;
  let lastPickedNodeId: string | null = null;
  let lastPickedAt = 0;

  // ---- Event wiring ----
  // `suppressPipeEvents` guards against feedback loops while `applyGraphOnce` is
  // mirroring the store back into Rete. `editor.addConnection(...)` and
  // `editor.removeConnection(...)` both emit pipe events that would otherwise
  // re-enter `createCb`/`removeCb`, push a duplicate/phantom edge into the
  // store, re-run the useEffect, and loop forever (as seen in the Firefox
  // "Script terminated by timeout" stack).
  let suppressPipeEvents = false;

  // Cycle prevention — intercept connection creation via editor pipe.
  editor.addPipe((ctx) => {
    if (suppressPipeEvents) return ctx;
    if (ctx.type === "connectioncreate") {
      const data = ctx.data as { source: string; target: string };
      const fromNode = editor.getNode(data.source);
      const toNode = editor.getNode(data.target);
      if (fromNode instanceof FeatureRNode && toNode instanceof FeatureRNode && createCb) {
        const ok = createCb(fromNode.featureId, toNode.featureId);
        if (!ok) {
          cycleCb?.();
          return;  // undefined cancels the event
        }
      }
    }
    if (ctx.type === "connectionremoved") {
      const conn = ctx.data as { id: string };
      const edgeId = edgeIdByConnectionId.get(conn.id);
      if (edgeId) removeCb?.(edgeId);
    }
    return ctx;
  });

  // Area-level events: selection and user-drag-end ONLY.
  // IMPORTANT: we listen to "nodedragged" (user drag end), NOT "nodetranslated".
  // `nodetranslated` fires for every translate — including programmatic `area.translate(...)`
  // calls from inside applyGraph — and would create a feedback loop.
  area.addPipe((ctx) => {
    if (ctx.type === "nodepicked") {
      const id = (ctx.data as { id: string }).id;
      const node = editor.getNode(id);
      if (node instanceof FeatureRNode) {
        const now = Date.now();
        const isDouble =
          lastPickedNodeId === id && now - lastPickedAt < DOUBLE_CLICK_MS;
        if (isDouble) {
          doubleClickCb?.(node.featureId);
          // Reset so a third pick does not chain into another double-click event.
          lastPickedNodeId = null;
          lastPickedAt = 0;
        } else {
          lastPickedNodeId = id;
          lastPickedAt = now;
        }
      }
    }
    if (ctx.type === "nodedragged") {
      const id = (ctx.data as { id: string }).id;
      const node = editor.getNode(id);
      const view = area.nodeViews.get(id);
      if (node instanceof FeatureRNode && view && moveCb) {
        moveCb(node.featureId, view.position.x, view.position.y);
      }
    }
    return ctx;
  });

  // ---- applyGraph — incremental diff against the closure map ----
  async function applyGraphOnce(features: WizardFeature[], edges: WizardFeatureEdge[]) {
    suppressPipeEvents = true;
    try {
      const desiredFeatures = new Map<string, WizardFeature>(features.map((f) => [f.id, f]));
      const desiredEdges = new Map<string, WizardFeatureEdge>(edges.map((e) => [e.id, e]));

      // 1. Remove nodes that are no longer desired. Catch errors — a concurrent
      //    user-delete may have already removed them.
      for (const [featureId, reteNodeId] of Array.from(nodeIdByFeatureId.entries())) {
        if (!desiredFeatures.has(featureId)) {
          try {
            await editor.removeNode(reteNodeId);
          } catch {
            /* may already be gone */
          }
          nodeIdByFeatureId.delete(featureId);
          renderedFeatures.delete(featureId);
        }
      }

      // 2. Add new nodes; update changed ones in place.
      for (const f of features) {
        const existingReteId = nodeIdByFeatureId.get(f.id);
        if (!existingReteId) {
          // NEW node
          const node = new FeatureRNode(f.id, f.title, f.scopes, f.description);
          node.addInput("in", new ClassicPreset.Input(socket, "depends on"));
          node.addOutput("out", new ClassicPreset.Output(socket, "required by"));
          await editor.addNode(node);
          await area.translate(node.id, { x: f.position.x, y: f.position.y });
          nodeIdByFeatureId.set(f.id, node.id);
          renderedFeatures.set(f.id, {
            title: f.title,
            description: f.description,
            scopesKey: scopesKey(f.scopes),
            x: f.position.x,
            y: f.position.y,
          });
          continue;
        }
        // UPDATE in place.
        const rendered = renderedFeatures.get(f.id)!;
        const newScopesKey = scopesKey(f.scopes);
        const node = editor.getNode(existingReteId) as FeatureRNode | undefined;
        if (
          node &&
          (rendered.title !== f.title ||
            rendered.description !== f.description ||
            rendered.scopesKey !== newScopesKey)
        ) {
          node.label = f.title;
          node.scopes = f.scopes;
          node.description = f.description;
          await area.update("node", existingReteId); // React re-renders the custom node
        }
        if (rendered.x !== f.position.x || rendered.y !== f.position.y) {
          // programmatic translate — will NOT fire `nodedragged` (only `nodetranslated`, which we don't listen to)
          await area.translate(existingReteId, { x: f.position.x, y: f.position.y });
        }
        renderedFeatures.set(f.id, {
          title: f.title,
          description: f.description,
          scopesKey: newScopesKey,
          x: f.position.x,
          y: f.position.y,
        });
      }

      // 3. Remove stale connections.
      for (const [connId, edgeId] of Array.from(edgeIdByConnectionId.entries())) {
        if (!desiredEdges.has(edgeId)) {
          try {
            await editor.removeConnection(connId);
          } catch {
            /* may already be gone */
          }
          edgeIdByConnectionId.delete(connId);
          connectionIdByEdgeId.delete(edgeId);
        }
      }
      // 4. Add new connections.
      for (const e of edges) {
        if (connectionIdByEdgeId.has(e.id)) continue;
        const fromReteId = nodeIdByFeatureId.get(e.from);
        const toReteId = nodeIdByFeatureId.get(e.to);
        if (!fromReteId || !toReteId) continue;

        // If the user just drag-created this edge, Rete already has the
        // Connection — the store was populated by createCb before our maps
        // were updated. Reuse the existing Connection instead of adding a
        // duplicate.
        const existing = editor
          .getConnections()
          .find((c) => c.source === fromReteId && c.target === toReteId);
        if (existing) {
          edgeIdByConnectionId.set(existing.id, e.id);
          connectionIdByEdgeId.set(e.id, existing.id);
          continue;
        }

        const fromNode = editor.getNode(fromReteId) as FeatureRNode | undefined;
        const toNode = editor.getNode(toReteId) as FeatureRNode | undefined;
        if (!fromNode || !toNode) continue;
        const conn = new ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node>(
          fromNode,
          "out",
          toNode,
          "in",
        );
        await editor.addConnection(conn);
        edgeIdByConnectionId.set(conn.id, e.id);
        connectionIdByEdgeId.set(e.id, conn.id);
      }
    } finally {
      suppressPipeEvents = false;
    }
  }

  // Coalescing queue: at most one running + one pending call.
  // A second call while one is in flight overwrites the pending slot (newest wins).
  let applying = false;
  let pending: { features: WizardFeature[]; edges: WizardFeatureEdge[] } | null = null;
  let destroyed = false;

  async function applyGraph(features: WizardFeature[], edges: WizardFeatureEdge[]) {
    if (destroyed) return;
    if (applying) {
      pending = { features, edges };
      return;
    }
    applying = true;
    try {
      await applyGraphOnce(features, edges);
      while (pending && !destroyed) {
        const next = pending;
        pending = null;
        await applyGraphOnce(next.features, next.edges);
      }
    } catch (err) {
      // If the editor was torn down mid-apply, swallow Rete-internal errors.
      if (!destroyed) throw err;
    } finally {
      applying = false;
    }
  }

  return {
    destroy: () => {
      destroyed = true;
      doubleClickCb = null;
      createCb = null;
      cycleCb = null;
      moveCb = null;
      removeCb = null;
      renderedFeatures.clear();
      nodeIdByFeatureId.clear();
      edgeIdByConnectionId.clear();
      connectionIdByEdgeId.clear();
      pending = null;
      area.destroy();
    },
    applyGraph,
    autoLayout: async () => {
      await arrange.layout();
      await AreaExtensions.zoomAt(area, editor.getNodes());
    },
    onNodeDoubleClick: (cb) => {
      doubleClickCb = cb;
    },
    onConnectionCreate: (cb) => {
      createCb = cb;
    },
    onCycleRejected: (cb) => {
      cycleCb = cb;
    },
    onNodeMove: (cb) => {
      moveCb = cb;
    },
    onConnectionRemove: (cb) => {
      removeCb = cb;
    },
  };
}
