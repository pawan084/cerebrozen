// Oracle SSE-over-POST client (docs/ARCHITECTURE.md frame list). The endpoint
// streams `data: {json}\n\n` frames over a POST response, so this consumes the
// body with fetch streaming rather than EventSource.
import { authedFetch } from "./api";

export type OracleWidget = { widget_kind: string; title: string; description: string };

export type OracleEvent =
  | { type: "token"; text: string }
  | { type: "crisis"; resources?: { message?: string; resources?: { name: string; number: string }[] } }
  | { type: "widget"; widget: OracleWidget }
  | { type: "tool_confirm"; summary?: string; thread_id: string; tool?: string }
  | { type: "awaiting_confirm"; thread_id: string }
  | { type: "done"; text: string }
  | { type: "error"; detail: string };

export async function oracleAvailable(): Promise<boolean> {
  try {
    const res = await authedFetch("/oracle/status");
    if (!res.ok) return false;
    return Boolean((await res.json()).available);
  } catch {
    return false;
  }
}

export async function* oracleStream(
  path: "/oracle/messages" | "/oracle/confirm",
  body: Record<string, unknown>,
): AsyncGenerator<OracleEvent> {
  const res = await authedFetch(path, {
    method: "POST",
    headers: { Accept: "text/event-stream" },
    body: JSON.stringify(body),
  });
  if (!res.ok || !res.body) {
    yield { type: "error", detail: `Oracle unavailable (${res.status}).` };
    return;
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let sep: number;
    while ((sep = buffer.indexOf("\n\n")) !== -1) {
      const frame = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      for (const line of frame.split("\n")) {
        if (!line.startsWith("data: ")) continue;
        try {
          yield JSON.parse(line.slice(6)) as OracleEvent;
        } catch {
          // Malformed frame — skip rather than kill the stream.
        }
      }
    }
  }
}
