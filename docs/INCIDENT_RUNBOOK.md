# CereBro — Incident runbook

> **Scope.** Something is broken *now*: the API is down, the database is
> unreachable, nudges have stopped, a deploy went wrong, or the safety path is
> behaving oddly. Written 2026-08-22 to close WC-19.
>
> **Not this document:** a personal-data breach — unauthorised access, exposure
> or loss of user data — is [BREACH_RUNBOOK.md](BREACH_RUNBOOK.md), which has
> its own legal clock under DPDP. If an outage turns out to also be a breach,
> that document takes over and this one continues underneath it.
>
> Every technical claim here was verified against the code on 2026-08-22 and
> names the file it came from, so a future reader can re-check rather than
> trust. **The human details are deliberately blank** — see the next section.

---

## 0. What the owner must fill in before this is usable

This runbook is complete on what the system does and empty on who does what,
because inventing a rota is worse than admitting there isn't one. Nothing below
this line is an engineering decision.

| Slot | Needed | Status |
| --- | --- | --- |
| **First responder** | Who gets contacted when the API is down, and how (phone? Signal? email is not a pager) | **TO FILL** |
| **Backup responder** | Who is contacted when the first does not answer within N minutes, and what N is | **TO FILL** |
| **Out-of-hours** | Whether there is any expectation of a response overnight, and if not, what that means for the SLOs below | **TO FILL** |
| **Clinical escalation** | Who is called if a safety-path defect may have affected a real person in crisis. This is not an engineering call, and WC-3 (a named clinical advisor) is still open — until it is filled, this row has no answer | **TO FILL — blocked on WC-3** |
| **Hosting access** | Who can restart the stack / reach the host, and where those credentials live | **TO FILL** |
| **Status communication** | Whether users are told about an outage, where, and by whom | **TO FILL** |

---

## 1. Severity, defined by harm rather than by system state

A rung is chosen by **what a person cannot do**, not by which container is red.

| Sev | Meaning | Examples |
| --- | --- | --- |
| **S1** | Someone in crisis cannot reach help *from the app*, or the app tells them something false about safety | The crisis screen crashes on open; a region resolves to the wrong country's helpline; the safety scan silently stops running on new messages |
| **S2** | The product is unusable for everyone, but the safety path is intact | API down; database unreachable; every authenticated screen fails |
| **S3** | A feature is broken or degraded; the rest works | Nudges not sending; chat replies falling back to the keyless path; sleep charts empty |
| **S4** | Cosmetic or single-surface, no data at risk | One screen mis-renders; a copy error |

**S1 is the only rung with a hard rule:** treat it as live until proven
otherwise, and do not wait for business hours. Everything else can be triaged.

---

## 2. The crisis path: what can and cannot break it

The single most useful fact in an incident, and the reason S1 is rarer than it
looks:

> **The crisis numbers are compiled into each client, not fetched from us.**

* Web: `apps/app/lib/crisis.ts` — `CRISIS_LINES` is a literal array (Tele-MANAS
  14416, emergency 112, KIRAN, findahelpline).
* Android: `ui/screens/CrisisDirectory.kt` — literal targets against string
  resources, region-selected on device.
