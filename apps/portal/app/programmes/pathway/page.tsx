import Link from "next/link";
import { PATHWAY_MODULES } from "@/lib/mock";
import { Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** PRO-03 — Pathway builder. */
export default function PathwayBuilderPage() {
  return (
    <>
      <PageIntro
        eyebrow="Programme design"
        title="Build from approved CereBro modules."
        lede="A pathway sequences modules that already exist in the product. Organisations choose ordering and duration — not clinical content, which is governed centrally."
      />

      <SampleData />

      <Notice tone="warn" icon="!">
        Modules cannot be authored here. Anything that would change what a member is told is
        reviewed in the evidence library first, so a programme cannot quietly become advice.
      </Notice>

      <div className="card">
        <h2>Sequence</h2>
        <div className="steps" style={{ marginTop: 12 }}>
          {PATHWAY_MODULES.map((m, i) => (
            <div className="step" key={m.name}>
              <div className="step-num">{i + 1}</div>
              <div className="grow">
                <b>{m.name}</b>
                <div className="tiny">{m.detail}</div>
              </div>
              <span className="badge">{m.weeks}</span>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <div className="grid cols-2">
        <div className="card">
          <h2>Duration</h2>
          <div className="form-grid" style={{ marginTop: 14 }}>
            <label>
              <span className="label">Programme length</span>
              <select className="select" defaultValue="12 weeks">
                <option>8 weeks</option>
                <option>12 weeks</option>
                <option>16 weeks</option>
              </select>
            </label>
            <label>
              <span className="label">Weekly commitment</span>
              <select className="select" defaultValue="Under 15 minutes">
                <option>Under 10 minutes</option>
                <option>Under 15 minutes</option>
                <option>Under 30 minutes</option>
              </select>
            </label>
          </div>
        </div>

        <div className="card tint">
          <h2>What a pathway cannot do</h2>
          <ul className="tiny" style={{ marginTop: 10, paddingLeft: 18 }}>
            <li>Require completion, or report who fell behind</li>
            <li>Send escalating reminders</li>
            <li>Expose module-level activity for any individual</li>
            <li>Introduce assessment, scoring or diagnosis</li>
          </ul>
        </div>
      </div>

      <div className="toolbar">
        <Link className="btn secondary" href="/programmes">Back to library</Link>
      </div>
    </>
  );
}
