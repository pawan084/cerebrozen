import Foundation

/// One decoded frame from the Oracle SSE stream (`/oracle/messages`, `/oracle/confirm`).
enum OracleEvent {
    case token(String)            // incremental assistant text
    case widget(RemoteWidget)     // inline activity to render
    case toolConfirm(OracleConfirm)
    case awaitingConfirm
    case done(String)             // final assistant text
    case error(String)
}

/// A paused write action awaiting the user's approval (the ToolConfirmCard).
struct OracleConfirm: Equatable, Identifiable {
    let tool: String
    let summary: String
    let threadId: String
    var id: String { threadId + tool }
}

/// Consume an SSE request as a typed async stream. Each `data: {json}` line is
/// decoded by its `type` discriminator; non-data/keep-alive lines are skipped.
func oracleEventStream(_ request: URLRequest) -> AsyncThrowingStream<OracleEvent, Error> {
    AsyncThrowingStream { continuation in
        let task = Task {
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                    continuation.finish(throwing: APIError.server(http.statusCode, ""))
                    return
                }
                for try await line in bytes.lines {
                    guard line.hasPrefix("data:") else { continue }
                    let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                    guard let data = payload.data(using: .utf8),
                          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                          let type = obj["type"] as? String else { continue }
                    switch type {
                    case "token":
                        if let t = obj["text"] as? String { continuation.yield(.token(t)) }
                    case "widget":
                        if let w = obj["widget"] as? [String: Any],
                           let kind = w["widget_kind"] as? String,
                           let title = w["title"] as? String,
                           let desc = w["description"] as? String {
                            continuation.yield(.widget(RemoteWidget(widget_kind: kind, title: title, description: desc)))
                        }
                    case "tool_confirm":
                        continuation.yield(.toolConfirm(OracleConfirm(
                            tool: obj["tool"] as? String ?? "",
                            summary: obj["summary"] as? String ?? "Confirm this action?",
                            threadId: obj["thread_id"] as? String ?? "")))
                    case "awaiting_confirm":
                        continuation.yield(.awaitingConfirm)
                    case "done":
                        continuation.yield(.done(obj["text"] as? String ?? ""))
                    case "error":
                        continuation.yield(.error(obj["detail"] as? String ?? "Something went wrong."))
                    default:
                        break
                    }
                }
                continuation.finish()
            } catch {
                continuation.finish(throwing: error)
            }
        }
        continuation.onTermination = { _ in task.cancel() }
    }
}