* iOS: the same directory, hand-synced (ARCHITECTURE's cross-stack table).

**Therefore a total backend outage does not take the crisis door down.** The
Urgent-support screen opens, shows the right region's line, and dials — with the
API, the database and the LLM all dead. When triaging an S2, this is what you
tell people: the emergency path is device-local.

What an outage *does* affect on the safety path:

| Capability | Backend down | LLM provider down |
| --- | --- | --- |
| Crisis screen + dialling | **Works** (device-local) | **Works** |
| Safety plan (already written) | **Works offline** — "Yours, in your words · works offline" | Works |
| Scanning new messages for risk | Stops — nothing is being written to scan | **Degrades, does not stop.** `services/safety.py` runs a keyword net as a **floor** under the LLM, not merely as a fallback; if the keyword net itself errors it returns `elevated` and flags conservatively |
| Trusted-contact escalation | Stops | Fires on the keyword floor's verdict |
| Crisis resources appended to a reply | Stops with chat | Works — the suffix is code, not a model output |

**The hard rule that survives every incident:** safety never blocks. A scan that
cannot run must never stop a message being sent (CLAUDE.md). If a proposed fix
would make the app refuse input while the classifier is down, it is the wrong
fix.

---

## 3. First fifteen minutes

1. **Is the crisis path affected?** Open the app, tap the shield, confirm the
   region's line shows and dials. If it does, you are not in S1 — say so out
   loud, because it changes how fast everything else has to move.
2. **What is actually down?**
   ```
   curl -s https://api.cerebrozen.in/health   # liveness: the process is up
   curl -s https://api.cerebrozen.in/ready    # readiness: Postgres answered
   ```
   `/ready` returns **503 `{"status":"not_ready","database":"unavailable"}`**
   when Postgres is unreachable, and is the fastest way to split "app is broken"
   from "database is gone" (`app/main.py`).
3. **Is it us or a dependency?** `docker compose logs api --since 15m`. A
   request that is not in that log never reached the container — see the port
   trap in §6.
4. **Did we cause it?** `git log --oneline -5` against what is deployed. A
   recent deploy is the first suspect and the fastest fix (§5).
5. **Write down the start time.** Not for process — for the retrospective, where
   "when did it begin" is the question nobody can answer afterwards.

---

## 4. Playbooks

### API is down (S2)
`/health` fails or times out.
* Restart: `docker compose up -d api`, then watch `docker compose logs -f api`.
* Boot-time refusals are *deliberate*: the production guard rejects insecure
  config (dev secrets, dev logins) rather than starting unsafe. Read the
  `Insecure production config:` line — it names every problem at once.
* Migrations run at boot via `prestart.py`. A failed migration means the
  container is up and refusing traffic, not silently half-migrated.

### Database unreachable (S2)
`/ready` returns 503.
* `docker compose ps db`, `docker compose logs db --since 15m`.
* The API is designed to fail loudly here rather than serve wrong data. Clients
  serve their own caches: the app shows its last copy with an honest banner
  (`Session.servedStale` on Android, the equivalent on web), so users see stale
  data *labelled as stale*, not an empty product.
* **There is no backup/restore tooling in `deploy/`** as of 2026-08-22 — no
  `pg_dump` schedule, no restore drill. That is a gap this runbook cannot paper
  over; it is the thing to fix after the incident.

### The LLM provider is down or rate-limiting (S3)
* Nothing needs restarting. Everything degrades by design: chat falls back to
  deterministic replies, plans come back with a rationale that says they were
  not drawn from the conversation, and the safety keyword floor keeps running.
* Confirm with `/health` — `llm_configured` / `ai_enabled` tells you what the
  server thinks it has.
* **Do not** "fix" this by disabling safety scanning to save quota.

### Nudges have stopped (S3)
* The dispatcher runs in-process every `NUDGE_DISPATCH_INTERVAL_MINUTES`
  (0 = an external cron hits `POST /admin/nudges/dispatch`).
* Its log line reports five endings — `sent`, `skipped`, `failed`, `expired`,
  `deferred`. Read them before acting:
  * `expired` climbing = **we were down**, and those nudges were dropped on
    purpose rather than delivered hours late (`MAX_LATENESS`, 2h).
  * `deferred` climbing = deliveries are blipping and being retried (bounded to
    `MAX_ATTEMPTS`, 3).
  * `failed` climbing = devices are refusing after all retries — a real delivery
    problem to chase.
  * `skipped` climbing = people have no delivery channel at all. A reach
    question, not an outage.
* Multiple workers are safe: rows are claimed `FOR UPDATE SKIP LOCKED`, proven
  by `tests/test_nudge_concurrency.py`.

### A bad deploy (any sev)
* Roll back first, diagnose second. The revert is a commit; the forward fix can
  wait for daylight.
* CI gates that must have passed: backend pytest ≥95% coverage, `:app:check`
  (Android, ≥96%), the four Next typechecks + the root test typecheck, and the
  Playwright e2e stack. If a gate was skipped to ship, say so in the
  retrospective — that is the finding.

### The safety path is behaving oddly (S1 until disproven)
* Do **not** disable the classifier. The keyword floor is what remains when the
  model is wrong, and removing it removes the floor.
* Check whether the region is resolving correctly — a UK helpline shown to an
  Indian user is an S1 and has happened before (WC-5's history).
* `CrisisPathDeviceTest` walks the `cerebro://crisis` deeplink on hardware and
  asserts the resolved region's **name and number**. Run it on a device before
  concluding the path is fine.

---

## 5. Detection: the honest state

**There is no alerting.** As of 2026-08-22 there is no uptime monitor, no
latency alerting and no error-rate threshold anywhere — WC-18 is open, and this
runbook must not imply otherwise. Today an incident is noticed by a person
using the app or reading logs.

What *does* exist to detect with:
* `/health` and `/ready`, suitable for any external uptime checker.
* Structured request logging: every request logs `method`, `path`, `status`,
  `duration_ms` and a `request_id` that is also returned to the caller in
  `X-Request-ID` — so a user's report ("it said request id abc123") is
  greppable.
* Structured error tracking (WC-17, 2026-08-22): every unhandled failure is
  fingerprinted and emitted as one `error_event` line, on the backend and both
  clients. A rising count of one fingerprint is the closest thing to an alert
  the system currently has.

**The smallest thing that would fix this:** an external checker on `/ready`
every minute, alerting the first responder in §0. That is one account and one
row of configuration, and it is the difference between "a user told us" and "we
knew".

---

## 6. Traps that have cost real time here

* **A second server on port 8000.** A stray `python -m uvicorn app:app` from
  another project answers `/health` convincingly (its payload is
  `{"ok":true,"service":"aira"}`; ours is
  `{"status":"ok","version":"0.1.0"}`) and 404s everything else. Tell them apart
  by the health **body**, never by getting a 200 — and confirm in
  `docker compose logs api` that the request arrived at all.
* **`| tail` hides a failure.** A piped command returns `tail`'s exit code, so a
  failing suite reports success. Redirect to a file and read `$?`.
* **A killed `docker compose run` leaves its container up**, and a second run
  then shares one test database with the first. `docker rm -f cerebrosg-api-run-*`
  before re-running.

---

## 7. After it is over

Write these five lines somewhere durable — `docs/TODO.md` is fine:

1. When it started, when it was noticed, when it ended. The gap between the
   first two is the alerting gap in §5, measured.
2. What the user-visible effect was, including whether the crisis path was
   affected (§2).
3. What actually caused it, not what triggered it.
4. What made it slow to diagnose — that is usually a missing signal, and it is
   the most valuable output.
5. The one change that would have prevented it, or made it a rung less severe.

No blame section. The gates exist so that shipping a defect is a process
outcome rather than a personal one.

## Cost abuse (added 2026-08-23)

`GET /admin/metrics/ceilings` shows pressure against the daily abuse ceilings
for today: how many accounts have reached each one, how many are approaching it,
and the busiest single count. Identifiers appear only for accounts that actually
reached a ceiling — enough to act on, and no more.

**Nobody is paged.** The payload says `alerting: false` for that reason. This is
a surface someone has to open, which is the same honest limitation the rest of
this runbook records.

Reading it: an account at a ceiling is worth a look, since every ceiling is five
to twenty times a heavy day of genuine use. A *crowd* approaching one is the
opposite finding — the ceiling is too low, and the fix is the number in
`services/usage.CEILINGS`, not the accounts.
