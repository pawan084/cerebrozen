# e2e — Playwright tests (web + admin)

Dockerized end-to-end tests that run against a throwaway full stack on an
isolated network (no host ports, won't clash with the dev stack).

```bash
# from the repo root
docker compose -f docker-compose.e2e.yml up --build \
  --abort-on-container-exit --exit-code-from e2e
docker compose -f docker-compose.e2e.yml down -v   # cleanup
```

Exit code is the test result (0 = pass). The `e2e` service waits for api/web/admin
to be ready (`wait.js`), then runs `playwright test`.

## Coverage
- **landing.spec.ts** — hero/features/pricing render; waitlist signup hits the live API.
- **admin.spec.ts** — login, overview stat cards, users list, content create+delete,
  safety review queue, waitlist tab.

In this stack the browser reaches services by name (`web:3000`, `admin:3001`,
`api:8000`), and the API's `CORS_ORIGINS` is set to the in-network origins.
