import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// next/link and next/navigation are ALIASED to stubs in vitest.config.ts, not
// mocked here — see tests/stubs/next-navigation.ts for why vi.mock cannot work
// from a test file for a package installed per app.
import { resetRouter, routerState } from "../stubs/next-navigation";
import {
  LiveData,
  LiveScreen,
  LoadError,
  NoOrgAccess,
  RequireSession,
  SampleData,
  SaveStatus,
  SignOutButton,
  useOrgData,
  useSave,
} from "../../apps/portal/components/data";
import { NotAnOrgAdminError } from "../../apps/portal/lib/api";

// lib/api is NOT mocked: hasSession() reads localStorage and signOut() goes
// through fetch, so seeding the one and stubbing the other exercises the real
// session code these components are supposed to be wired to.
const REFRESH_KEY = "cerebro_portal_refresh";

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetRouter();
  routerState.replace = vi.fn();
  routerState.push = vi.fn();
  window.localStorage.clear();
  fetchMock = vi.fn(async () => ({ ok: true, status: 200, json: async () => ({}) }) as unknown as Response);
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

/** A promise a test resolves by hand, so a load can be held in flight. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  // Nothing awaits a rejection until the component does; without this Node
  // reports an unhandled rejection and fails the run for the wrong reason.
  promise.catch(() => {});
  return { promise, resolve, reject };
}

describe("every screen declares which kind of numbers it is showing", () => {
  // "36 screens were built against lib/mock.ts, four of them now read the real
  // /org API, and they look identical." An administrator who cannot tell the
  // difference reads invented figures as their own organisation's.
  it("says sample data is not the reader's organisation", () => {
    render(<SampleData />);
    expect(screen.getByText("Sample data — not your organisation.")).toBeTruthy();
  });

  it("explains why, rather than leaving a bare label", () => {
    render(<SampleData />);
    expect(screen.getByText(/the API behind it does not exist yet/i)).toBeTruthy();
    expect(screen.getByText(/fixed example/i)).toBeTruthy();
  });

  it("lets a screen give its own reason", () => {
    render(<SampleData reason="The pilot has no members yet." />);
    expect(screen.getByText("The pilot has no members yet.")).toBeTruthy();
    expect(screen.queryByText(/does not exist yet/i)).toBeNull();
  });

  it("is loud rather than a footnote", () => {
    // Deliberate: the warn styling and a role that reads out, not a `tiny`
    // caption under the numbers it is disclaiming.
    const { container } = render(<SampleData />);
    const notice = container.querySelector(".notice")!;
    expect(notice.className).toContain("warn");
    expect(notice.getAttribute("role")).toBe("note");
  });

  it("says what live data is limited to, so the banner is not just reassurance", () => {
    render(<LiveData />);
    expect(screen.getByText("Live data from your organisation.")).toBeTruthy();
    expect(screen.getByText(/aggregate totals only/i)).toBeTruthy();
    expect(screen.getByText(/reporting threshold applied/i)).toBeTruthy();
  });

  it("does not dress a live banner in the sample banner's warning", () => {
    const { container } = render(<LiveData />);
    expect(container.querySelector(".notice")!.className).not.toContain("warn");
  });
});

describe("a failed read stays a failed read", () => {
  it("says what broke and offers a way back", () => {
    const onRetry = vi.fn();
    render(<LoadError error="The server took too long." onRetry={onRetry} />);
    expect(screen.getByText(/couldn’t load this/i)).toBeTruthy();
    expect(screen.getByText("The server took too long.")).toBeTruthy();
    screen.getByRole("button", { name: "Try again" }).click();
    expect(onRetry).toHaveBeenCalled();
  });

  it("interrupts, because a stale screen with an unnoticed error is the failure mode", () => {
    render(<LoadError error="Network unreachable." onRetry={vi.fn()} />);
    expect(screen.getByRole("alert")).toBeTruthy();
  });

  it("explains the 403 an ordinary user gets instead of treating it as a fault", () => {
    render(<NoOrgAccess />);
    expect(screen.getByText(/does not administer an organisation/i)).toBeTruthy();
    // The distinction that stops a support ticket: having the app is not the
    // same as administering the org that pays for it.
    expect(screen.getByText(/being a CereBro user is not the same thing/i)).toBeTruthy();
  });
});

describe("the live-screen wrapper", () => {
  function live<T>(load: () => Promise<T>, render_: (d: T) => React.ReactNode = (d) => <p>{String(d)}</p>) {
    return render(
      <LiveScreen load={load} what="engagement">
        {render_}
      </LiveScreen>,
    );
  }

  it("says what it is waiting for while the request is in flight", () => {
    live(() => deferred<string>().promise);
    expect(screen.getByText("Loading engagement…")).toBeTruthy();
  });

  it("shows the live banner above the numbers once they arrive", async () => {
    live(async () => "412 check-ins");
    await screen.findByText("412 check-ins");
    expect(screen.getByText("Live data from your organisation.")).toBeTruthy();
  });

  it("NEVER falls back to sample data when the read fails", async () => {
    // The rule the file is built around: a screen that silently substitutes
    // invented numbers for a failed request is worse than one that says it is
    // broken. Both halves are asserted — the error appears AND the children,
    // which are the numbers, do not.
    live(async () => {
      throw new Error("upstream timed out");
    });
    await screen.findByText("upstream timed out");
    expect(screen.queryByText("Live data from your organisation.")).toBeNull();
    expect(screen.queryByText("Sample data — not your organisation.")).toBeNull();
  });

  it("renders nothing of the screen behind a failed read", async () => {
    live(
      async () => {
        throw new Error("boom");
      },
      () => <p>Median mood 3.4</p>,
    );
    await screen.findByText("boom");
    expect(screen.queryByText("Median mood 3.4")).toBeNull();
  });

  it("tells a non-admin why, instead of showing them an error", async () => {
    live(async () => {
      throw new NotAnOrgAdminError("Not an org admin");
    });
    await screen.findByText(/does not administer an organisation/i);
    // A 403 here is the system working correctly; "We couldn't load this" would
    // send someone to support over a permission that is behaving as designed.
    expect(screen.queryByText(/couldn’t load this/i)).toBeNull();
  });

  it("really re-runs the read when Try again is pressed", async () => {
    const user = userEvent.setup();
    let attempt = 0;
    live(async () => {
      attempt += 1;
      if (attempt === 1) throw new Error("first attempt failed");
      return "614 check-ins";
    });
    await screen.findByText("first attempt failed");
    await user.click(screen.getByRole("button", { name: "Try again" }));
    await screen.findByText("614 check-ins");
    expect(attempt).toBe(2);
  });

  it("clears the old error while the retry is in flight", async () => {
    // Otherwise the failure banner sits above a spinner and the screen looks
    // broken while it is in fact working.
    const user = userEvent.setup();
    let attempt = 0;
    live(async () => {
      attempt += 1;
      if (attempt === 1) throw new Error("first attempt failed");
      return deferred<string>().promise;
    });
    await screen.findByText("first attempt failed");
    await user.click(screen.getByRole("button", { name: "Try again" }));
    await waitFor(() => expect(screen.queryByText("first attempt failed")).toBeNull());
    expect(screen.getByText("Loading engagement…")).toBeTruthy();
  });
});

describe("a superseded read cannot overwrite a newer one", () => {
  // The `cancelled` flag in useOrgData. Without it, a slow first request that
  // lands after a retry paints stale numbers over fresh ones — and the screen
  // gives no sign it happened.
  function Harness({ load }: { load: () => Promise<string> }) {
    const { data, error, forbidden, retry } = useOrgData(load);
    return (
      <div>
        <button onClick={retry}>retry</button>
        <span data-testid="state">{forbidden ? "forbidden" : (error ?? data ?? "pending")}</span>
      </div>
    );
  }

  it("ignores the first response when a retry has already replaced it", async () => {
    const user = userEvent.setup();
    const first = deferred<string>();
    const second = deferred<string>();
    let call = 0;
    render(
      <Harness
        load={() => {
          call += 1;
          return call === 1 ? first.promise : second.promise;
        }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "retry" }));
    second.resolve("fresh");
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("fresh"));

    // Flushed inside act: a bare `await Promise.resolve()` is NOT enough to
    // land the .then and its setState, so the assertion would pass whether or
    // not the cancellation exists — the first version of this test did exactly
    // that, and deleting `cancelled = true` sailed straight through it.
    await act(async () => {
      first.resolve("stale");
      await first.promise;
    });
    expect(screen.getByTestId("state").textContent).toBe("fresh");
  });

  it("keeps a permission answer separate from a failure", async () => {
    render(
      <Harness
        load={async () => {
          throw new NotAnOrgAdminError("nope");
        }}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("forbidden"));
  });

  it("carries a thrown non-Error through as something readable", async () => {
    render(
      <Harness
        load={async () => {
          throw "a string, from somewhere careless";
        }}
      />,
    );
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("Something went wrong."));
  });
});

describe("the session guard", () => {
  it("sends a signed-out visitor to sign in", async () => {
    render(
      <RequireSession>
        <p>member list</p>
      </RequireSession>,
    );
    await waitFor(() => expect(routerState.replace).toHaveBeenCalledWith("/signin"));
  });

  it("renders nothing of the guarded screen while signed out", () => {
    render(
      <RequireSession>
        <p>member list</p>
      </RequireSession>,
    );
    expect(screen.queryByText("member list")).toBeNull();
    expect(screen.getByText("Loading your session…")).toBeTruthy();
  });

  it("replaces rather than pushes, so Back does not bounce", async () => {
    // A push would leave the guarded route in history: Back returns to it, the
    // guard fires again, and the visitor is stuck in a loop they did not cause.
    render(
      <RequireSession>
        <p>member list</p>
      </RequireSession>,
    );
    await waitFor(() => expect(routerState.replace).toHaveBeenCalled());
    expect(routerState.push).not.toHaveBeenCalled();
  });

  it("lets a signed-in administrator through", async () => {
    window.localStorage.setItem(REFRESH_KEY, "a-refresh-token");
    render(
      <RequireSession>
        <p>member list</p>
      </RequireSession>,
    );
    await screen.findByText("member list");
    expect(routerState.replace).not.toHaveBeenCalled();
  });
});

describe("signing out", () => {
  it("drops the local session and leaves for the sign-in page", async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(REFRESH_KEY, "a-refresh-token");
    render(<SignOutButton />);
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(window.localStorage.getItem(REFRESH_KEY)).toBeNull());
    expect(routerState.replace).toHaveBeenCalledWith("/signin");
  });

  it("signs out even when the server cannot be told", async () => {
    // Revoking server-side is best effort; a failed request must not leave
    // someone signed in on a shared machine because the network was down.
    const user = userEvent.setup();
    fetchMock.mockRejectedValue(new Error("offline"));
    window.localStorage.setItem(REFRESH_KEY, "a-refresh-token");
    render(<SignOutButton />);
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(window.localStorage.getItem(REFRESH_KEY)).toBeNull());
    expect(routerState.replace).toHaveBeenCalledWith("/signin");
  });
});

describe("saving never claims more than it did", () => {
  // "A portal that claims a saved threshold it did not save is worse than one
  // that cannot save at all — the administrator walks away believing a privacy
  // control is in force."
  function SaveHarness({
    run,
    onDone,
    savedLabel,
  }: {
    run: () => Promise<string>;
    onDone?: (r: string) => void;
    savedLabel?: string;
  }) {
    const { save, reset, ...state } = useSave();
    return (
      <div>
        <button onClick={() => save(run, onDone)}>Save</button>
        <button onClick={reset}>Reset</button>
        <span data-testid="status">{state.status}</span>
        <SaveStatus state={state} savedLabel={savedLabel} />
      </div>
    );
  }

  it("says nothing at all before anyone has saved", () => {
    render(<SaveHarness run={async () => "ok"} />);
    expect(screen.queryByRole("status")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("confirms only after the write actually returns", async () => {
    const user = userEvent.setup();
    const onDone = vi.fn();
    render(<SaveHarness run={async () => "threshold=5"} onDone={onDone} />);
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Saved.");
    expect(onDone).toHaveBeenCalledWith("threshold=5");
  });

  it("lets a screen name what was saved", async () => {
    const user = userEvent.setup();
    render(<SaveHarness run={async () => "ok"} savedLabel="Reporting threshold updated." />);
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Reporting threshold updated.")).toBeTruthy();
  });

  it("goes back to not-saved when the write fails, and says nothing was changed", async () => {
    const user = userEvent.setup();
    render(
      <SaveHarness
        run={async () => {
          throw new Error("The threshold must be at least 5.");
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Not saved.");
    expect(screen.getByText(/Nothing was changed/)).toBeTruthy();
    expect(screen.getByText(/The threshold must be at least 5\./)).toBeTruthy();
    // The status must not sit on "saved" behind an error banner: a caller that
    // reads status alone would re-render the form as though the write landed.
    expect(screen.getByTestId("status").textContent).toBe("idle");
    expect(screen.queryByText("Saved.")).toBeNull();
  });

  it("tells a read-only role what actually happened", async () => {
    // A permission answer, not a fault — "That didn't save" would send someone
    // retrying a write their role will never be allowed to make.
    const user = userEvent.setup();
    render(
      <SaveHarness
        run={async () => {
          throw new NotAnOrgAdminError("403");
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText(/Your role can read reports but not change this\./)).toBeTruthy();
  });

  it("still says something when what was thrown was not an error", async () => {
    const user = userEvent.setup();
    render(
      <SaveHarness
        run={async () => {
          throw { code: 500 };
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText(/That didn’t save\.|That didn't save\./)).toBeTruthy();
  });

  it("clears a previous failure when the next attempt succeeds", async () => {
    const user = userEvent.setup();
    let attempt = 0;
    render(
      <SaveHarness
        run={async () => {
          attempt += 1;
          if (attempt === 1) throw new Error("The threshold must be at least 5.");
          return "ok";
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Not saved.");
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Saved.");
    expect(screen.queryByText("Not saved.")).toBeNull();
  });

  it("can be put away again", async () => {
    const user = userEvent.setup();
    render(<SaveHarness run={async () => "ok"} />);
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Saved.");
    await user.click(screen.getByRole("button", { name: "Reset" }));
    await waitFor(() => expect(screen.queryByText("Saved.")).toBeNull());
  });

  it("interrupts for a failure and merely announces a success", async () => {
    // Different urgency, different role: a save that did not happen has to
    // reach someone who has already looked away.
    const user = userEvent.setup();
    render(<SaveHarness run={async () => "ok"} />);
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Saved.");
    expect(screen.getByRole("status")).toBeTruthy();
    expect(screen.queryByRole("alert")).toBeNull();

    cleanup();
    render(
      <SaveHarness
        run={async () => {
          throw new Error("nope");
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Save" }));
    await screen.findByText("Not saved.");
    expect(screen.getByRole("alert")).toBeTruthy();
  });
});
