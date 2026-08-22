import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import Faq, { type FaqEntry } from "../../apps/web/components/Faq";

afterEach(cleanup);

const ITEMS: FaqEntry[] = [
  { q: "Is it therapy?", a: "No — it is a companion, never a clinician." },
  { q: "What does it store?", a: "Only what you choose.", cta: { href: "/privacy", label: "Read the policy" } },
  { q: "Can I delete it?", a: "Yes, from inside the app or by email." },
];

function faq() {
  return render(<Faq items={ITEMS} />);
}

const panelOf = (q: string) =>
  document.getElementById(`faq-panel-${q.replace(/\W+/g, "-").toLowerCase()}`)!;

describe("one open at a time", () => {
  it("starts with everything closed", () => {
    faq();
    for (const item of ITEMS) {
      expect(screen.getByRole("button", { name: new RegExp(item.q, "i") }).getAttribute("aria-expanded"))
        .toBe("false");
    }
  });

  it("opens the one that was asked for", async () => {
    const user = userEvent.setup();
    faq();
    await user.click(screen.getByRole("button", { name: /Is it therapy/i }));
    expect(screen.getByRole("button", { name: /Is it therapy/i, expanded: true })).toBeTruthy();
  });

  it("closes the previous one rather than stacking", async () => {
    const user = userEvent.setup();
    faq();
    await user.click(screen.getByRole("button", { name: /Is it therapy/i }));
    await user.click(screen.getByRole("button", { name: /What does it store/i }));

    expect(screen.getAllByRole("button", { expanded: true })).toHaveLength(1);
    expect(screen.getByRole("button", { name: /What does it store/i, expanded: true })).toBeTruthy();
  });

  it("closes on a second press of the same question", async () => {
    const user = userEvent.setup();
    faq();
    const button = screen.getByRole("button", { name: /Is it therapy/i });
    await user.click(button);
    await user.click(button);
    expect(screen.queryAllByRole("button", { expanded: true })).toHaveLength(0);
  });
});

describe("a closed answer is genuinely out of reach", () => {
  // "the closed panel has to be `inert` + `aria-hidden` so its links never take
  // focus while invisible". A panel that is only visually collapsed still hands
  // a keyboard user a link they cannot see, and a screen reader an answer the
  // page appears not to be showing.
  it("is aria-hidden while closed", () => {
    faq();
    expect(panelOf(ITEMS[0].q).getAttribute("aria-hidden")).toBe("true");
  });

  it("is inert while closed", () => {
    faq();
    expect(panelOf(ITEMS[0].q).hasAttribute("inert")).toBe(true);
  });

  it("drops BOTH once opened", async () => {
    // `inert={false}` would still render the attribute and trap the answer —
    // the attribute has to be omitted entirely, which is why the component
    // spreads an object rather than passing a boolean.
    const user = userEvent.setup();
    faq();
    await user.click(screen.getByRole("button", { name: /Is it therapy/i }));

    const panel = panelOf(ITEMS[0].q);
    expect(panel.hasAttribute("inert"), "an opened answer is still inert").toBe(false);
    expect(panel.getAttribute("aria-hidden")).toBe("false");
  });

  it("puts the closed one back out of reach", async () => {
    const user = userEvent.setup();
    faq();
    await user.click(screen.getByRole("button", { name: /Is it therapy/i }));
    await user.click(screen.getByRole("button", { name: /What does it store/i }));

    expect(panelOf(ITEMS[0].q).hasAttribute("inert")).toBe(true);
    expect(panelOf(ITEMS[1].q).hasAttribute("inert")).toBe(false);
  });
});

describe("the wiring a screen reader follows", () => {
  it("points each question at its own answer", () => {
    faq();
    for (const item of ITEMS) {
      const button = screen.getByRole("button", { name: new RegExp(item.q, "i") });
      const id = button.getAttribute("aria-controls")!;
      expect(document.getElementById(id), `${item.q} controls nothing`).toBeTruthy();
    }
  });

  it("gives every answer a unique id", () => {
    faq();
    const ids = ITEMS.map((i) =>
      screen.getByRole("button", { name: new RegExp(i.q, "i") }).getAttribute("aria-controls"),
    );
    expect(new Set(ids).size).toBe(ITEMS.length);
  });

  it("hides the decorative sign from assistive tech", () => {
    // The "+" says nothing that aria-expanded does not already say.
    const { container } = faq();
    for (const sign of container.querySelectorAll(".faq-sign")) {
      expect(sign.getAttribute("aria-hidden")).toBe("true");
    }
  });
});

describe("the content", () => {
  it("renders every question", () => {
    faq();
    for (const item of ITEMS) expect(screen.getByText(item.q)).toBeTruthy();
  });

  it("renders a call to action when one is given", async () => {
    const user = userEvent.setup();
    faq();
    await user.click(screen.getByRole("button", { name: /What does it store/i }));
    const link = screen.getByRole("link", { name: "Read the policy" });
    expect(link.getAttribute("href")).toBe("/privacy");
  });

  it("survives an empty list", () => {
    const { container } = render(<Faq items={[]} />);
    expect(container.querySelectorAll(".faq-item")).toHaveLength(0);
  });
});
