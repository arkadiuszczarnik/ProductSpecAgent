import { NodeEditor, GetSchemes, ClassicPreset } from "rete";
import { AreaPlugin, AreaExtensions } from "rete-area-plugin";
import { ConnectionPlugin, Presets as ConnectionPresets } from "rete-connection-plugin";
import { ReactPlugin, Presets as ReactPresets } from "rete-react-plugin";
import { createRoot } from "react-dom/client";
import type { StepType, StepStatus } from "@/lib/api";

export class FlowNode extends ClassicPreset.Node {
  width = 180;
  height = 80;
  constructor(
    public stepType: StepType,
    label: string,
    public status: StepStatus = "OPEN"
  ) {
    super(label);
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Schemes = GetSchemes<FlowNode, ClassicPreset.Connection<FlowNode, FlowNode>>;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AreaExtra = any;

const STEPS: { type: StepType; label: string }[] = [
  { type: "IDEA", label: "Idee" },
  { type: "PROBLEM", label: "Problem" },
  { type: "SCOPE", label: "Scope" },
  { type: "MVP", label: "MVP" },
];

export interface EditorContext {
  destroy: () => void;
  updateNodeStatus: (stepType: StepType, status: StepStatus) => Promise<void>;
  onNodeClick: (cb: (stepType: StepType) => void) => void;
}

export async function createEditor(
  container: HTMLElement,
  customNodeComponent?: React.ComponentType<{ data: FlowNode }>
): Promise<EditorContext> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const editor = new NodeEditor<any>();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const area = new AreaPlugin<any, any>(container);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const connection = new ConnectionPlugin<any, any>();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const render = new ReactPlugin<any, any>({ createRoot });

  if (customNodeComponent) {
    render.addPreset(ReactPresets.classic.setup({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      customize: { node() { return customNodeComponent as any; } },
    }));
  } else {
    render.addPreset(ReactPresets.classic.setup());
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  connection.addPreset((ConnectionPresets.classic.setup as any)());
  editor.use(area);
  area.use(connection);
  area.use(render);

  const socket = new ClassicPreset.Socket("flow");
  const nodeMap = new Map<StepType, FlowNode>();

  for (let i = 0; i < STEPS.length; i++) {
    const { type, label } = STEPS[i];
    const node = new FlowNode(type, label);
    if (i < STEPS.length - 1) node.addOutput("out", new ClassicPreset.Output(socket));
    if (i > 0) node.addInput("in", new ClassicPreset.Input(socket));
    await editor.addNode(node);
    await area.translate(node.id, { x: i * 240, y: 0 });
    nodeMap.set(type, node);
  }

  for (let i = 0; i < STEPS.length - 1; i++) {
    const from = nodeMap.get(STEPS[i].type)!;
    const to = nodeMap.get(STEPS[i + 1].type)!;
    await editor.addConnection(new ClassicPreset.Connection(from, "out", to, "in"));
  }

  await AreaExtensions.zoomAt(area, editor.getNodes());

  let clickCallback: ((stepType: StepType) => void) | null = null;

  area.addPipe((ctx) => {
    if (ctx.type === "nodepicked" && clickCallback) {
      const nodeId = (ctx as any).data.id;
      const node = editor.getNode(nodeId);
      if (node instanceof FlowNode) clickCallback(node.stepType);
    }
    return ctx;
  });

  return {
    destroy: () => area.destroy(),
    updateNodeStatus: async (stepType, status) => {
      const node = nodeMap.get(stepType);
      if (node) { node.status = status; await area.update("node", node.id); }
    },
    onNodeClick: (cb) => { clickCallback = cb; },
  };
}
