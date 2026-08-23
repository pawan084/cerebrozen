// Block until the API, web, and admin are all serving, then exit 0.
const targets = [
  process.env.API_URL ? `${process.env.API_URL}/health` : "http://api:8000/health",
  process.env.WEB_URL || "http://web:3000",
  process.env.ADMIN_URL || "http://admin:3001",
  // The second API, with every gate switched on. It boots AFTER `api` (it waits
  // for that instance's healthcheck so the two cannot run Alembic against one
  // database at once), so it is the last thing to come up and the easiest to
  // race — gated-api.spec.ts hits it on its very first request.
  `${process.env.GATED_API_URL || "http://api-gated:8000"}/health`,
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  for (const url of targets) {
    let ok = false;
    for (let i = 0; i < 90; i++) {
      try {
        const res = await fetch(url);
        if (res.ok) { ok = true; break; }
      } catch {}
      await sleep(2000);
    }
    if (!ok) {
      console.error("NOT READY:", url);
      process.exit(1);
    }
    console.log("ready:", url);
  }
})();
