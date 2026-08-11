import Link from "next/link";
import { PROGRAMMES, PROGRAMME_FILTERS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Progress } from "@/components/ui";

/** PRO-01 — Programme library. */
export default function ProgrammesPage() {
  return (
    <>
      <PageIntro
        eyebrow="Programme curation"
        title="Choose what members receive."
        lede="Sponsor evidence-informed pathways while preserving voluntary participation and personal control."
      />

      <div className="toolbar">
        {PROGRAMME_FILTERS.map((f) => (
          <span key={f} className={f === "All" ? "filter active" : "filter"}>
            {f}
          </span>
        ))}
      </div>

      <div className="grid cols-3">
        {PROGRAMMES.map((p) => (
          <div key={p.name} className={p.tag === "Active" ? "card tint" : "card"}>
            <div className="between">
              <Badge tone={p.tag === "Active" ? "good" : ""}>{p.tag}</Badge>
              <span className="tiny">{p.type}</span>
            </div>
            <h2 style={{ marginTop: 12 }}>{p.name}</h2>
            <p className="tiny" style={{ margin: "8px 0 14px" }}>{p.desc}</p>
            {typeof p.progress === "number" ? (
              <>
                <Progress value={p.progress} label={`${p.name} is ${p.progress}% through its schedule`} />
                <p className="tiny" style={{ margin: "8px 0 14px" }}>{p.status}</p>
                <Link className="btn" href="/engagement">Manage programme</Link>
              </>
            ) : (
              <>
                <p className="tiny" style={{ marginBottom: 14 }}>{p.status}</p>
                <button className="btn secondary" type="button">Sponsor programme</button>
              </>
            )}
          </div>
        ))}
      </div>

      <div className="spacer" />

      <Notice icon="⛨">
        Sponsoring a programme makes it available. It does not enrol anyone.
        Members choose whether to start, and choosing not to start is never
        reported back to the organisation.
      </Notice>
    </>
  );
}
