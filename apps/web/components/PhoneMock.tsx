// Device mocks drawn from the design tokens rather than photographed.
//
// The baked renders in public/screens and public/brand are still the indigo
// build: they carry the old tab set (Home · Sleep · …) and one of them shows a
// "3-day streak · beautifully done" milestone — an affordance this page and the
// product spec both rule out. Regenerating them is REDESIGN_V2.md §5 phase 3;
// until then a marked-up mock is the honest illustration, and it inherits the
// Light Dawn palette for free.
//
// Every mock is aria-hidden with a caption supplied by the caller: it is
// decoration, and a screen reader stepping through fake UI text would learn
// nothing true.

const TABS = ["Today", "Explore", "Talk", "Journal", "You"] as const;

function TabBar({ active }: { active: (typeof TABS)[number] }) {
  return (
    <div className="pm-tabbar">
      {TABS.map((t) => (
        <span className={`pm-tab${t === active ? " on" : ""}`} key={t}>
          <i />
          {t}
        </span>
      ))}
    </div>
  );
}

export type MockKind = "today" | "sleep" | "journal";

export function PhoneMock({ kind }: { kind: MockKind }) {
  if (kind === "sleep") {
    return (
      <div className="pm pm-dark" aria-hidden="true">
        <div className="pm-screen">
          <div className="pm-kicker">Tonight</div>
          <div className="pm-h">Wind down</div>
          <div className="pm-card">
            <b>Rain over quiet hills</b>
            <small>Sleep story · 18 min · fades out on its own</small>
            <span className="pm-btn">Play</span>
          </div>
          <div className="pm-card">
            <b>Your mix</b>
            <div className="pm-meter">
              {[38, 62, 30, 74, 46, 58, 26, 68].map((h, i) => (
                <span key={i} style={{ height: `${h}%` }} />
              ))}
            </div>
            <small>Rain · Ocean · Wind, each at its own level</small>
          </div>
          <div className="pm-row">
            <i />
            Sleep timer · 30 min
          </div>
          <TabBar active="Explore" />
        </div>
      </div>
    );
  }

  if (kind === "journal") {
    return (
      <div className="pm" aria-hidden="true">
        <div className="pm-screen">
          <div className="pm-kicker">Journal</div>
          <div className="pm-h">Today&apos;s prompt</div>
          <div className="pm-card warm">
            <b>Where did you feel most like yourself?</b>
            <small>Two lines is a whole entry.</small>
            <span className="pm-btn">Write</span>
          </div>
          <div className="pm-row">
            <i />
            History · past entries and tags
          </div>
          <div className="pm-row">
            <i />
            Private mode · you choose what the AI reads
          </div>
          <div className="pm-row">
            <i />
            Lock with Face ID
          </div>
          <TabBar active="Journal" />
        </div>
      </div>
    );
  }

  return (
    <div className="pm" aria-hidden="true">
      <div className="pm-screen">
        <div className="pm-kicker">Today</div>
        <div className="pm-h">Good evening</div>
        <div className="pm-card warm">
          <b>How are you, really?</b>
          <small>A 20-second check-in shapes what comes next.</small>
          <span className="pm-btn">Check in</span>
        </div>
        <div className="pm-card">
          <b>3-minute breathing reset</b>
          <small>Suggested because you said today felt wired.</small>
        </div>
        <div className="pm-row">
          <i />
          Something else instead
        </div>
        <div className="pm-row">
          <i />
          Tonight&apos;s sleep plan
        </div>
        <TabBar active="Today" />
      </div>
    </div>
  );
}
