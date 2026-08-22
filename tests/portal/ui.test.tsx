import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import {
  Badge,
  BarChart,
  Metric,
  Notice,
  PageIntro,
  PrivacyWall,
  Progress,
  Spacer,
} from "../../apps/portal/components/ui";
import { PRIVACY_WALL_NOTICE_BODY, PRIVACY_WALL_NOTICE_TITLE } from "../../apps/portal/lib/copy";

afterEach(cleanup);

describe("the privacy-wall notice", () => {
  // Repeated on every reporting surface on purpose: an administrator should
  // never have to remember which page the rule was stated on. Shell.test.tsx
  // covers the permanent sidebar copy; this is the in-page notice.
  it("states the rule and how it is enforced, verbatim from lib/copy", () => {
    render(<PrivacyWall />);
    expect(screen.getByText(PRIVACY_WALL_NOTICE_TITLE)).toBeTruthy();
    expect(screen.getByText(PRIVACY_WALL_NOTICE_BODY)).toBeTruthy();
  });

  it("names the three mechanisms rather than promising privacy in general", () => {
    // "We take privacy seriously" is not a claim anyone can check. Thresholds
    // and suppression are.
    render(<PrivacyWall />);
    expect(screen.getByText(/anonymous group totals/i)).toBeTruthy();
    expect(screen.getByText(/minimum cohort thresholds/i)).toBeTruthy();
    expect(screen.getByText(/small-cell suppression/i)).toBeTruthy();
  });

  it("keeps its shield out of the reading", () => {
    const { container } = render(<PrivacyWall />);
    expect(container.querySelector("span")!.getAttribute("aria-hidden")).toBe("true");
  });
});

describe("charts carry their meaning in text", () => {
  it("gives a proportion bar an accessible name", () => {
    // "A bare bar with no accessible name is unreadable to anyone not looking
    // at it" — which is why the prop is required rather than optional.
    render(<Progress value={62} label="62% of invited members have joined" />);
    expect(screen.getByRole("img", { name: "62% of invited members have joined" })).toBeTruthy();
  });

  it("passes the proportion to the styling as a value, not a width guess", () => {
    const { container } = render(<Progress value={62} label="62% joined" />);
    expect(container.querySelector(".progress > span")!.getAttribute("style")).toContain("--value: 62%");
  });

  it("announces a bar chart once, not once per bar", () => {
    // Eight individually announced bars are noise. The plot carries one label
    // describing the shape and the peak, which is the thing worth hearing.
    const bars = Array.from({ length: 8 }, (_, i) => ({ week: `W${i + 1}`, height: (i + 1) * 10 }));
    render(<BarChart bars={bars} label="Weekly check-ins, rising to a peak in week 8" />);
    expect(screen.getAllByRole("img")).toHaveLength(1);
    expect(screen.getByRole("img", { name: /peak in week 8/ })).toBeTruthy();
  });

  it("still draws every bar it was given", () => {
    const bars = [
      { week: "W1", height: 20 },
      { week: "W2", height: 55 },
      { week: "W3", height: 90 },
    ];
    const { container } = render(<BarChart bars={bars} label="Weekly check-ins" />);
    const drawn = Array.from(container.querySelectorAll(".bar"));
    expect(drawn).toHaveLength(3);
    expect(drawn.map((b) => b.getAttribute("data-label"))).toEqual(["W1", "W2", "W3"]);
    expect(drawn[2].getAttribute("style")).toContain("--h: 90%");
  });

  it("draws nothing rather than an empty frame when there is no data", () => {
    const { container } = render(<BarChart bars={[]} label="No check-ins yet" />);
    expect(container.querySelectorAll(".bar")).toHaveLength(0);
    expect(screen.getByRole("img", { name: "No check-ins yet" })).toBeTruthy();
  });
});

describe("a metric only shows a change when there is one", () => {
  it("shows the number and what it counts", () => {
    render(<Metric value="412" label="Check-ins this month" />);
    expect(screen.getByText("412")).toBeTruthy();
    expect(screen.getByText("Check-ins this month")).toBeTruthy();
  });

  it("renders no delta element at all when none was given", () => {
    // An empty span still takes layout and still reads as something. A month
    // with no comparison should look like a month with no comparison.
    const { container } = render(<Metric value="412" label="Check-ins this month" />);
    expect(container.querySelector(".delta")).toBeNull();
  });

  it("shows a delta when there is one", () => {
    const { container } = render(<Metric value="412" label="Check-ins" delta="+12%" />);
    expect(screen.getByText("+12%")).toBeTruthy();
    expect(container.querySelector(".delta")!.className).toBe("delta");
  });

  it("marks a delta that is bad news", () => {
    const { container } = render(<Metric value="180" label="Check-ins" delta="−34%" warn />);
    expect(container.querySelector(".delta")!.className).toBe("delta warn");
  });
});

describe("the tone classes stay clean", () => {
  // `tone ? \`badge ${tone}\` : "badge"` rather than a template with a hole in
  // it: the naive version emits "badge " or "badge undefined", and a CSS rule
  // written against the exact class then silently stops matching.
  it("leaves a toneless badge with just its base class", () => {
    const { container } = render(<Badge>Pilot</Badge>);
    expect(container.querySelector("span")!.className).toBe("badge");
  });

  it.each(["good", "warn", "danger", "info"] as const)("composes the %s badge", (tone) => {
    const { container } = render(<Badge tone={tone}>Pilot</Badge>);
    expect(container.querySelector("span")!.className).toBe(`badge ${tone}`);
  });

  it("leaves a toneless notice with just its base class", () => {
    const { container } = render(<Notice icon="i">Something worth knowing.</Notice>);
    expect(container.querySelector(".notice")!.className).toBe("notice");
  });

  it.each(["warn", "danger", "info"] as const)("composes the %s notice", (tone) => {
    const { container } = render(
      <Notice tone={tone} icon="!">
        Something worth knowing.
      </Notice>,
    );
    expect(container.querySelector(".notice")!.className).toBe(`notice ${tone}`);
  });

  it("keeps a notice's icon out of the reading", () => {
    render(<Notice icon="⛨">Reporting uses group totals.</Notice>);
    expect(screen.getByText("⛨").getAttribute("aria-hidden")).toBe("true");
    expect(screen.getByText("Reporting uses group totals.")).toBeTruthy();
  });
});

describe("the page introduction", () => {
  it("puts the page's own title at the top of the document outline", () => {
    // One h1 per route, carrying the route's name — the heading a screen
    // reader jumps to first.
    render(<PageIntro eyebrow="Reporting" title="Engagement" lede="How the pilot is being used." />);
    const h1 = screen.getByRole("heading", { level: 1 });
    expect(h1.textContent).toBe("Engagement");
  });

  it("keeps the eyebrow out of the heading structure", () => {
    // It is a label for the section, not a heading of its own; promoting it
    // would put two competing titles at the top of every page.
    render(<PageIntro eyebrow="Reporting" title="Engagement" lede="How the pilot is being used." />);
    expect(screen.getAllByRole("heading")).toHaveLength(1);
    expect(screen.getByText("Reporting")).toBeTruthy();
  });

  it("carries the lede", () => {
    render(<PageIntro eyebrow="Reporting" title="Engagement" lede="How the pilot is being used." />);
    expect(screen.getByText("How the pilot is being used.")).toBeTruthy();
  });
});

describe("the spacer", () => {
  it("is nothing but space", () => {
    // No text, no role: it exists for layout and must not be announced.
    const { container } = render(<Spacer />);
    expect(container.textContent).toBe("");
    expect(container.querySelector(".spacer")).toBeTruthy();
  });
});
