"use client";

/* The governed coaching arc, drawn from the engine's real compiled graph
   (GET /v1/graph). READ-ONLY on purpose: the arc is compiled in build_graph.py and
   routing is deterministic code predicates over typed state — a canvas that let an
   operator rewire it would be a lie about how this product works. So: no palette, no
   edge inserter, no delete. Pan/zoom/inspect only.

   The graph is DENSE (~244 edges): LangGraph's conditional edges fan out to every
   possible target (profile_read dispatches to every stage; every stage chains to every
   next stage). Rendering all of them is the hairball the mermaid view was. So we draw
   the SPINE by default and reveal a node's true outgoing routes when it's selected. */

import { useEffect, useMemo, useRef, useState } from "react";
import {
  Background, Controls, Handle, Position, ReactFlow, ReactFlowProvider, useReactFlow,
  type Edge, type Node, type NodeProps,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { engineJson } from "@/lib/api";

type GEdge = { source: string; target: string; label: string; conditional: boolean };
type GraphResp = { nodes: { id: string }[]; edges: GEdge[]; stage_to_node: Record<string, string> };
export type AgentRow = { stage: string; model: string; enabled: boolean; size: number };

const EMOJI: Record<string, string> = {
  __start__: "▶", safety: "🛡", safe_response: "🆘", profile_read: "📥", action_checkin: "🔁",
  checkin: "🔄", intake: "📝", challenge: "🔀", core: "✨", capability: "🏗",
  dynamic_actions: "✅", simulation_decision: "🎭", role_play: "🎬", sjt: "🧩", pattern: "🔍",
  learning_aid: "📚", final_action_check: "🚦", feedback: "💬", session_complete: "🏁", __end__: "⏹",
};
const SUB: Record<string, string> = {
  __start__: "every turn starts here", safety: "crisis screen · ~1ms, no model",
  safe_response: "scripted helpline reply · zero tokens", profile_read: "loads context, sets entry stage",
  action_checkin: "standalone: one tapped action", checkin: "7-day repeat-user check-in",
  intake: "once: Coachable Index", challenge: "emits coaching_path (CIM/CBT/CH)",
  core: "the CIM + CBT slot", capability: "CH: Goals → Commitments → Development",
  dynamic_actions: "action cards + insights", simulation_decision: "offers rehearsal",
  role_play: "persona rehearsal", sjt: "situational judgement", pattern: "one pattern mirror",
  learning_aid: "one retrieved micro-learning", final_action_check: "the commit gate · blocks close",
  feedback: "mood + feedback · sole path to close", session_complete: "terminal", __end__: "turn ends",
};
/** Nodes that run in CODE — no model call. The rest map to a workbook agent. */
const CODE = new Set(["safety", "safe_response", "profile_read", "final_action_check", "session_complete"]);
const TERMINAL = new Set(["__start__", "__end__"]);
/** Off-spine nodes: they hang below the node they branch from, not on the spine. */
const PAIR: Record<string, string> = {
  safe_response: "safety", action_checkin: "profile_read", capability: "core", sjt: "role_play",
};
/* The arc runs LEFT → RIGHT (the axis the nodes' Left/Right handles already assume) and
   WRAPS into rows of PER_ROW, like text. 16 steps in one row is ~4000px — three times the
   canvas, so both ends were always off-screen. Wrapped, the whole governed arc is legible
   at once and spends both axes instead of leaving one of them empty. Branches drop under
   their step; each row is as tall as its deepest branch. */
const PER_ROW = 6;
const COL_W = 252;
const ROW_H = 128;
const ROW_GAP = 66;
const NODE_W = 218;

type NodeData = {
  title: string; emoji: string; sub: string;
  kind: "llm" | "code" | "terminal"; model?: string; enabled?: boolean; size?: number;
};

function ArcNode({ data, selected }: NodeProps) {
  const d = data as unknown as NodeData;
  const pip = d.kind === "llm" ? (d.enabled ? "ok" : "off") : d.kind === "code" ? "code" : null;
  return (
    <div className={`anode ${d.kind} ${selected ? "sel" : ""}`}>
      {!TERMINAL.has(d.title) && <Handle type="target" position={Position.Left} className="an-h" />}
      <div className="an-head">
        <span className="an-ic" aria-hidden="true">{d.emoji}</span>
        <span className="an-t">{d.title}</span>
        {pip && <span className={`an-pip ${pip}`} title={pip === "off" ? "disabled in the Catalog" : pip === "code" ? "runs in code" : "enabled"} />}
      </div>
      {d.sub && <div className="an-s">{d.sub}</div>}
      {d.kind !== "terminal" && (
        <div className="an-foot">
          {d.kind === "code"
            ? <span className="an-tag">code · no model</span>
            : <span className="an-count"><b>{d.model || "—"}</b> {d.size ? `· ${(d.size / 1000).toFixed(1)}k ch` : ""}</span>}
        </div>
      )}
      <Handle type="source" position={Position.Right} className="an-h" />
    </div>
  );
}
const nodeTypes = { arc: ArcNode };

type CanvasProps = {
  agents: AgentRow[];
  /** Stage selected elsewhere (the agents rail) — the canvas pans to it. */
  focusStage: string | null;
  onInspect: (stage: string | null) => void;
};

function Canvas({ agents, focusStage, onInspect }: CanvasProps) {
  const [graph, setGraph] = useState<GraphResp | null>(null);
  const [error, setError] = useState("");
  const [showAll, setShowAll] = useState(false);
  const [sel, setSel] = useState<string | null>(null);
  const rf = useReactFlow();
  /** The stage this canvas itself just selected — so a click doesn't re-pan the view
      out from under the cursor. Only selections from the rail move the viewport. */
  const fromCanvas = useRef<string | null>(null);

  useEffect(() => {
    engineJson<GraphResp>("/v1/graph").then(setGraph).catch((e) => setError(e.message));
  }, []);

  // Rail → canvas: select the node and bring it into view.
  useEffect(() => {
    if (fromCanvas.current === focusStage) { fromCanvas.current = null; return; }
    if (!graph || !focusStage) return;
    const id = graph.stage_to_node[focusStage];
    if (!id) return;
    setSel(id);
    const n = rf.getNode(id);
    if (n) rf.setCenter(n.position.x + NODE_W / 2, n.position.y + 36, { zoom: Math.max(rf.getZoom(), 0.9), duration: 400 });
  }, [focusStage, graph, rf]);

  const { nodes, edges, hiddenCount } = useMemo(() => {
    if (!graph) return { nodes: [] as Node[], edges: [] as Edge[], hiddenCount: 0 };
    // node id -> stage (reverse the engine's stage->node map) -> workbook agent
    const nodeToStage: Record<string, string> = {};
    for (const [stage, node] of Object.entries(graph.stage_to_node)) nodeToStage[node] = stage;
    const byStage = new Map(agents.map((a) => [a.stage, a]));

    // Layout. The engine returns nodes in arc order, so a node's index is its step along
    // the spine; a paired node shares its partner's step and drops one branch deep.
    const step: Record<string, number> = {};
    const branch: Record<string, number> = {};
    let n = 0;
    for (const { id } of graph.nodes) {
      const partner = PAIR[id];
      if (partner && step[partner] != null) { step[id] = step[partner]; branch[id] = (branch[partner] ?? 0) + 1; }
      else { step[id] = n++; branch[id] = 0; }
    }
    // Wrap the spine into rows, each sized to its own deepest branch (a row of plain
    // spine steps must not reserve empty space for a branch that only row 1 has).
    const rowOf = (id: string) => Math.floor(step[id] / PER_ROW);
    const depth: number[] = [];
    for (const { id } of graph.nodes) depth[rowOf(id)] = Math.max(depth[rowOf(id)] ?? 0, branch[id]);
    const rowY: number[] = [];
    let y = 0;
    for (let r = 0; r < depth.length; r++) { rowY[r] = y; y += (depth[r] + 1) * ROW_H + ROW_GAP; }

    const rfNodes: Node[] = graph.nodes.map(({ id }) => {
      const stage = nodeToStage[id];
      const agent = stage ? byStage.get(stage) : undefined;
      const kind: NodeData["kind"] = TERMINAL.has(id) ? "terminal" : CODE.has(id) ? "code" : "llm";
      return {
        id, type: "arc",
        position: { x: (step[id] % PER_ROW) * COL_W, y: rowY[rowOf(id)] + branch[id] * ROW_H },
        data: {
          title: id, emoji: EMOJI[id] || "●", sub: SUB[id] || "", kind,
          model: agent?.model, enabled: agent?.enabled, size: agent?.size,
        } as unknown as Record<string, unknown>,
        draggable: true, // reposition for readability; nothing is persisted
      };
    });

    // The spine: unconditional edges, labelled forks, and consecutive arc steps. Everything
    // else is the conditional fan-out — shown when its source is selected, or via "all routes".
    const isSpine = (e: GEdge) =>
      !e.conditional || !!e.label || (step[e.target] === step[e.source] + 1 && branch[e.target] === 0);
    const shown = graph.edges.filter((e) => showAll || isSpine(e) || e.source === sel);
    const rfEdges: Edge[] = shown.map((e, i) => {
      const hot = e.source === sel;
      const spine = isSpine(e);
      // The step that continues onto the next row: routed orthogonally so it runs back
      // through the gap between rows instead of slicing diagonally across the nodes.
      const wrap = spine && rowOf(e.target) > rowOf(e.source);
      return {
        id: `${e.source}->${e.target}:${i}`,
        source: e.source, target: e.target,
        label: e.label || undefined,
        animated: hot,
        type: wrap ? "smoothstep" : undefined,
        className: `aedge ${hot ? "hot" : spine ? "spine" : "faint"}${wrap ? " wrap" : ""}`,
      };
    });
    return { nodes: rfNodes, edges: rfEdges, hiddenCount: graph.edges.length - shown.length };
  }, [graph, agents, showAll, sel]);

  if (error) return <p className="error">{error}</p>;
  if (!graph) return <p className="hint">Loading graph…</p>;

  return (
    <>
      <div className="graphbar">
        <span className="hint">{graph.nodes.length} nodes · {graph.edges.length} routes · read-only (routing is code)</span>
        <label className="allroutes">
          <input type="checkbox" checked={showAll} onChange={(e) => setShowAll(e.target.checked)} />
          Show all routes
        </label>
        {!showAll && hiddenCount > 0 && <span className="hint">{hiddenCount} conditional routes hidden — click a node to see where it can go</span>}
      </div>
      <div className="canvas">
        <ReactFlow
          nodes={nodes} edges={edges} nodeTypes={nodeTypes}
          // Wrapped, the whole arc fits — so let fitView actually fit it, with a floor
          // that keeps nodes legible (below ~0.62 the model/size line stops reading).
          fitView fitViewOptions={{ padding: 0.08, minZoom: 0.62, maxZoom: 1 }}
          minZoom={0.2} maxZoom={1.8}
          nodesConnectable={false} edgesFocusable={false} deleteKeyCode={null}
          onNodeClick={(_, n) => {
            setSel(n.id);
            const stage = Object.entries(graph.stage_to_node).find(([, node]) => node === n.id)?.[0] ?? null;
            fromCanvas.current = stage;
            onInspect(stage);
          }}
          onPaneClick={() => { setSel(null); fromCanvas.current = null; onInspect(null); }}
          proOptions={{ hideAttribution: false }}
        >
          <Background gap={18} size={1} />
          <Controls showInteractive={false} />
          {/* No minimap: the arc is a single spine, so it added a floating panel over the
              canvas without telling you anything the fit/zoom controls don't. */}
        </ReactFlow>
      </div>
    </>
  );
}

/** React Flow's viewport hooks (useReactFlow) need a provider above them, and the
    rail-driven pan uses setCenter — so the provider lives here, one per canvas. */
export function AgentFlowCanvas(props: CanvasProps) {
  return (
    <ReactFlowProvider>
      <Canvas {...props} />
    </ReactFlowProvider>
  );
}
