import Reveal from "@/components/Reveal";

/**
 * The coaching engine, drawn live: a deterministic graph where the model
 * writes words inside three nodes but never decides the routing.
 * Deterministic nodes are outlined in ink, model nodes in coral.
 */
export default function Engine() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-7xl px-6 py-24">
        <Reveal className="mx-auto max-w-3xl text-center">
          <h2 className="font-[family-name:var(--font-heading)] text-4xl font-medium leading-tight text-brand-900 sm:text-5xl">
            The Coaching Engine — a Real Turn, in Flight
          </h2>
          <p className="mt-5 leading-7 text-brand-800">
            Routing is code you can read, not a model you have to trust. The
            model supplies the words inside three of these boxes — it does not
            decide which box comes next, and it cannot talk its way past the
            commit gate.
          </p>
        </Reveal>

        <Reveal delay={120} className="mt-14">
          <div className="overflow-hidden rounded-[2rem] border border-mist-200 bg-mist-50 p-6 sm:p-8 [background-image:radial-gradient(circle_at_1px_1px,rgba(16,16,16,0.06)_1px,transparent_0)] [background-size:20px_20px]">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <span className="font-mono text-xs font-semibold uppercase tracking-widest text-brand-600">
                One governed arc · every session
              </span>
              <span className="inline-flex items-center gap-2 font-mono text-xs font-bold uppercase tracking-widest text-zen-600">
                <span className="h-2 w-2 animate-pulse rounded-full bg-zen-500" />
                deterministic
              </span>
            </div>

            <div className="overflow-x-auto">
              <svg
                viewBox="0 0 900 250"
                role="img"
                aria-label="The coaching engine: a session flows through safety, context, framing, method, practice and commit. Safety, context and commit are deterministic code with no model. Framing, method and practice call the model."
                className="min-w-[760px]"
              >
                <defs>
                  <marker
                    id="arrow"
                    markerWidth="7"
                    markerHeight="7"
                    refX="6"
                    refY="3.5"
                    orient="auto"
                  >
                    <path d="M0,0 L7,3.5 L0,7 z" fill="#8a8478" />
                  </marker>
                </defs>

                {/* edges */}
                <g fill="none" stroke="#c9beab" strokeWidth="1.6">
                  <path d="M118,72 L152,72" markerEnd="url(#arrow)" stroke="#f56b6b" />
                  <path d="M258,72 L292,72" markerEnd="url(#arrow)" stroke="#f56b6b" />
                  <path d="M398,72 L432,72" markerEnd="url(#arrow)" stroke="#f56b6b" />
                  <path d="M538,72 L572,72" markerEnd="url(#arrow)" stroke="#f56b6b" />
                  <path d="M678,72 L712,72" markerEnd="url(#arrow)" stroke="#f56b6b" />
                  <path
                    d="M58,96 L58,150 L152,150"
                    markerEnd="url(#arrow)"
                    strokeDasharray="4 3"
                  />
                  <path
                    d="M782,96 L782,190 L468,190 L468,96"
                    markerEnd="url(#arrow)"
                    strokeDasharray="4 3"
                  />
                </g>

                {/* deterministic nodes: ink outline */}
                {[
                  { x: 8, w: 110, label: "Safety", sub: "no model · ~1ms" },
                  { x: 152, w: 106, label: "Context", sub: "no model · ~7ms" },
                ].map((n) => (
                  <g key={n.label}>
                    <rect
                      x={n.x}
                      y="48"
                      width={n.w}
                      height="48"
                      rx="6"
                      fill="#ffffff"
                      stroke="#101010"
                      strokeWidth="1.4"
                    />
                    <text x={n.x + 12} y="70" fontSize="12" fontWeight="700" fill="#101010">
                      {n.label}
                    </text>
                    <text x={n.x + 12} y="85" fontSize="8.5" fill="#6e6c62" fontFamily="monospace">
                      {n.sub}
                    </text>
                  </g>
                ))}

                {/* model nodes: coral outline */}
                {[
                  { x: 292, label: "Framing" },
                  { x: 432, label: "Method" },
                  { x: 572, label: "Practice" },
                ].map((n) => (
                  <g key={n.label}>
                    <rect
                      x={n.x}
                      y="48"
                      width="106"
                      height="48"
                      rx="6"
                      fill="#ffffff"
                      stroke="#f56b6b"
                      strokeWidth="1.6"
                    />
                    <text x={n.x + 12} y="70" fontSize="12" fontWeight="700" fill="#101010">
                      {n.label}
                    </text>
                    <text x={n.x + 12} y="85" fontSize="8.5" fill="#f56b6b" fontFamily="monospace">
                      model
                    </text>
                  </g>
                ))}

                {/* commit gate */}
                <g>
                  <rect
                    x="712"
                    y="48"
                    width="140"
                    height="48"
                    rx="6"
                    fill="#101010"
                    stroke="#101010"
                  />
                  <text x="724" y="70" fontSize="12" fontWeight="700" fill="#ffffff">
                    Commit
                  </text>
                  <text x="724" y="85" fontSize="8.5" fill="#f58a8a" fontFamily="monospace">
                    no model · gate
                  </text>
                </g>

                {/* crisis exit */}
                <g>
                  <rect
                    x="152"
                    y="126"
                    width="150"
                    height="44"
                    rx="6"
                    fill="#ffffff"
                    stroke="#101010"
                    strokeWidth="1.4"
                  />
                  <text x="164" y="146" fontSize="11.5" fontWeight="700" fill="#101010">
                    Crisis support
                  </text>
                  <text x="164" y="160" fontSize="8.5" fill="#6e6c62" fontFamily="monospace">
                    no model · 0 tokens
                  </text>
                </g>

                {/* annotations */}
                <text x="8" y="38" fontSize="9" fontWeight="700" fill="#101010" fontFamily="monospace" letterSpacing="1">
                  DETERMINISTIC — CODE
                </text>
                <text x="292" y="38" fontSize="9" fontWeight="700" fill="#f56b6b" fontFamily="monospace" letterSpacing="1">
                  MODEL SUPPLIES THE WORDS
                </text>
                <text x="712" y="38" fontSize="9" fontWeight="700" fill="#101010" fontFamily="monospace" letterSpacing="1">
                  GATE — CODE
                </text>
                <text x="300" y="186" fontSize="9.5" fill="#6e6c62" fontFamily="monospace">
                  no commitment saved → the session cannot close
                </text>

                {/* the packet: a real turn moving through the graph */}
                <circle r="4" fill="#f56b6b">
                  <animateMotion
                    dur="6s"
                    repeatCount="indefinite"
                    path="M63,72 L152,72 L205,72 L292,72 L345,72 L432,72 L485,72 L572,72 L625,72 L712,72 L782,72"
                  />
                  <animate
                    attributeName="opacity"
                    dur="6s"
                    repeatCount="indefinite"
                    values="0;1;1;1;1;0"
                    keyTimes="0;0.03;0.5;0.9;0.97;1"
                  />
                </circle>
              </svg>
            </div>

            <p className="mt-5 text-sm leading-6 text-brand-800">
              Every edge is a code predicate over typed state. Which method a
              person gets, what must be true before an agent may hand off, when
              a session is allowed to close — all of it is code.{" "}
              <strong>An auditor can read this.</strong>
            </p>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
