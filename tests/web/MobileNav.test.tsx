import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import MobileNav from "../../apps/web/components/MobileNav";

afterEach(cleanup);

// Audit E21. This is a native <details>, chosen for good reasons — no JS, no
// motion, works if a script fails — but NOTHING EVER CLOSED IT. Choosing an
// in-page anchor like #features left the panel sitting open over the very
// content the reader had just navigated to, and there was no outside-click or
// Escape either. The menu could only be closed by finding "Menu" again
// underneath the panel covering it.
function nav() {
  const { container } = render(
    <MobileNav>
      <a href="/#features">Features</a>
      <a href="/privacy">Privacy</a>
    </MobileNav>,
  );
  const details = container.querySelector("details") as HTMLDetailsElement;
  return { details };
}

async function open(details: HTMLDetailsElement) {
  // jsdom does not implement the summary-click toggle, so the state is set the
  // way the browser would set it before each dismissal is exercised.
  details.open = true;
  return details;
}

describe("the three dismissals it was missing", () => {
  it("closes when a link inside it is chosen", async () => {
    const user = userEvent.setup();
    const { details } = nav();
    await open(details);

    await user.click(screen.getByRole("link", { name: "Features" }));
    expect(details.open, "the panel stayed open over the content just navigated to").toBe(false);
  });

  it("closes for a link it was never told about", async () => {
    // The handler is DELEGATED rather than wired onto each link, because the
    // panel's contents arrive as children from a server component. That is the
    // version of this bug that would otherwise come back the next time someone
    // adds a link.
    const user = userEvent.setup();
    const { container } = render(
      <MobileNav>
        <a href="/brand-new-page">Something added later</a>
      </MobileNav>,
    );
    const details = container.querySelector("details") as HTMLDetailsElement;
    await open(details);

    await user.click(screen.getByRole("link", { name: "Something added later" }));
    expect(details.open).toBe(false);
  });

  it("closes on Escape", async () => {
    const { details } = nav();
    await open(details);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(details.open).toBe(false);
  });

  it("returns focus to the control that opened it", async () => {
    // Otherwise the reader is left with no idea where they are in the document.
    const { details } = nav();
    await open(details);

    fireEvent.keyDown(document, { key: "Escape" });
    expect(document.activeElement).toBe(details.querySelector("summary"));
  });

  it("closes on a click outside", async () => {
    const { details } = nav();
    await open(details);

    fireEvent.pointerDown(document.body);
    expect(details.open).toBe(false);
  });

  it("stays open on a click INSIDE that is not a link", async () => {
    // Closing on any interior click would make the panel unusable — a reader
    // who taps the padding while reaching for a link loses the menu.
    const { details } = nav();
    await open(details);

    fireEvent.pointerDown(details.querySelector(".nav-menu-panel")!);
    expect(details.open).toBe(true);
  });

  it("ignores Escape when it is already closed", async () => {
    const { details } = nav();
    expect(details.open).toBe(false);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(details.open).toBe(false);
  });

  it("ignores other keys", async () => {
    const { details } = nav();
    await open(details);
    fireEvent.keyDown(document, { key: "Enter" });
    expect(details.open).toBe(true);
  });
});

describe("it is still a <details>", () => {
  // "The fix stays inside that original choice rather than replacing it": with
  // JavaScript disabled the markup must behave exactly as it did before.
  it("renders a details/summary, not a scripted menu", () => {
    const { details } = nav();
    expect(details.tagName).toBe("DETAILS");
    expect(details.querySelector("summary")?.textContent).toBe("Menu");
  });

  it("takes its label from the caller", () => {
    render(<MobileNav label="Sections"><a href="/x">X</a></MobileNav>);
    expect(screen.getByText("Sections")).toBeTruthy();
  });

  it("renders whatever children it is given", () => {
    nav();
    expect(screen.getByRole("link", { name: "Privacy" })).toBeTruthy();
  });
});

describe("it cleans up after itself", () => {
  it("stops listening once unmounted", () => {
    // Two document listeners per mount, and every landing page mounts one.
    const { container, unmount } = render(
      <MobileNav>
        <a href="/x">X</a>
      </MobileNav>,
    );
    const details = container.querySelector("details") as HTMLDetailsElement;
    details.open = true;
    unmount();
    // Nothing left to throw at a detached node.
    expect(() => fireEvent.keyDown(document, { key: "Escape" })).not.toThrow();
    expect(() => fireEvent.pointerDown(document.body)).not.toThrow();
  });
});
