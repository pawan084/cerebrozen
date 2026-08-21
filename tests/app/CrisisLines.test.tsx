import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { CrisisLines } from "../../apps/app/components/CrisisLines";
import { CRISIS_LINES } from "../../apps/app/lib/crisis";

afterEach(cleanup);

// The one place crisis numbers are rendered — chat banner, journal banner and
// /support all use it. lib/crisis is already tested; what is untested is
// whether the component puts those numbers on screen in a form a person in
// distress can actually use. A number that renders correctly and dials wrongly
// is the worst failure this product has.
describe("rendering the crisis directory", () => {
  it("leads with Tele-MANAS, whatever order it is handed", () => {
    render(
      <CrisisLines
        lines={[
          { name: "Emergency services", number: "112" },
          { name: "Tele-MANAS", number: "14416" },
        ]}
      />,
    );
    const links = screen.getAllByRole("link");
    expect(links[0].textContent).toContain("Tele-MANAS");
  });

  it("makes every shipped number tappable", () => {
    render(<CrisisLines />);
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(CRISIS_LINES.length);
    for (const a of links) {
      expect(a.getAttribute("href")).toMatch(/^(tel:[+\d]+|https?:\/\/)/);
    }
  });

  it("dials a hyphenated number without its hyphens", () => {
    // KIRAN is displayed as 1800-599-0019 because that is readable; a tel: URI
    // carrying hyphens is not reliably dialled.
    render(<CrisisLines lines={[{ name: "KIRAN", number: "1800-599-0019" }]} />);
    expect(screen.getByRole("link").getAttribute("href")).toBe("tel:18005990019");
  });

  it("still SHOWS the readable form", () => {
    render(<CrisisLines lines={[{ name: "KIRAN", number: "1800-599-0019" }]} />);
    expect(screen.getByText("1800-599-0019")).toBeTruthy();
  });

  it("says what will happen, for someone who cannot see the screen", () => {
    // A screen-reader user has to know a tap places a CALL before they make it.
    render(<CrisisLines lines={[{ name: "Tele-MANAS", number: "14416" }]} />);
    expect(screen.getByLabelText("Call Tele-MANAS on 14416")).toBeTruthy();
  });

  it("labels a web resource as opening, not calling", () => {
    render(<CrisisLines lines={[{ name: "Find a helpline", number: "https://findahelpline.com" }]} />);
    expect(screen.getByLabelText("Open Find a helpline")).toBeTruthy();
  });

  it("opens a web resource in a new tab, safely", () => {
    // rel=noreferrer as well as target=_blank: the opened page must not get a
    // handle back onto a page that may be mid-crisis-conversation.
    render(<CrisisLines lines={[{ name: "Find a helpline", number: "https://findahelpline.com" }]} />);
    const a = screen.getByRole("link");
    expect(a.getAttribute("target")).toBe("_blank");
    expect(a.getAttribute("rel")).toContain("noreferrer");
  });

  it("does not open a phone number in a new tab", () => {
    render(<CrisisLines lines={[{ name: "Tele-MANAS", number: "14416" }]} />);
    expect(screen.getByRole("link").getAttribute("target")).toBeNull();
  });

  it("strips the scheme from a displayed URL but not from the href", () => {
    render(<CrisisLines lines={[{ name: "Find a helpline", number: "https://findahelpline.com" }]} />);
    expect(screen.getByText("findahelpline.com")).toBeTruthy();
    expect(screen.getByRole("link").getAttribute("href")).toBe("https://findahelpline.com");
  });

  it("renders the compact variant without losing anything", () => {
    // The chat banner uses compact; fewer pixels must not mean fewer numbers.
    const { container } = render(<CrisisLines compact />);
    expect(container.querySelector(".crisis-lines.compact")).toBeTruthy();
    expect(screen.getAllByRole("link")).toHaveLength(CRISIS_LINES.length);
  });

  it("renders nothing rather than crashing on an empty list", () => {
    // A server-driven region with no resources must not take the banner down
    // with it — the static fallback is the caller's job, not a stack trace.
    render(<CrisisLines lines={[]} />);
    expect(screen.queryAllByRole("link")).toHaveLength(0);
  });
});

describe("the landing's copy is a real mirror", () => {
  // apps/web's CrisisLines says it "mirrors apps/app/components/CrisisLines.tsx
  // deliberately — same markup, same class names, same aria wording, so a fix
  // to one reads as a fix to the other". Two files that claim to be identical
  // and drift are worse than two that never claimed it, and the landing is the
  // FIRST surface someone in crisis reaches — before they have an account.
  const body = (path: string) => {
    const src = readFileSync(resolve(__dirname, "../..", path), "utf8");
    // Everything from the import line down; the header comments differ on
    // purpose, and only the rendered markup is the shared contract.
    return src.slice(src.indexOf("import {")).trim();
  };

  it("is byte-identical below the header comment", () => {
    expect(body("apps/web/components/CrisisLines.tsx")).toBe(
      body("apps/app/components/CrisisLines.tsx"),
    );
  });
});
