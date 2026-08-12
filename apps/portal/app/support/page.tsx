import { SUPPORT_CHANNELS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";
import { SampleData } from "@/components/data";

/** SUP-01 — Support & status. */
export default function SupportPage() {
  return (
    <>
      <PageIntro
        eyebrow="Support"
        title="Help, incidents and implementation support."
        lede="Administrative support for the organisation. Members get help inside the app, from people who are not your colleagues."
      />

      <SampleData />

      <div className="card">
        <div className="list">
          {SUPPORT_CHANNELS.map((c) => (
            <div className="list-item" key={c.name}>
              <div className="grow">
                <b>{c.name}</b>
                <div className="tiny">{c.detail}</div>
              </div>
              <Badge tone={c.tone}>{c.badge}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="danger" icon="!">
        <b>Support cannot look up a member.</b>
        <br />
        If an administrator is worried about a specific person, the route is the same one
        available to anyone: talk to them, and point them at human help. CereBro support has no
        view of an individual’s wellbeing data either.
      </Notice>
    </>
  );
}
