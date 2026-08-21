import { beforeEach, describe, expect, it, vi } from "vitest";

const authedFetchMock = vi.fn();
// `net.down` throws from the mock module itself rather than from the spy. A
// vi.fn() whose implementation throws — or returns a rejection — has that error
// recorded and re-surfaced by vitest as an unhandled one, failing the test even
// though `oracleAvailable` catches it and returns false (confirmed by calling
// it directly). Throwing outside the spy keeps the scenario and drops the
// bookkeeping. vi.hoisted because vi.mock factories run before `const`s here.
const net = vi.hoisted(() => ({ down: false }));
vi.mock("../../apps/app/lib/api", () => ({
  authedFetch: (...args: unknown[]) => {
    if (net.down) throw new TypeError("Failed to fetch");
    return authedFetchMock(...args);
  },
}));

import { oracleStream, oracleAvailable } from "../../apps/app/lib/oracle";

/** A ReadableStream that hands back these chunks, split however the caller asks —
 *  the point being that SSE frames do NOT arrive aligned to network reads. */
function sseBody(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let i = 0;
  return new ReadableStream({
    pull(controller) {
      if (i >= chunks.length) return controller.close();
      controller.enqueue(encoder.encode(chunks[i++]));
    },
  });
}

function streamResponse(chunks: string[], status = 200): Response {
  return { ok: status >= 200 && status < 300, status, body: sseBody(chunks) } as Response;
}

async function collect(gen: AsyncGenerator<any>): Promise<any[]> {
  const out: any[] = [];
  for await (const e of gen) out.push(e);
  return out;
}

beforeEach(() => {
  net.down = false;
  authedFetchMock.mockReset();
});

describe("availability", () => {
  it("is true only when the server says so", async () => {
    authedFetchMock.mockResolvedValue({ ok: true, json: async () => ({ available: true }) });
    await expect(oracleAvailable()).resolves.toBe(true);
  });

  it("is false when the agent is off", async () => {
    authedFetchMock.mockResolvedValue({ ok: true, json: async () => ({ available: false }) });
    await expect(oracleAvailable()).resolves.toBe(false);
  });

  it("is false rather than throwing when the endpoint fails", async () => {
    // "Everything degrades without keys" — the chat screen falls back to the
    // plain /chat path, which it cannot do if this rejects.
    authedFetchMock.mockResolvedValue({ ok: false, status: 503 });
    await expect(oracleAvailable()).resolves.toBe(false);
  });

  it("is false when the network never answers", async () => {
    net.down = true;
    expect(await oracleAvailable()).toBe(false);
  });
});

describe("frames", () => {
  it("parses tokens and a final done", async () => {
    authedFetchMock.mockResolvedValue(
      streamResponse([
        'data: {"type":"token","text":"Hel"}\n\n',
        'data: {"type":"token","text":"lo"}\n\n',
        'data: {"type":"done","text":"Hello"}\n\n',
      ]),
    );
    const events = await collect(oracleStream("/oracle/messages", { text: "hi" }));
    expect(events.map((e) => e.type)).toEqual(["token", "token", "done"]);
    expect(events.map((e) => e.text).join("")).toBe("HelloHello");
  });

  it("reassembles a frame split across network reads", async () => {
    // The decisive property. Chunks arrive at whatever size the network
    // chooses, so a parser that assumed one read per frame would drop tokens
    // under exactly the conditions it is hardest to reproduce by hand.
    authedFetchMock.mockResolvedValue(
      streamResponse(['data: {"type":"tok', 'en","text":"split"}\n', '\n']),
    );
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events).toEqual([{ type: "token", text: "split" }]);
  });

  it("handles several frames arriving in one read", async () => {
    authedFetchMock.mockResolvedValue(
      streamResponse(['data: {"type":"token","text":"a"}\n\ndata: {"type":"token","text":"b"}\n\n']),
    );
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events.map((e) => e.text)).toEqual(["a", "b"]);
  });

  it("skips a malformed frame rather than killing the stream", async () => {
    // A dropped token is a cosmetic loss; a dead stream mid-reply looks to the
    // person like the companion stopped talking to them.
    authedFetchMock.mockResolvedValue(
      streamResponse([
        'data: {"type":"token","text":"before"}\n\n',
        "data: {not json at all}\n\n",
        'data: {"type":"token","text":"after"}\n\n',
      ]),
    );
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events.map((e) => e.text)).toEqual(["before", "after"]);
  });

  it("ignores SSE lines that are not data", async () => {
    authedFetchMock.mockResolvedValue(
      streamResponse([': keep-alive\nevent: ping\ndata: {"type":"token","text":"x"}\n\n']),
    );
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events).toEqual([{ type: "token", text: "x" }]);
  });

  it("carries a crisis frame through untouched", async () => {
    // Safety payloads must reach the UI exactly as sent — this is the frame
    // that raises the CrisisBanner.
    const frame = {
      type: "crisis",
      resources: { message: "Help is available", region: "IN", lines: [{ name: "Tele-MANAS", number: "14416" }] },
    };
    authedFetchMock.mockResolvedValue(streamResponse([`data: ${JSON.stringify(frame)}\n\n`]));
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events[0]).toEqual(frame);
  });

  it("carries a tool_confirm through, thread id and all", async () => {
    const frame = { type: "tool_confirm", summary: "Save a memory?", thread_id: "t-1", tool: "add_memory" };
    authedFetchMock.mockResolvedValue(streamResponse([`data: ${JSON.stringify(frame)}\n\n`]));
    expect((await collect(oracleStream("/oracle/confirm", {})))[0]).toEqual(frame);
  });

  it("drops a trailing partial frame instead of yielding half a message", async () => {
    authedFetchMock.mockResolvedValue(
      streamResponse(['data: {"type":"token","text":"whole"}\n\n', 'data: {"type":"tok']),
    );
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events).toEqual([{ type: "token", text: "whole" }]);
  });
});

describe("when the stream cannot start", () => {
  it("yields one error frame instead of throwing", async () => {
    // The caller is a `for await` in a React component; a throw there would
    // surface as an unhandled rejection rather than a message in the thread.
    authedFetchMock.mockResolvedValue({ ok: false, status: 503, body: null });
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events).toEqual([{ type: "error", detail: "Oracle unavailable (503)." }]);
  });

  it("errors when the response has no body at all", async () => {
    authedFetchMock.mockResolvedValue({ ok: true, status: 200, body: null });
    const events = await collect(oracleStream("/oracle/messages", {}));
    expect(events[0].type).toBe("error");
  });

  it("asks for an event stream and posts the body it was given", async () => {
    authedFetchMock.mockResolvedValue(streamResponse([]));
    await collect(oracleStream("/oracle/messages", { text: "hello", thread_id: "t-9" }));
    const [path, init] = authedFetchMock.mock.calls[0];
    expect(path).toBe("/oracle/messages");
    expect(init.method).toBe("POST");
    expect(init.headers.Accept).toBe("text/event-stream");
    expect(JSON.parse(init.body)).toEqual({ text: "hello", thread_id: "t-9" });
  });
});
