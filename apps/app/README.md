# apps/app — Employee web app (`app.cerebrozen.in`)

The coaching experience in a browser: a member signs in and talks with their
coach. The browser counterpart of the Android app — same platform auth, same
engine session/turn SSE contract.

- **Auth:** `POST {NEXT_PUBLIC_API_URL}/auth/login` (form `username`/`password`)
  → `{access_token, refresh_token}` in `localStorage`; coalesced refresh.
- **Coach:** `POST {NEXT_PUBLIC_ENGINE_URL}/v1/sessions/start?stream=true` then
  `/v1/sessions/{id}/turn?stream=true`, Bearer-auth, SSE events
  `status` / `token` / `done`. The engine mints `session_id` on the `done` event.

"Counts, never content" still holds: this app shows the member their **own**
conversation only; nothing here is exposed to admin/HR surfaces.

## Dev

```
cp .env.example .env.local   # point at local platform:8100 / engine:8000
npm install && npm run dev    # http://localhost:3002
```

Prod build inlines `NEXT_PUBLIC_API_URL=https://api.cerebrozen.in` and
`NEXT_PUBLIC_ENGINE_URL=https://api.cerebrozen.in/engine` (Dockerfile build args).
Served behind Caddy at `app.cerebrozen.in` (see `deploy/Caddyfile`).
