import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

// next/link and next/navigation are ALIASED to stubs in vitest.config.ts, not
// mocked here. `next` is installed per app and not at the root, so vi.mock
// cannot resolve those ids from a test file and registers nothing at all —
// silently. See tests/stubs/next-navigation.ts.
import { resetRouter, routerState } from "../stubs/next-navigation";
import Shell from "../../apps/portal/components/Shell";
import { PRIVACY_WALL_SIDEBAR } from "../../apps/portal/lib/copy";
import { NAV, PAGE_META } from "../../apps/portal/lib/nav";

beforeEach(resetRouter);

afterEach(cleanup);

// This file also proves the per-app "@/" resolver in vitest.config.ts. Shell
// imports "@/lib/copy", which exists ONLY in apps/portal — apps/app has no
// lib/copy at all. Under the old single alias pointing at apps/app this import
// would not resolve, so the fact that these render is the check.
describe("the privacy wall is on every route", () => {
  // The portal's whole permission to exist beside a mental-health product.
  // "Permanent. Not dismissible, not collapsible, present on every route."
  it("renders the sentence verbatim", () => {
    render(<Shell>page</Shell>);
    expect(screen.getByText(PRIVACY_WALL_SIDEBAR)).toBeTruthy();
  });

  it("names it, so a reader knows what they are looking at", () => {
    render(<Shell>page</Shell>);
    expect(screen.getByText("Privacy wall")).toBeTruthy();
  });

  it.each(["/", "/members", "/engagement", "/reports", "/billing"])(
    "is still there on %s",
    (path) => {
      // A wall that is present on the dashboard and absent on the reporting
      // screens would be missing from exactly the pages where someone is most
      // tempted to go looking for individuals.
      routerState.pathname = path;
      render(<Shell>page</Shell>);
      expect(screen.getByText(PRIVACY_WALL_SIDEBAR)).toBeTruthy();
    },
  );

  it("offers no way to dismiss it", () => {
    render(<Shell>page</Shell>);
    const wall = screen.getByText(PRIVACY_WALL_SIDEBAR).closest(".privacy-note")!;
    expect(wall.querySelector("button")).toBeNull();
  });
});

describe("the topbar names where you are", () => {
  it("uses the route's own title", () => {
    routerState.pathname = "/members";
    render(<Shell>page</Shell>);
    expect(screen.getAllByText(PAGE_META["/members"].title).length).toBeGreaterThan(0);
  });

  it("falls back rather than rendering an unnamed shell", () => {
    // An operator on one of thirty-six screens needs to know which.
    routerState.pathname = "/a-route-that-does-not-exist";
    render(<Shell>page</Shell>);
    expect(screen.getByText("Organisation portal")).toBeTruthy();
  });
});

describe("the sidebar", () => {
  it("lists every group and every built route", () => {
    render(<Shell>page</Shell>);
    for (const group of NAV) {
      expect(screen.getAllByText(group.title).length).toBeGreaterThan(0);
    }
    const linked = NAV.flatMap((g) => g.items).filter((i) => i.href);
    for (const item of linked) {
      expect(screen.getAllByText(item.label).length, `${item.label} is missing`).toBeGreaterThan(0);
    }
  });

  it("says this is illustrative data, not a real member's", () => {
    // The screens render mock aggregates. Not saying so, on a page full of
    // plausible-looking numbers, would be the most consequential kind of quiet.
    render(<Shell>page</Shell>);
    expect(screen.getByText(/illustrative aggregate data/i)).toBeTruthy();
  });
});

describe("the mobile navigation", () => {
  it("has no scrim until it is opened", () => {
    const { container } = render(<Shell>page</Shell>);
    expect(container.querySelector(".backdrop")).toBeNull();
  });

  it("opens, and the scrim is a real button rather than a bare div", async () => {
    // Keyboard reachability: a div with an onClick cannot be tabbed to, so
    // someone navigating without a mouse could open the menu and not close it.
    //
    // Selected by class, not by label: once open, the TOGGLE also reads "Close
    // navigation" — correctly, since it does the same thing — so the label
    // alone matches two controls.
    const user = userEvent.setup();
    const { container } = render(<Shell>page</Shell>);
    await user.click(screen.getByRole("button", { name: "Open navigation" }));

    const scrim = container.querySelector(".backdrop")!;
    expect(scrim).toBeTruthy();
    expect(scrim.tagName).toBe("BUTTON");
    expect(scrim.getAttribute("aria-label")).toBe("Close navigation");
  });

  it("closes again from the scrim", async () => {
    const user = userEvent.setup();
    const { container } = render(<Shell>page</Shell>);
    await user.click(screen.getByRole("button", { name: "Open navigation" }));
    await user.click(container.querySelector(".backdrop") as HTMLElement);
    expect(container.querySelector(".backdrop")).toBeNull();
  });

  it("moves the menu button's label with its state", async () => {
    // It read "Open navigation" while the menu was open — a label that
    // describes the control rather than the action is worse than none, because
    // a screen-reader user acts on it.
    const user = userEvent.setup();
    render(<Shell>page</Shell>);
    const button = screen.getByRole("button", { name: /navigation/i });
    expect(button.getAttribute("aria-expanded")).toBe("false");
    await user.click(button);
    expect(screen.getByRole("button", { name: /navigation/i, expanded: true })).toBeTruthy();
  });

  it("closes when a destination is chosen", async () => {
    // Otherwise the menu stays over the page you just asked for.
    const user = userEvent.setup();
    const { container } = render(<Shell>page</Shell>);
    await user.click(screen.getByRole("button", { name: "Open navigation" }));
    await user.click(screen.getAllByText("Members & seats")[0]);
    expect(container.querySelector(".backdrop")).toBeNull();
  });
});

describe("the page itself", () => {
  it("renders whatever it is wrapping", () => {
    render(<Shell><p>the actual page</p></Shell>);
    expect(screen.getByText("the actual page")).toBeTruthy();
  });
});
