import { NOTIFICATIONS } from "@/lib/mock";
import { Badge, Notice, PageIntro, Spacer } from "@/components/ui";

/** NOT-01 — Notifications. */
export default function NotificationsPage() {
  return (
    <>
      <PageIntro
        eyebrow="Portal alerts"
        title="Notifications."
        lede="Governance, safety, privacy and integration alerts only. The portal never generates notifications about individual member distress or wellness activity."
      />

      <div className="card">
        <div className="list">
          {NOTIFICATIONS.map((n) => (
            <div className="list-item" key={n.id}>
              <div
                aria-hidden="true"
                className={
                  n.type === "Safety" ? "item-ic" : n.type === "Integration" ? "item-ic blue" : "item-ic sage"
                }
              >
                {n.read ? "✓" : "•"}
              </div>
              <div className="grow">
                <b>{n.title}</b>
                <div className="tiny">
                  {n.type} · {n.read ? "Read" : "Unread"}
                </div>
              </div>
              <Badge tone={n.read ? "" : "info"}>{n.read ? "Read" : "New"}</Badge>
            </div>
          ))}
        </div>
      </div>

      <Spacer />

      <Notice tone="info" icon="i">
        There is deliberately no alert type for a member in distress. That signal stays inside
        the member’s own session, where it reaches helplines and their safety plan — never an
        administrator.
      </Notice>
    </>
  );
}
