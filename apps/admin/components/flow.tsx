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

import { useEffect, useMemo, useState } from "react";
import {
  Background, Controls, Handle, Position, ReactFlow,
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
/** Off-spine nodes: placed beside the node they branch from, not in the column. */
const PAIR: Record<string, string> = {
  safe_response: "safety", action_checkin: "profile_read", capability: "core", sjt: "role_play",
};
const COL_W = 268;
const ROW_H = 96;

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

export function AgentFlowCanvas({ agents, onInspect }: { agents: AgentRow[]; onInspect: (stage: string | null) => void }) {
  const [graph, setGraph] = useState<GraphResp | null>(null);
  const [error, setError] = useState("");
  const [showAll, setShowAll] = useState(false);
  const [sel, setSel] = useState<string | null>(null);

  useEffect(() => {
    engineJson<GraphResp>("/v1/graph").then(setGraph).catch((e) => setError(e.message));
  }, []);

  const { nodes, edges, hiddenCount } = useMemo(() => {
    if (!graph) return { nodes: [] as Node[], edges: [] as Edge[], hiddenCount: 0 };
    // node id -> stage (reverse the engine's stage->node map) -> workbook agent
    const nodeToStage: Record<string, string> = {};
    for (const [stage, node] of Object.entries(graph.stage_to_node)) nodeToStage[node] = stage;
    const byStage = new Map(agents.map((a) => [a.stage, a]));

    // Layout: the engine returns nodes in arc order; paired nodes sit beside their branch.
    const row: Record<string, number> = {};
    const col: Record<string, number> = {};
    let r = 0;
    for (const { id } of graph.nodes) {
      const partner = PAIR[id];
      if (partner && row[partner] != null) { row[id] = row[partner]; col[id] = (col[partner] ?? 0) + 1; }
      else { row[id] = r++; col[id] = 0; }
    }

    const rfNodes: Node[] = graph.nodes.map(({ id }) => {
      const stage = nodeToStage[id];
      const agent = stage ? byStage.get(stage) : undefined;
      const kind: NodeData["kind"] = TERMINAL.has(id) ? "terminal" : CODE.has(id) ? "code" : "llm";
      return {
        id, type: "arc",
        position: { x: col[id] * COL_W, y: row[id] * ROW_H },
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
      !e.conditional || !!e.label || (row[e.target] === row[e.source] + 1 && col[e.target] === 0);
    const shown = graph.edges.filter((e) => showAll || isSpine(e) || e.source === sel);
    const rfEdges: Edge[] = shown.map((e, i) => {
      const hot = e.source === sel;
      const spine = isSpine(e);
      return {
        id: `${e.source}->${e.target}:${i}`,
        source: e.source, target: e.target,
        label: e.label || undefined,
        animated: hot,
        className: `aedge ${hot ? "hot" : spine ? "spine" : "faint"}`,
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
          // The arc is tall; letting fitView shrink to fit made every node unreadable.
          // Clamp it to a legible zoom and let the operator pan (minimap + fit button).
          fitView fitViewOptions={{ padding: 0.1, minZoom: 0.72, maxZoom: 1 }}
          minZoom={0.2} maxZoom={1.8}
          nodesConnectable={false} edgesFocusable={false} deleteKeyCode={null}
          onNodeClick={(_, n) => {
            setSel(n.id);
            const stage = Object.entries(graph.stage_to_node).find(([, node]) => node === n.id)?.[0];
            onInspect(stage ?? null);
          }}
          onPaneClick={() => { setSel(null); onInspect(null); }}
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
