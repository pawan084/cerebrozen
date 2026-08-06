"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { NAV, PAGE_META } from "@/lib/nav";
import { ADMIN, NOTIFICATIONS, ORGS } from "@/lib/mock";
import { PRIVACY_WALL_SIDEBAR } from "@/lib/copy";

/**
 * The portal chrome: a 284px sidebar and a sticky topbar, wrapped around every
 * route. Client-side only for the active-route highlight and the two topbar
 * menus — there is no data here, and no request leaves the page.
 */
export default function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname() ?? "/";
  const [org, setOrg] = useState<string>(ORGS[0]);
  const [orgOpen, setOrgOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const meta = PAGE_META[pathname] ?? { title: "Organisation portal", sub: org };
  const unread = NOTIFICATIONS.filter((n) => !n.read).length;

  return (
    <div className={menuOpen ? "app menu" : "app"}>
      {/* Below 820px the sidebar slides in over the page. The scrim closes it;
          it is a real button so it is reachable by keyboard, not a bare div. */}
      {menuOpen ? (
        <button
          type="button"
          className="backdrop"
          aria-label="Close navigation"
          onClick={() => setMenuOpen(false)}
        />
      ) : null}

      <aside className="sidebar" aria-label="Organisation portal navigation">
        <Link href="/" className="brand" onClick={() => setMenuOpen(false)}>
          <div className="orb" aria-hidden="true" />
          <div>
            <strong>CereBro</strong>
            <small>for Organisations</small>
          </div>
        </Link>

        <div className="review-pill">Design review · illustrative aggregate data</div>

        <nav aria-label="Portal sections">
          {NAV.map((group) => (
            <div key={group.title}>
              <div className="nav-section">{group.title}</div>
              {group.items.map((item) =>
                item.href ? (
                  <Link
                    key={item.code}
                    href={item.href}
                    className={
                      isActive(pathname, item.href) ? "nav-btn active" : "nav-btn"
                    }
                    aria-current={isActive(pathname, item.href) ? "page" : undefined}
                    onClick={() => setMenuOpen(false)}
                  >
                    <span className="nav-ic" aria-hidden="true">{item.icon}</span>
                    {item.label}
                  </Link>
                ) : (
                  // Listed but not built. Shown rather than hidden so the full
                  // 36-route shape stays reviewable; disabled so nobody lands
                  // on an empty page believing it exists.
                  <span
                    key={item.code}
                    className="nav-btn"
                    aria-disabled="true"
                    style={{ opacity: 0.5 }}
                    title="Not part of this design review"
                  >
                    <span className="nav-ic" aria-hidden="true">{item.icon}</span>
                    {item.label}
                  </span>
                ),
              )}
            </div>
          ))}
        </nav>

        {/* Permanent. Not dismissible, not collapsible, present on every route. */}
        <div className="privacy-note">
          <b>Privacy wall</b>
          {PRIVACY_WALL_SIDEBAR}
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            type="button"
            className="icon-btn mobile-menu"
            aria-expanded={menuOpen}
            aria-label="Open navigation"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            ☰
          </button>

          <div className="top-title">
            <b>{meta.title}</b>
            <span>{meta.sub}</span>
          </div>

          <label className="top-search">
            <span aria-hidden="true">⌕</span>
            <span className="sr-only">Search the portal</span>
            <input placeholder="Search members, programmes, reports…" />
          </label>

          <div className="top-actions">
            <div style={{ position: "relative" }}>
              <button
                type="button"
                className="btn secondary small org-switch"
                aria-expanded={orgOpen}
                aria-haspopup="menu"
                onClick={() => { setOrgOpen(!orgOpen); setNotifOpen(false); }}
              >
                {org} ▾
              </button>
              {orgOpen ? (
                <div className="card" style={menuStyle} role="menu">
                  {ORGS.map((o) => (
                    <button
                      key={o}
                      type="button"
                      role="menuitem"
                      className={o === org ? "filter active" : "filter"}
                      style={{ width: "100%", justifyContent: "flex-start", marginBottom: 6 }}
                      onClick={() => { setOrg(o); setOrgOpen(false); }}
                    >
                      {o}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            <div style={{ position: "relative" }}>
              <button
                type="button"
                className={unread ? "icon-btn notification-dot unread" : "icon-btn notification-dot"}
                aria-expanded={notifOpen}
                aria-label={`Notifications, ${unread} unread`}
                onClick={() => { setNotifOpen(!notifOpen); setOrgOpen(false); }}
              >
                ♧
              </button>
              {notifOpen ? (
                <div className="card" style={{ ...menuStyle, width: 300 }}>
                  <div className="list">
                    {NOTIFICATIONS.map((n) => (
                      <div key={n.id} className="list-item">
                        <div className="grow">
                          <b style={{ fontSize: 12.5 }}>{n.title}</b>
                          <div className="tiny">{n.type}</div>
                        </div>
                        {n.read ? null : <span className="badge warn">New</span>}
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>

            <span className="avatar" aria-hidden="true">{ADMIN.initials}</span>
            <span className="sr-only">
              Signed in as {ADMIN.name}, {ADMIN.role}
            </span>
          </div>
        </header>

        <div className="content">{children}</div>
      </div>
    </div>
  );
}

/** "/" only matches itself; deeper routes match their section prefix. */
function isActive(pathname: string, href: string) {
  return href === "/" ? pathname === "/" : pathname.startsWith(href);
}

const menuStyle: React.CSSProperties = {
  position: "absolute",
  top: "calc(100% + 8px)",
  right: 0,
  zIndex: 30,
  width: 260,
  padding: 12,
};
