# CereBro — 300 points to a world-class product

Written 2026-08-13, after a full-codebase review with every runnable gate executed
(backend 629 tests / 95.5% coverage, Android `:app:check`, four Next apps on tsc +
lint, six static gates, and the Playwright e2e stack at 53 passing).

**How to read this.** It is a superset, not a sprint. Items are grouped by
discipline and numbered 1–300 continuously so they can be cited in commits and
issues (`WC-114`). Each is written to be actionable — if an item reads as a
sentiment rather than a task, it is a bug in this document.

**Three honest caveats.** (1) A list this long necessarily mixes load-bearing
work with polish; the **§0 Critical Path** below names the ~25 that actually gate
"world class", and everything else is downstream of those. (2) Roughly a third of
these are product or clinical decisions rather than engineering, and are marked
**[decide]** — they need a human with authority, not a pull request. (3) Several
require money or an external account and cannot be started by writing code; they
are marked **[external]**.

---

## §0 The critical path — if only 25 of these happen

Ordered. Nothing below this section makes the product world-class while these are
open.

> **Status note, 2026-08-17.** This list is the 2026-08-13 snapshot and is left
> unrenumbered so `WC-n` citations keep meaning what they meant. Four entries have
> since moved, and a stale critical path misdirects as badly as an incomplete one:
>
> - **4 (TEST-01) — closed.** `backend/tests/conftest.py` blanks the provider keys
>   before settings are read, so key presence in a developer's `.env` or shell can
>   no longer route the suite through live OpenAI. Re-verified 2026-08-17 with a
>   live key sitting in `backend/.env`: **695 passed, 2 skipped, coverage 96%**,
>   and both "degrades without keys" contract tests green. The 2 skips are
>   `tests/test_live_llm.py`, opt-in behind `RUN_LLM_TESTS=1` — which the same fix
>   had made unreachable until 2026-08-17, since the blanking ran unconditionally.
>   Both halves are now pinned: hermetic by default, and the escape hatch opens.
> - **5 (crisis path on hardware) — now automated, and the last step should stay
>   undone.** Walked by hand on the OnePlus against a live backend (a flagged
>   message returns the server's region-correct Tele-MANAS card, TODO §V3-d; the
>   TIPP note's door 2026-08-12, CLAIMS_MAP §2), and since 2026-08-17 walked by
>   `CrisisPathDeviceTest` on every connected run: the `cerebro://crisis` deeplink
>   lands, and the screen shows the name **and the number** that this hardware's
>   own region resolves to — the half that was wrong when a UK helpline reached
>   Indian users. What is *not* verified is the final `ACTION_DIAL` connecting,
>   and nobody should verify it: the last step of this path rings a helpline
>   staffed for people in crisis. The honest close is a test line, not a live dial.
> - **6, 7 (iOS build + walking both clients) — half.** Android has been walked on
>   the CPH2681 repeatedly and now runs `DeviceSmokeTest` on it; iOS has still not
>   been compiled in this repo. This machine has no Mac, so item 6 gates item 7.
> - **281 (Android instrumented tests in CI) — one step left.** The suite runs on
>   hardware on demand and is stable; CI has no handset, so it needs a managed
>   device or emulator runner. See TODO's WC-281 entry.

> **Re-verification, 2026-08-22 — all 25 checked against the code, one by one.**
>
> Three items in a row had been found narrower than written (WC-17's scope,
> WC-24's premise, and a "lint is red" note that was two fixes out of date), so
> the list was audited rather than implemented against. Numbering is untouched;
> `WC-n` still means what it meant. **Evidence is a file, a grep or a passing
> gate — not a memory of having done it.**
>
> | # | Verified state | Evidence |
> |---|---|---|
> | 1 | **Open · owner.** POSITIONING frames the stance ("companion, never clinician") and CLAIMS_MAP enforces the phrasing, but no single defended clinical claim is written down | `docs/POSITIONING.md` §positioning axes |
> | 2 | **Open, and larger than written.** There is no PHQ-9 or GAD-7 in the codebase at all — `services/assessment.py` is an LLM topic generator over a motivations/goals taxonomy. The item is not "run a study", it is "there is no instrument to measure with" | `services/assessment.py` has no scored instrument |
> | 3 | **Open · owner.** Recorded as pending in two places already | `POSITIONING.md` "no named clinical advisor" |
> | 4 | **Closed.** `conftest` blanks provider keys unless `RUN_LLM_TESTS=1` | `backend/tests/conftest.py:30` |
> | 5 | **Closed.** `CrisisPathDeviceTest.kt` runs the deeplink on hardware | `app/src/androidTest/.../CrisisPathDeviceTest.kt` |
> | 6 | **Stale — this reads worse than reality.** CI compiles AND tests iOS on `macos-15` with `xcodebuild test` every run. "No iOS code has been compiled" has not been true since that job landed. What remains is a Mac for *interactive* work | `.github/workflows/ci.yml:300,315` |
> | 7 | **Half.** Android walked on the CPH2681 (55 screens, 2026-08-22). iOS not walked — needs item 6's Mac | `docs/TODO.md` device-walk entries |
> | 8 | **Code ready · account external.** `signingConfigs` reads `releaseKeystore`; the keystore and Console account are the missing half | `apps/android/app/build.gradle.kts:106` |
> | 9 | **Open · external, and split.** Entitlements DO declare `applesignin` + `healthkit`; `GIDClientID` is genuinely absent from `Info.plist` | `CereBro.entitlements`, `Info.plist` |
> | 10 | **Client DONE 2026-08-22 · products external.** `billing-ktx` 7.1.1, `net/Billing.kt` (rules, unit-tested) + `net/BillingBridge.kt` (SDK, excluded like `PushKt`). Verified on the handset: the client connects to Play (Finsky bound `InAppBillingService` to the app) and, with no Console products, offers nothing — so no paywall door is drawn at all. Creating the IAP products remains external | `net/Billing.kt`, `BillingTest.kt` (15) |
> | 11 | **Closed as written.** Org RBAC is real: `ROLE_BENEFITS_OWNER` / `ROLE_PROGRAMME_ADMIN` / `ROLE_ANALYST`, a `role` column per membership, `ROLES_CAN_WRITE` enforced. `User.is_admin` survives as the INTERNAL admin-dashboard flag, which is a different thing from portal roles | `models/organization.py:46,106` |
> | 12 | **Open · confirmed.** Portal auth is email + password; the Caddyfile still comments the portal host out | `apps/portal/lib/api.ts:125`, `deploy/Caddyfile:64` |
> | 13 | **Open · owner.** `/org/summary` and suppressed group totals exist; which aggregates an employer may see is still undecided | `api/routes/organizations.py:137` |
> | 14 | **Open · confirmed.** `backend/media/` holds only `narration/` (9 generated files). Every catalogue key — `ambience.rain`, the pads, the beds — has no licensed audio behind it | `find backend/media -name '*.mp3'` → 9, all narration |
> | 15 | **Half, and the missing half is Android.** Apple is real: `appstore.verify_transaction` does JWS + certificate-chain verification. **Play has nothing** — no `androidpublisher` call anywhere | `services/appstore.py:66`; no Google receipt code |
> | 16 | **Third leg added 2026-08-22.** Activation (`/events`), retention (Dn cohorts) and now `metrics.quiet_users` — people who were here and stopped, named for the behaviour rather than as churn, withheld under 20 people, and shipping its own "this is not the same as churn" caveat. The event vocabulary stays deliberately tiny | `services/metrics.py`, `tests/test_quiet_users.py` (13) |
> | 17 | **Done 2026-08-22**, backend + web + Android, one policy. Transport (where a sink lands) is left as an owner DPDP decision | `services/errors.py`, `apps/app/lib/errors.ts`, `net/ErrorTracking.kt` |
> | 18 | **Open.** `/ready` and `/health` exist and admin metrics aggregate; there is no uptime or latency monitoring and no alerting | `main.py:243` |
> | 19 | **Written 2026-08-22, and blocked on §0 above it.** `docs/INCIDENT_RUNBOOK.md` covers severity-by-harm, the crisis path under outage, per-failure playbooks and the traps. The rota is deliberately blank — and its clinical-escalation row cannot be filled until **item 3** names an advisor, so 19 is gated on 3 rather than on engineering | `docs/INCIDENT_RUNBOOK.md` §0 |
> | 20 | **Open · owner.** The only scheduled deletion is `idempotency.purge_expired`; there is no per-data-class retention schedule | `services/idempotency.py:157` |
> | 21 | **Text shipped, verification open.** The consent notice exists in **13 languages** (en + 12 Eighth-Schedule). What is missing is the native-speaker check, which is the half the item actually names | `apps/app/lib/consentNotice.ts` → 13 keys |
> | 22 | **Open, and correctly claimed.** The accessibility page says "A formal WCAG 2.2 AA audit. We do not claim conformance today." Note the drift: the page says 2.2, §0 says 2.1 | `apps/web/app/accessibility/page.tsx:102` |
> | 23 | **Partial.** Android is 1,254 of 1,838 strings (~68%); the consent notice is 13 languages; web and iOS are not localized | `values-hi/strings.xml` |
> | 24 | **Correctness half proven 2026-08-22**, with mutants. What remains is durability: a nudge scheduled while every instance is down goes out late, and a transiently failed delivery is terminal with no backoff. Neither needs a scheduler vendor | `tests/test_nudge_concurrency.py` |
> | 25 | **Open · confirmed.** No load test, harness or budget exists anywhere in the repo | no k6/locust/bench under `scripts/` or `backend/` |
>
> **What the audit changes.** Items **6** and **11** were wrong in the costly direction — both read as open and are in fact done, and a critical path
> that lists finished work misdirects exactly as badly as one that omits real
> work. Items **2**, **15** and **19** are each *larger or differently shaped*
> than their sentence suggests: no instrument exists to run a study with, receipt
> validation is done on Apple and absent on Play, and the runbook that exists is
> about breaches rather than outages. Those five are the reason to re-read the
> list before working from it.

1. **[decide]** Choose the clinical claim you are willing to defend, in writing, in front of a regulator — everything in §1 follows from that one sentence.
2. Run one real outcome study (pre/post PHQ-9 or GAD-7 on consenting users) so retention is measured against *getting better*, not against session count.
3. Recruit a named clinical advisor and publish the name — an unsigned safety model is not a credential.
4. Close TEST-01: the backend suite makes live OpenAI calls whenever a key is present, so the "degrades without keys" contract is only ever verified on CI.
5. Verify the crisis path end to end against a live account on a real device — it is the one flow where a bug is measured in human harm, and it has never been walked on hardware.
6. Get an iOS build compiling and testing on a Mac; no iOS code in this repo has been compiled in the current review.
7. Walk both native clients on a physical device, including the merged bottom-nav inset change that only a device can adjudicate.
8. **[external]** Create the Play upload keystore and Play Console account.
9. **[external]** Enable Sign in with Apple + HealthKit on the App ID, and add `GIDClientID` to `Info.plist` — the code reads it, the value is absent.
10. **[external]** Create the IAP products; Android has no Play Billing client at all.
11. Replace `is_admin` with real RBAC — one boolean cannot express the seven roles the portal design needs.
12. Ship SSO for the organisation portal; without it the portal cannot go on a public host, which is why `deploy/Caddyfile` still comments it out.
13. **[decide]** Answer the engagement/outcomes question that blocks 20 portal screens: which aggregates may an employer see, given the product refuses per-member behavioural data?
14. **[external]** License the media catalogue — the keyed rows exist and the audio does not.
15. Add server-side receipt validation for App Store *and* Play so entitlement cannot be forged client-side.
16. Instrument the funnel end to end so activation, retention and churn are observable before you spend on acquisition.
17. Add structured error tracking (Sentry or equivalent) across backend and both clients — today a production exception is a log line nobody reads.
18. Add uptime and latency monitoring with alerting on the API, not just the `/ready` endpoint.
19. Write the incident runbook: who is paged, what a crisis-path outage means, and how a safety event is escalated out of hours.
20. **[decide]** Decide the data-retention period per data class and implement deletion jobs to match; DPDP compliance is a schedule, not a policy page.
21. Complete the DPDP consent-notice obligations in all 13 declared languages, verified by a native speaker rather than a translation API.
22. Achieve real WCAG 2.1 AA on the member-facing surfaces and *then* claim it; the accessibility page currently, correctly, claims nothing.
23. Localize the product (not just the consent notice) into at least Hindi end to end, including content and voice.
24. Move nudge dispatch off the in-process loop onto a durable scheduler before multi-instance deployment.
25. Load-test the API at 100× current traffic and fix what breaks before the first marketing push.

---

## §1 Safety and clinical credibility

The category that separates a wellness toy from a product people trust with their
worst hour. This is also where the legal exposure lives.

26. Publish the crisis-detection model's precision and recall on a labelled evaluation set.
27. Build that labelled evaluation set from real (consented, de-identified) messages, not synthetic ones.
28. Add a false-negative review loop: every crisis event a human later reclassifies feeds the eval set.
29. **[decide]** Define the escalation SLA — how long may a crisis event sit unreviewed?
30. Staff the admin safety queue, or automate what happens when nobody is watching it.
31. Add on-call paging for crisis events rather than an email to `OPS_ALERT_EMAIL`.
32. Verify every crisis helpline number quarterly, automatically, and fail CI when one stops resolving.
33. Expand verified crisis regions beyond India with the same verification bar.
34. Add a "this was not a crisis" correction affordance so members can teach the system without punishment.
35. Never let a false positive gate functionality — confirm the current design keeps this true under load.
36. Add a clinician-reviewed safety-plan template rather than a blank form.
37. Test the safety plan's offline path on a device with airplane mode on.
38. **[decide]** Decide whether trusted-contact notification should ever fire without a fresh consent re-confirmation.
39. Rate-limit trusted-contact notifications so a distressed member cannot inadvertently spam someone.
40. Log every trusted-contact send to an auditable trail the member can read.
41. Give members a one-tap way to see and revoke everything the crisis system has recorded about them.
42. Add age-appropriate routing: 18+ is a gate, not a design; consider what happens when a minor lies.
43. Publish a written escalation policy so members know what triggers a human.
44. **[decide]** Decide the product's position on active suicidal ideation: escalate, hand off, or both.
45. Add a "talk to a human now" path that does not depend on the LLM being available.
46. Measure time-to-resource on the crisis path and treat regressions as sev-1.
47. Run an adversarial red-team pass on the safety classifier — jailbreaks, euphemism, code-switching, Hinglish.
48. Test the classifier specifically on Hinglish and transliterated Hindi, the most likely real input.
49. Add crisis resources for specific populations (LGBTQ+, students, new mothers) with verified lines.
50. Have a clinician review every piece of safety-adjacent copy in the product.
51. Add a post-crisis follow-up that is caring rather than automated-feeling.
52. **[decide]** Decide whether crisis history should influence future AI responses, and get consent for it explicitly.
53. Document what the product does *not* do, prominently, and keep `check-claims.mjs` enforcing it.
54. Add a clinical-supervision channel for the AI's outputs — sampled human review of real conversations.
55. Build a mechanism to withdraw an intervention quickly if evidence turns against it.

## §2 Privacy, security and compliance

Your architecture is already the strongest part of the product here. The gap is
proof, not design.

56. Commission an external penetration test and publish the summary.
57. Add automated dependency scanning (Dependabot/Renovate + audit) with a fix SLA.
58. Add SAST to CI for the Python and Kotlin surfaces.
59. Add secret scanning on push, not just the current clean history.
60. **[external]** Rotate every provider key that has ever been shared outside the team.
61. Move secrets to a managed secret store rather than `.env` on the host.
62. Add key rotation procedures with a documented cadence.
63. Encrypt journal and chat content at rest at the column level, not just the disk.
64. **[decide]** Decide whether end-to-end encryption for journals is feasible given AI memory features — and say so honestly either way.
65. Add per-user data-export rate limits so export cannot be used to exfiltrate at scale.
66. Verify the export contains everything DPDP requires, tested rather than asserted.
67. Implement hard deletion with a documented grace period and prove it with a test.
68. Add deletion propagation to every third party (LLM provider logs, analytics, push tokens).
69. **[decide]** Choose an LLM provider posture on training-data use, and contract for it.
70. Add a zero-retention or enterprise agreement with the LLM provider before B2B sales.
71. Audit what leaves the device in analytics events; assume every field is eventually breached.
72. Add differential-privacy or k-anonymity guarantees to organisation aggregates, above the current threshold floor.
73. Prove the reporting threshold cannot be defeated by cohort slicing — the classic re-identification attack.
74. Add tenant isolation tests for the portal that attempt cross-org reads directly.
75. Add authorization tests per role once RBAC exists — the most common source of B2B breaches.
76. Add MFA for admin and portal accounts.
77. Add session management: list active sessions, revoke one, revoke all.
78. Add anomalous-login detection and notification.
79. Add account-takeover protections on email change and password reset.
80. Verify the media token cannot be replayed across users, and pin it with a test.
81. Reduce the media token TTL or bind it to a user, now that the catalogue will hold licensed content.
82. Add Content-Security-Policy reporting endpoints so violations are observed.
83. Add subresource integrity where third-party assets are ever introduced.
84. Add an audit log for every admin action, exportable, and never deletable by admins.
85. Add a formal data-processing agreement and subprocessor list, kept current automatically.
86. Add a vulnerability-disclosure process with a real inbox and response SLA.
87. Add security headers testing to the e2e suite so a regression fails the build.
88. Document the threat model explicitly, including insider risk.
89. Add rate limiting keyed on account as well as IP, so one account cannot abuse from many IPs.
90. Add bot protection on signup before paid acquisition begins.

## §3 Backend and platform

91. Close TEST-01 by blanking provider keys under `TESTING=1` in `conftest`.
92. Add a load test with realistic conversation shapes and measure p95 latency.
93. Add database connection-pool tuning validated under that load.
94. Add read replicas or caching for the catalogue endpoints before scale.
95. Move nudge dispatch to a durable queue with visibility and retry.
96. Make the weekly digest idempotent under concurrent workers, and test it.
97. Add a job dashboard so failed background work is visible.
98. Add graceful degradation when Postgres is slow rather than a hard 503.
99. Add circuit breakers around every third-party call.
100. Add request timeouts on every outbound HTTP call, verified.
101. Add structured JSON logging with correlation ids through to the clients.
102. Add distributed tracing across API, LLM calls, and the Oracle graph.
103. Add cost attribution per endpoint so LLM spend is observable by feature.
104. Add per-user and per-org cost caps with graceful messaging.
105. Cache LLM responses where semantically safe to cut cost.
106. Add prompt caching on the LLM provider for the stable system-prompt prefix.
107. Version the prompts and A/B them with measurable outcomes.
108. Move prompts out of code into a reviewable store with change history.
109. Add evaluation harnesses for prompt changes so quality regressions are caught pre-merge.
110. Add a staging environment that mirrors production configuration exactly.
111. Add blue/green or canary deploys with automated rollback.
112. Add database migration testing against a production-sized dataset.
113. Add backup verification — restore drills, not just backup jobs.
114. Document and test the disaster-recovery RTO/RPO.
115. Add multi-region consideration for India latency.
116. Add API versioning strategy before third parties integrate.
117. Publish an OpenAPI spec consumers can generate clients from.
118. Add idempotency keys on every mutating endpoint the offline queue can replay.
119. Audit N+1 queries on the dashboard and catalogue paths.
120. Add database indexes validated by `EXPLAIN` on real data volumes.
121. Add archival for old chat and mood rows to keep the hot tables small.
122. Add a feature-flag system so risky features ship dark.
123. Add health checks that verify dependencies, not just process liveness.
124. Add graceful shutdown that drains in-flight requests.
125. Right-size container resources and set limits so one tenant cannot starve others.

## §4 AI and the Oracle agent

126. Define what "good" means for a companion response and build a rubric.
127. Build an LLM-as-judge eval suite against that rubric, run per PR.
128. Add golden-transcript regression tests for the highest-traffic intents.
129. Measure and reduce time-to-first-token; perceived latency is the product.
130. Stream everywhere the client can render a stream.
131. Add graceful fallbacks when the model refuses or errors mid-stream.
132. **[decide]** Decide the companion's persona boundaries and write them into the system prompt as testable rules.
133. Prevent the companion from claiming clinical authority, and test that it does not.
134. Add memory summarization so long-term context does not grow unbounded.
135. Give members a readable, editable view of what the AI remembers.
136. Add memory decay so stale facts stop shaping responses.
137. Test the Oracle's confirm-before-write flow adversarially.
138. Add tool-call audit visibility for members, not just admins.
139. Reduce Oracle latency or hide it behind meaningful progress.
140. Add fallback from Oracle to the deterministic router on timeout.
141. Evaluate whether the Oracle earns its complexity versus the simple router.
142. Add multi-turn coherence evaluation across a whole session.
143. Detect and handle code-switching mid-conversation.
144. Add response-length calibration — short answers to short questions.
145. Remove AI tells and validation tics from the companion's voice.
146. **[decide]** Decide whether the AI should ever proactively initiate, and what consent that needs.
147. Personalize with declared preferences before inferred ones.
148. Add an explicit "that response missed" signal and route it into evals.
149. Track refusal rate and investigate spikes.
150. Add a cheaper model tier for classification and routing to cut cost.
151. Benchmark a smaller model for the safety classifier specifically, where latency matters most.
152. Add semantic caching for repeated catalogue and FAQ questions.
153. Set and enforce token budgets per conversation.
154. Add conversation summarization for returning users so context survives.
155. Test behaviour when the provider is entirely down — the whole product must still work.

## §5 iOS

156. Get the project building in CI on macOS with the UI tests green.
157. Add snapshot tests for the design system.
158. Audit every screen with VoiceOver.
159. Support Dynamic Type at accessibility sizes without truncation.
160. Verify Reduce Motion on every animated surface.
161. Add haptics deliberately, and let them be disabled.
162. Add a Widget for the daily check-in.
163. Add Live Activities for breathing and sleep sessions.
164. Add Shortcuts and Siri intents for "start a breathing session".
165. Add a Watch companion for breathing and mood.
166. Verify HealthKit read/write consent copy against Apple's guidelines.
167. Add background audio correctness for sleep stories.
168. Test interruption handling — calls, alarms — during a session.
169. Verify offline behaviour for every tab.
170. Add proper state restoration after termination.
171. Reduce cold-start time and measure it.
172. Audit memory during long voice sessions.
173. Test on the oldest supported device, not the newest.
174. Verify Sign in with Apple end to end once entitlements are enabled.
175. Add App Store privacy-label verification against actual data flows.
176. Localize the App Store listing for India.
177. Add StoreKit 2 subscription-management deep links.
178. Handle subscription lapse and restore gracefully.
179. Test the sponsored-member branch on a device against a live sponsored account.
180. Add crash-free-session-rate monitoring with a target.

## §6 Android

181. Run the full app on a device and on the oldest supported API level.
182. Add Compose UI tests for the critical flows.
183. Add screenshot tests for the design system.
184. Audit with TalkBack on every screen.
185. Support font scaling to 200% without breaking layouts.
186. Verify the bottom-nav inset change on gesture and three-button navigation.
187. Build the Play Billing client.
188. Add Play Integrity to protect entitlement.
189. Verify the offline queue under real network flapping, not emulated.
190. Add WorkManager for reliable background sync.
191. Test Doze-mode behaviour for reminders.
192. Verify notification channels and their descriptions.
193. Add per-app language support properly via `LocaleManager` rather than the deprecated configuration path.
194. Complete the Hindi translation, including the `mg_*` strings that were never translated.
195. Remove the now-unreferenced strings from the retired games.
196. Add a baseline profile to improve cold start.
197. Reduce APK size and measure it per release.
198. Add R8 rules verified against reflection use.
199. Verify FCM delivery on a real device with the app killed.
200. Add Play Store listing assets: feature graphic and phone screenshots.
201. Test on a low-RAM device — a large part of the Indian market.
202. Verify behaviour on a slow 3G connection.
203. Add data-saver awareness for media downloads.
204. Test the sponsored-member branch on a device against a live sponsored account.
205. Add crash-free-session-rate monitoring with a target.

## §7 Web, admin and the organisation portal

206. Ship portal SSO (OIDC) and verify it against a real IdP.
207. Replace `is_admin` with the seven-role model the portal design needs.
208. Add per-role authorization tests.
209. Wire the 20 mock portal screens, or delete the ones that will never have data.
210. Remove the mock-data banner per screen as each becomes true.
211. Build the campaigns model, or cut the feature.
212. Build the pathway-builder model, or cut the feature.
213. **[decide]** Answer what engagement and outcomes may show an employer.
214. Add portal audit-log export for the customer's own compliance.
215. Add seat-management bulk operations beyond import.
216. Add invoice and billing history to the portal.
217. Add a customer-facing status page.
218. Make every inert portal form functional or visibly disabled with a reason.
219. Add optimistic-with-revert semantics to every portal write, matching the consent toggles.
220. Add Core Web Vitals monitoring on the landing site.
221. Get the landing page to a 95+ Lighthouse score on mobile.
222. Refresh the stale marketing screenshots.
223. Add structured data and Open Graph for the landing pages.
224. Add a sitemap and robots policy verified in Search Console.
225. Localize the landing page for India.
226. Add a proper blog or resource centre for organic acquisition.
227. Fix the two `exhaustive-deps` warnings in `apps/app` before they become stale-closure bugs.
228. Add e2e coverage for the authenticated web app's core loops.
229. Add visual-regression testing for the three web surfaces.
230. Add an admin dashboard for cost and usage per organisation.

## §8 Design, accessibility and content

231. Complete a full accessibility audit by someone who uses assistive technology daily.
232. Fix what that audit finds, then update the accessibility page to claim what is now true.
233. Add colour-blind-safe verification for every chart and state colour.
234. Verify contrast in both themes on real devices, not just the token checker.
235. Add motion-sensitivity alternatives for every animation.
236. Add a genuine reading-level pass on all member-facing copy.
237. Have a clinician review the tone of every intervention.
238. Add a copy style guide and enforce it in review.
239. Build the design system into a documented, versioned package.
240. Add design tokens for spacing and typography, not just colour.
241. Reconcile the remaining Night-era overlay values across the three web surfaces.
242. Add a component gallery all three web apps consume.
243. Commission original illustration rather than stock imagery.
244. **[external]** License or commission the audio catalogue.
245. Record professional voice narration in English and Hindi.
246. Add captions and transcripts for every audio and video asset.
247. Expand the practice library with clinician-authored content.
248. Add content versioning so an intervention can be corrected everywhere at once.
249. Move Android's hardcoded practice content into the backend catalogue.
250. Add a content-review cadence with expiry dates on clinical claims.

## §9 Onboarding, retention and growth — without dark patterns

251. Measure activation and define it as a wellbeing action, not a signup.
252. Reduce onboarding to the shortest path that still earns informed consent.
253. A/B test onboarding length against 30-day retention, not completion rate.
254. Add a genuine first-session value moment inside 60 seconds.
255. Add progressive disclosure so advanced features do not overwhelm day one.
256. Add a re-onboarding path for returning lapsed users.
257. Measure and reduce time-to-first-conversation.
258. Add streaks that forgive — and test that a broken streak does not increase churn.
259. **[decide]** Decide the ethical line on notification frequency and hold it.
260. Make every notification cancellable from the notification itself.
261. Personalize nudge timing to observed usage rather than a fixed hour.
262. Add a weekly reflection that shows progress honestly, including plateaus.
263. Never gamify mood — verify no mechanic rewards reporting positive feelings.
264. Add a graceful downgrade experience when premium lapses.
265. Make cancellation as easy as subscribing, on every platform, and test it.
266. Add a pause-subscription option instead of cancel.
267. Add win-back that is respectful and frequency-capped.
268. Add referral only if it cannot pressure a vulnerable user.
269. Measure the harm side of engagement: are heavy users getting better or worse?
270. Add usage caps that suggest a break, and test they do not feel punitive.
271. Publish a public commitment against engagement-maximizing design.
272. Add an in-product feedback channel routed to a human.
273. Run continuous user interviews, especially with churned users.
274. Segment retention by cohort, tier, and sponsorship to find the real product-market fit.
275. Track NPS alongside an outcome measure, and trust the outcome measure more.

## §10 Testing, CI/CD and observability

276. Make the backend suite hermetic so it never touches a paid API.
277. Add mutation testing on the safety and entitlement modules.
278. Add contract tests between backend and both native clients.
279. Automate the cross-stack contract table so drift fails CI rather than review.
280. Add iOS to CI properly, with the UI tests actually executing.
281. Add Android instrumented tests on an emulator in CI.
282. Add e2e coverage for the portal's live screens.
283. Add flake detection and quarantine rather than silent reruns.
284. Track test-suite duration and keep it under a threshold.
285. Add coverage gates per module, not just globally, so critical paths cannot be diluted.
286. Add performance regression tests on the hottest endpoints.
287. Add synthetic monitoring that walks the crisis path in production hourly.
288. Add alerting on safety-event volume anomalies.
289. Add release notes generated from commits, and a public changelog.
290. Add a staged rollout process for both stores.
291. Add feature-flag-driven kill switches for every AI surface.
292. Add a documented rollback procedure tested at least once.
293. Add error budgets and an SLO per critical flow.
294. Add a weekly reliability review with the metrics visible to the whole team.
295. Add a post-incident review template and the practice of using it.

## §11 Business, legal and launch readiness

296. **[external]** Complete every store-account prerequisite currently blocking submission.
297. **[decide]** Set pricing against Indian willingness-to-pay research, not US comparables.
298. Get the terms, privacy policy and DPDP notice reviewed by an Indian data-protection lawyer.
299. **[decide]** Decide the B2B contract model — seats, minimums, data-processing terms — before the first enterprise conversation.
300. Define what success means in one sentence with one number, and put it where the whole team sees it daily.

---

## What this list deliberately does not say

It does not tell you to add more features. The review found a product with
unusually good engineering discipline — a claims-map gate that blocks unbacked
marketing copy, a structural test that proves the organisation modules cannot
import a wellbeing model, entitlements computed rather than stored so a lapsed
sponsorship cannot leave someone premium forever. That discipline is the
foundation of a world-class product and it is already here.

What is missing is **proof that it helps people**, and the operational maturity to
run it when something goes wrong at 3am. Items 1–3 and 17–19 matter more than the
other 295 combined.
