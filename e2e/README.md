# e2e — End-to-End Suite

Playwright against the **composed stack** — five services and one Postgres, with
zero API keys (the engine runs its mock LLM provider).

```bash
npm ci
npx playwright install --with-deps chromium

npm run e2e:compose     # bring the stack up, run, tear down (what CI does)

# or, against a stack you already have running (compose or dev servers):
npm test
npm test -- --headed 04-ops-console
```

## Why it exists

`docs/ENGINEERING.md` §"Cross-stack change protocol": *"SSE vocabulary and JWT
claims changes additionally require an e2e run against the composed stack before
merge."* This suite **is** that gate. It is also the only place several promises
can be checked at all, because they only exist *between* services:

| Spec | The thing only an e2e run can prove |
|---|---|
| `01-tenancy` | The JWT the platform *actually signs* is accepted by the engine that *actually validates it* — and a forged one isn't. App-layer tenancy is worth nothing if it doesn't hold over the wire. |
| `02-employee-journey` | The SSE event names **on the wire** (`status`/`node`/`token`/`done`), read from the raw stream rather than through a client that might paper over a rename. |
| `03-hr-aggregates` | A suppressed metric carries **no value in the HTTP response**. A unit test proves the floor function floors; only this proves the number never left the building. |
| `04-ops-console` | The admin's per-request nonce CSP, fresh each request, and that it doesn't break the console it protects. The admin has no unit-test runner — this is its home. |
| `05-site-links` | `NEXT_PUBLIC_*` hosts are inlined at **build** time from Docker build args. A stack built without them points at production, and that is invisible in the markup and at runtime. The only way to know is to build it and click. |

## Rules

- **No mocks.** A mocked backend cannot discharge the protocol above.
- **Seeded personas, not bespoke fixtures** (`tests/helpers.ts`): the suite runs
  against the same stack `docker compose up` gives a developer, so it can't drift
  from it. They cannot exist in production — `guard_production()` refuses to boot
  with `CEREBROZEN_SEED_DEV_ADMIN` on.
- **Serial, one worker.** One stack, one database: parallel workers would race on
  org/seat state. File order is execution order; each file owns its data.
- **Assert the boundary, not the coaching.** The mock provider makes replies
  meaningless — quality belongs to the evals harness, off the merge path.

URLs default to the ports compose publishes (also the dev-server ports).
Override with `E2E_WEB_URL`, `E2E_ADMIN_URL`, `E2E_APP_URL`, `E2E_PLATFORM_URL`,
`E2E_ENGINE_URL`.
