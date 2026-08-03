# CereBro Android — उपयोग, मॉड्यूल स्थिति, UI सुधार और Backend बदलाव

> यह दस्तावेज़ चार सवालों का एक जगह जवाब है: **app किस काम आता है और लोग इसे क्यों चुनेंगे**,
> **अभी कौन-से module सच में चल रहे हैं**, **app को use कैसे करें**, और **UI तथा backend में
> आगे क्या बदलना पड़ेगा**।
>
> आधार: current source code (`apps/android`, `backend`), device audit log
> [docs/MODULE_AUDIT.md](../../docs/MODULE_AUDIT.md), redesign spec
> [docs/REDESIGN.md](../../docs/REDESIGN.md), और खुला debt
> [docs/TODO.md](../../docs/TODO.md)।
> Module-wise विस्तृत सूची पहले से [APPLICATION_MODULES_HI.md](APPLICATION_MODULES_HI.md) में है —
> यह दस्तावेज़ उसे दोहराता नहीं, उसके ऊपर **निर्णय और अगला काम** जोड़ता है।

---

## 1. एक पन्ने का सार

- **App क्या है:** एक native Android mental-wellness companion — रोज़ का mood check-in,
  sleep diary + sleep sounds, AI conversational support, private journal, personalized plan,
  calming tools और crisis safety pathway। Clinical treatment नहीं, **adjunct** है।
- **असली पकड़ (differentiator) polish नहीं है** — भारतीय market में aesthetics पहले से
  ऊँची है। खाली जगह तीन हैं: **crisis pathway (सिर्फ़ 26.9% apps), evidence/credibility
  (10.9%), और Indian language (2.9%)** — तीनों पर यह app पहले से काम कर चुका है।
- **स्थिति:** 16 में से 16 audited modules device पर 8–9/10 पर हैं। Core flows live backend
  के साथ end-to-end चल रहे हैं।
- **Launch से पहले असली blockers तीन हैं:** Google Play Billing (client + backend दोनों),
  push notifications (FCM — backend में सिर्फ़ web-push है), और Hindi localization का
  qualified review।
- **Backend बदलाव अधिकतर additive हैं** — मौजूदा APIs Android को पूरी तरह serve कर रहे हैं;
  नया काम billing, push, offline sync support और content catalogue depth पर है।

---

## 2. यह app है क्या — और लोग इसे क्यों चुनेंगे

### 2.1 काम क्या करता है

पाँच tabs — **Home · Sleep · Talk · Journal · You** — और उनके नीचे ~28 routes।
हर tab एक असली रोज़मर्रा की ज़रूरत से जुड़ा है:

| Tab | User की ज़रूरत | App क्या देता है |
|---|---|---|
| Home | "आज मैं कैसा हूँ?" | 1-tap mood check-in, presence (streak का नरम रूप), daily plan, time-matched content rail |
| Sleep | "नींद ठीक नहीं हो रही" | Sleep diary, Health Connect prefill, honest weekly summary, soundscapes, CBT-I "Sleep Reset" program |
| Talk | "किसी से बात करनी है" | Streaming AI companion + structured CBT exercises (reframe, box breathing, TIPP) widgets के रूप में |
| Journal | "लिखकर हल्का लगता है" | Private, biometric-locked journal + prompt chips + safety card |
| You | "मेरा data मेरे पास रहे" | Consent controls, theme, reminders, export, account deletion, crisis settings |

### 2.2 लोग क्यों चुनेंगे (positioning)

1. **Crisis सिर्फ़ 2 tap दूर है, हर जगह से** — Tele-MANAS 14416 पहले, फिर 112, फिर
   findahelpline; offline भी काम करता है। बाज़ार की 73% apps यह नहीं देतीं।
2. **हर दावे के पीछे mechanism है** — "Why this works" provenance footer हर tool पर,
   और repo में [docs/CLAIMS_MAP.md](../../docs/CLAIMS_MAP.md) + CI gate
   (`check-claims.mjs`, `check-prices.mjs`) झूठे दावे रोकते हैं।
3. **Gamification pressure नहीं** — streak "presence" है; missed day धुँधला होता है,
   शून्य नहीं होता। Evidence कहता है pressure loops बीमार users को सबसे ज़्यादा तनाव देते हैं।
4. **Privacy पहले** — encrypted token/cache storage, journal lock, 6-category DPDP consent,
   data export, account deletion — सब in-app।
5. **Offline में भी उपयोगी** — grounding, breathing, body scan, CBT reframe, TIPP, CBT-I और
   MBCT content network के बिना चलते हैं।
6. **Hindi की नींव तैयार** — 987 English strings, 806 का Hindi draft (review pending)।

### 2.3 यह किसके लिए नहीं है

Severe symptoms, self-harm risk, medication decisions या emergency — इनके लिए यह app
पर्याप्त नहीं है और खुद यही कहता है। App **companion है, care नहीं**।

---

## 3. अभी कौन-से module चल रहे हैं

### 3.1 Device पर verify हो चुके modules (2026-07-30/31 audit run)

हर module असली hardware पर देखा गया, score किया गया, ठीक किया गया, फिर दोबारा देखा गया।
"Before → After" score:

| Module | Score | क्या गलत था, अब क्या है |
|---|---|---|
| splash | 5 → **9** | पहला frame ठीक किया गया |
| onboarding | 3 → **8** | चार झूठे दावे हटे; consent अब पूरे 6 categories दिखाता है |
| home | 5 → **8** | Bedtime पर bright screen; plan अपना नाम दो बार बोलता था |
| sleep | 6 → **8** | शब्द बीच से टूटता था; सलाह दो बार छपती थी |
| talk | 6 → **8** | Composer transcript के साथ scroll हो जाता था — अब pinned footer |
| journal | 3 → **8** | लिख तो सकते थे, पढ़ नहीं सकते थे |
| you | 6 → **8** | Sign out एक caption था जो sign out कर देता था |
| crisis | 4 → **8** | "Settings में add करें" ऐसी setting की ओर इशारा करता था जो थी ही नहीं |
| safety-plan | 6 → **9** | खाली screen बता नहीं पाती थी कि खाली क्यों है |
| patterns | 3 → **8** | Hide button मौजूद ही नहीं था (backend भी बदला) |
| privacy | 5 → **8** | Read fail होने पर हर consent switch off दिखता था |
| insights | 3 → **8** | बिना check-in के mood reading गढ़ ली जाती थी (backend fix) |
| goals | 5 → **8** | दो tap में goal retire, वापसी का रास्ता नहीं |
| programs | 6 → **8** | Journey छोड़ने पर पूरा हफ़्ता चला जाता था (backend fix) |
| toolkit | 6 → **9** | पाँच जगह "दो मिनट" का वादा, असल समय अलग |
| premium | 3 → **8** | Paywall गलत क़ीमत बोल रहा था (₹399 बनाम असली ₹499) |

**पढ़ने का तरीक़ा:** 9–10 = जैसा है वैसा ship; 7–8 = छोटी polish बाक़ी; 5 से नीचे = user
भरोसा खो देता। यानी आज हर audited module ship-योग्य band में है।

### 3.2 Configuration/data पर निर्भर

| Module | किस पर अटका है |
|---|---|
| Google Sign-In | Code तैयार; OAuth client ID + `GOOGLE_CLIENT_ID` server-side चाहिए |
| Cloud voice (STT/TTS) | `/voice/stt`, `/voice/tts` मौजूद; provider keys के बिना on-device fallback चलता है |
| Health Connect sleep | Read flow तैयार; device पर HC install + user permission चाहिए |
| Audio library depth | Player/mixer तैयार; असली media URLs backend catalogue से आते हैं — production DB में narrated MP3 अभी शून्य |
| Insights/patterns | API सही; useful output पर्याप्त real check-ins के बाद ही आता है |
| Hindi UI | 806/987 strings translated, पर **crisis/clinical strings जानबूझकर English fallback पर** — qualified review pending |

### 3.3 साफ़-साफ़ अधूरा

| Module | स्थिति |
|---|---|
| **Premium / billing** | UI सही क़ीमत बोलता है, CTA **disabled** है और साफ़ लिखा है कि Android पर billing wired नहीं। Google Play Billing न client में है, न backend में (backend सिर्फ़ Stripe + App Store webhooks जानता है)। |
| **Push notifications** | सिर्फ़ local AlarmManager reminders। FCM नहीं — backend में केवल `web_push` model है। |
| **Full offline sync** | कुछ GET responses encrypted cache में हैं; create/update actions के लिए pending-queue नहीं। |
| **Tablet/landscape** | `screenOrientation="portrait"` locked, कोई `sw600dp` resources नहीं। |

---

## 4. App को use कैसे करें

### 4.1 End user का रास्ता

**पहली बार (≈3 मिनट):**
1. Splash → onboarding: welcome, "यह क्या है और क्या नहीं है" disclosure, भाषा, आज की स्थिति।
2. Sign-up से **पहले** एक 2-minute reset breathing — value पहले, account बाद में।
3. Consent screen: छह categories, हर एक label + hint के साथ। यहाँ तक analytics बंद रहता है।
4. Email/password या OTP से account; Google बटन configure होने पर ही active दिखता है।
5. Notification permission और reminder समय।

**रोज़ का loop (≈2 मिनट):**
- Home खोलें → mood + intensity + optional note (एक tap काफ़ी है)।
- Plan hero में आज का एक step पूरा करें।
- रात को Sleep tab → सुबह की check-in, या soundscape/wind-down चला दें।

**हफ़्ते में एक बार:** You → Insights में patterns देखें; Goals/Habits update करें;
Programs में enrolled journey का आज का guide पढ़ें।

**बुरे दिन में:** किसी भी screen से Support door → grounding (5-4-3-2-1), TIPP, safety plan,
trusted contact, या Tele-MANAS 14416। यह रास्ता offline भी खुलता है।

**Privacy control:** You → Privacy से consent बदलें, journal lock चालू करें,
data export माँगें, या account delete करें।

### 4.2 Developer / QA का रास्ता

```bash
# 1. Backend चालू करें (repo root से)
docker compose up --build            # api :8000, web :3000, admin :3001, app :3002

# 2a. Emulator पर — debug build खुद 10.0.2.2:8000 पर जाता है
cd apps/android && ./gradlew installDebug

# 2b. असली फ़ोन पर — USB tunnel + override base URL
adb reverse tcp:8000 tcp:8000
./gradlew installDebug -PapiBaseUrl=http://localhost:8000
# Windows पर tunnel अपने आप बना रहे: pwsh ./adb-reverse-watch.ps1

# 3. जाँच
./gradlew testDebugUnitTest lintVitalRelease
adb exec-out screencap -p > shot.png          # देखे बिना कोई finding असली नहीं
adb logcat -d -s CereBroApi:D                 # app ने असल में क्या call किया
```

Dev logins (सिर्फ़ dev; prod boot guard इन्हें अस्वीकार करता है):
`pawan@cerebro.app / demo12345`, admin के लिए `admin@cerebro.app / admin12345`।

Release build `https://api.cerebrozen.in` पर जाता है — यह `BuildConfig.API_BASE_URL` में
build type से तय होता है, runtime setting नहीं।

**Device QA की सीमाएँ जो पहले चुकाई जा चुकी हैं:** OEM (ColorOS) `pm clear`, `font_scale`
और `wm density` को blocks करता है — इसलिए बड़े font और सँकरी width की robustness
construction से argue करनी पड़ती है (fixed grids की जगह FlowRow), "देख लिया" कहकर नहीं।

---

## 5. UI को और बेहतर कैसे बनाएँ

> Redesign audit का निष्कर्ष याद रखें: **visual polish इस app की सबसे बड़ी कमी नहीं है।**
> इसलिए नीचे की सूची "नया look" नहीं माँगती — वह चीज़ें माँगती है जो अभी सच में चुभती हैं,
> प्राथमिकता के क्रम में।

### P0 — असली defects, ship से पहले

1. **Keyboard के पीछे bottom nav अपनी जगह घेरे रहता है** — IME खुलने पर keyboard के ऊपर
   एक खाली पट्टी बचती है। Fix हर screen का behaviour बदलता है, इसलिए यह अपना alag pass माँगता है।
   → [ui/CereBroApp.kt](app/src/main/java/com/cerebrozen/app/ui/CereBroApp.kt)
2. **Accessibility labels पतले हैं** — पूरे app में सिर्फ़ ~60 `contentDescription` हैं और
   कई screens (PlanScreen, SafetyPlan, Settings के icon buttons) में icon-only controls
   बिना label हैं। TalkBack pass + हर icon button पर label + streaming Talk text के लिए
   `liveRegion` = सबसे सस्ता बड़ा सुधार।
3. **Tap target audit** — audit rubric 48dp माँगता है; chips और छोटे icon buttons को
   एक बार systematically मापना बाक़ी है।
4. **200% font scale और Hindi long strings** — device OEM इन्हें reproduce नहीं करने देता,
   इसलिए इन्हें **Compose preview + Robolectric screenshot test** से pin करना चाहिए,
   ताकि दावा "argue किया" से "verify किया" बन जाए।

### P1 — craft, जो app को premium महसूस कराए

5. **असली illustration assets** — `artVariant` composition/anchor/gradient बदलता है,
   पर 48dp पर फ़र्क़ मामूली है। यह code नहीं, **budget/asset निर्णय** है: 12–15 commissioned
   illustrations पूरे content rail की शक्ल बदल देंगे।
   → [ui/screens/ContentArt.kt](app/src/main/java/com/cerebrozen/app/ui/screens/ContentArt.kt)
6. **Loading states: spinner → skeleton** — Home, Sleep summary, Insights, Search में
   shimmer skeleton content की shape दिखाए, ताकि screen "सोच रही है" के बजाय "बन रही है" लगे।
7. **Optimistic UI** — mood check-in और plan step toggle तुरंत दिखें, network बाद में
   settle हो; fail होने पर एक शांत undo snackbar।
8. **एक साझा Empty/Error component** — अभी हर screen अपना खाली-state गढ़ती है।
   एक `StateBlock(icon, title, body, action)` से copy और spacing दोनों एक जैसे रहेंगे।
   → [ui/screens/Common.kt](app/src/main/java/com/cerebrozen/app/ui/screens/Common.kt)
9. **Motion का एक ही व्याकरण** — page transitions पहले से gentle fade-scale पर हैं;
   अब cards के enter/exit, list item add/remove और bottom-sheet को उसी spring spec पर लाएँ।
   Reduce-motion discipline पहले से मौजूद है — उसे तोड़ें नहीं।
10. **Sounds surface की transport polish** — एक ही `NowPlayingBar` हर जगह; lock-screen
    controls और media notification (MediaSession) अभी सबसे कमज़ोर कड़ी हैं।
    → [audio/SoundscapeService.kt](app/src/main/java/com/cerebrozen/app/audio/SoundscapeService.kt)

### P2 — platform-level, बड़े काम

11. **Large-screen support** — portrait lock हटाकर tablet/foldable के लिए
    content column की max-width और two-pane layout दें। Play Store अब large-screen
    quality को rank में गिनता है।
12. **`androidx.core.splashscreen`** — अभी custom splash है; system splash API cold-start
    को साफ़ करता है और OEM के साथ झगड़ा नहीं करता।
13. **Home-screen widgets (Glance)** — mood check-in, आज का plan step, एक-tap breathing।
    यह retention का सबसे सीधा UI lever है।
14. **Material You accent (opt-in)** — brand tokens प्राथमिक रहें, पर एक "device colours"
    विकल्प Android users को घर जैसा लगता है। Contrast gate उसी test से गुज़रना चाहिए।
15. **Screenshot regression tests (Paparazzi/Roborazzi)** — दो themes × दो font scales ×
    Hindi/English। Device audit ने जो defects पकड़े, उनमें से आधे इसी से रुक जाते।

### जो जानबूझकर **नहीं** करना है

- नए badges, points, levels या leaderboard — evidence कहता है यह null है और unwell users को
  तनाव देता है।
- Glassmorphism वापस लाना — fills opaque हैं, blur dependency कुछ नहीं करती थी, हटाई जा चुकी है।
- Risk score दिखाना बिना उसके साथ रास्ता दिए — यह documented anti-pattern है।

---

## 6. आगे कौन-से module जोड़े जा सकते हैं

### Tier 1 — launch से पहले (ये न हों तो app अधूरा है)

| Module | क्यों |
|---|---|
| **Google Play Billing** | Premium screen मौजूद है पर CTA disabled — monetization शून्य है |
| **FCM push** | Local reminders OEM battery restrictions में मरते हैं; server-driven nudges के बिना re-engagement नहीं |
| **Offline write queue (Room)** | कमज़ोर network में लिखा हुआ mood/journal खोना सबसे बड़ा trust-breaker है |
| **Crash/ANR monitoring** | privacy-safe crash reporting के बिना production blind है |
| **Hindi review sign-off** | crisis/clinical strings का English fallback अभी भी shipping risk है |

### Tier 2 — user value बढ़ाने वाले

1. **Trends dashboard** — mood × sleep correlation, weekly/monthly chart, user-controlled range।
2. **Flexible reminders** — कई schedules, quiet hours, sleep और habit के अलग reminders।
3. **Journal 2.0** — tags, search, filter, edit/delete, encrypted local drafts, attachments।
4. **Sleep depth** — sleep-window recommendation, sleep debt trend, wearable import।
5. **Streak calendar** — presence का month view, forgiveness के साथ।
6. **Professional bridge** — verified therapist directory, consent-based report sharing।
7. **Offline download manager** — programs, stories, soundscapes का explicit download।
8. **Accessibility suite** — voice navigation, captions/transcripts, colour-blind check।

### Tier 3 — platform growth

Wear OS quick check-in · home-screen widgets · cross-device continuity ·
PDF/CSV progress export · opt-in group challenges (कोई public mental-health data नहीं) ·
admin content pipeline से narrated audio की depth।

---

## 7. Backend में क्या बदलना पड़ेगा

### 7.1 अच्छी ख़बर पहले

Android आज **40+ endpoints** call करता है और सब पहले से मौजूद हैं — auth (signup/login/OTP/
refresh/forgot/google), users (me/consent/export/streak/memory/trusted-contact/attest),
moods, journal, sleep (+summary), chat + Oracle SSE, plans, goals, habits, insights,
programs, recommendations, safety-plan, content, voice (stt/tts/status), events, assessment।
**Core flows के लिए backend में कोई नया काम नहीं चाहिए।**

### 7.2 जहाँ backend सच में बदलना पड़ेगा

| # | Feature | Backend में क्या जोड़ना है | आकार |
|---|---|---|---|
| 1 | **Google Play Billing** | `POST /billing/google/verify` (purchase token → Play Developer API validation), `POST /webhooks/googleplay` (RTDN via Pub/Sub), entitlement mapping उसी table में जहाँ Stripe/App Store जाते हैं, product-id contract (`com.cerebrozen.premium.monthly/annual`) | **बड़ा** — अभी सिर्फ़ Stripe (`billing.py`) + App Store (`webhooks.py`) हैं |
| 2 | **FCM push** | Device-token register/unregister endpoints, `web_push` जैसा `device_push` model, nudge dispatcher में FCM sender (keys न हों तो no-op) | **मध्यम** — dispatcher pattern पहले से है |
| 3 | **Offline sync** | Write endpoints पर `Idempotency-Key` support ताकि queue replay duplicate न बनाए; mood/journal/sleep के लिए `updated_after` cursor | **मध्यम** |
| 4 | **Trends dashboard** | `GET /insights/trends?range=` — mood/sleep series एक ही payload में, ताकि client कई calls न करे | **छोटा** — `insights.py` में additive |
| 5 | **Content depth** | Narrated MP3 pipeline असल में भरना (production DB में अभी शून्य audio हैं) — यह content operation है, code नहीं | **operational** |
| 6 | **Server-side reminders** | Nudge scheduling को user timezone + quiet hours के हिसाब से — मॉडल मौजूद है, policy जोड़नी है | **छोटा** |
| 7 | **Journal search/tags** | `journal` model में tags column + `GET /journal?q=&tag=` | **छोटा** |
| 8 | **Therapist directory** | नया `professionals` model + admin CMS entry + read endpoint | **बड़ा, बाद में** |

### 7.3 Backend नियम जो तोड़ने नहीं हैं

- **Schema बदले तो Alembic revision** — boot पर `prestart.py` लगाता है।
- **Keys के बिना सब graceful no-op** — CI blank keys के साथ चलता है।
- **Safety कभी block नहीं करती** — crisis scanning resources जोड़ती है, message reject नहीं करती।
- **Cross-stack contracts हाथ से duplicate हैं** (assessment taxonomy, widget kinds, crisis
  regions, product ids) — backend + client एक ही commit में बदलें।
- **Coverage gate 95%** — नया route बिना test merge नहीं होगा।

---

## 8. सुझाया गया क्रम

| Phase | काम | क्यों इसी क्रम में |
|---|---|---|
| **A. Ship blockers** | Play Billing (client+backend) · FCM · offline write queue · crash monitoring · Hindi review sign-off | इनके बिना Play Store पर जाना revenue-शून्य और support-भारी होगा |
| **B. Trust polish** | P0 UI list (nav/IME, a11y labels, tap targets, font-scale tests) · skeleton + optimistic UI · screenshot regression | ये वही defects रोकते हैं जो audit में score गिराते हैं |
| **C. Value depth** | Trends dashboard · flexible reminders · journal 2.0 · sleep window recommendation · assets/illustration | अब user के पास लौटने की वजह बनती है |
| **D. Platform** | Widgets · large-screen · Wear OS · download manager · therapist bridge | Core stable होने के बाद ही; इनमें से हर एक नया surface है जिसे audit करना पड़ेगा |

**एक वाक्य में:** app का ढाँचा और भरोसा तैयार है — बचा हुआ काम *पैसा लेना (billing)*,
*वापस बुलाना (push)*, *कुछ न खोना (offline)* और *आँख से जाँचना (a11y + screenshot tests)* है।
UI को नए look की नहीं, इन चार चीज़ों के पूरे होने की ज़रूरत है।

---

---

## परिशिष्ट — 2026-08-01 को इस दस्तावेज़ के बाद क्या बन चुका है

इस दस्तावेज़ की सूची में से नीचे की चीज़ें अब **implement हो चुकी हैं** (backend + app दोनों)।
पूरा विवरण [docs/TODO.md](../../docs/TODO.md) के "Shipped 2026-08-01" खंड में है।

| Tier-1 आइटम | स्थिति |
|---|---|
| FCM push | ✅ Backend + client code पूरा; `google-services.json` मिलते ही चालू |
| Offline write queue | ✅ mood + journal, idempotency key सहित, offline undo के साथ |
| Trends dashboard | ✅ नया `/insights/trends` + Trends screen (You → Trends) |
| Journal search/tags | ✅ server-side search + tag chips |
| Sleep की state coverage | ✅ loading / failed / empty अब अलग-अलग |
| Games | ✅ 23 → 12, असली mechanics + timing + progression + आवाज़ |
| Nav-behind-keyboard | ✅ ठीक |

**अभी भी बाक़ी:** Google Play Billing (आपने इसे बाद के लिए चुना), crash/ANR monitoring,
Hindi translation review, असली illustration assets, tablet/large-screen support, और
Android का coverage gate (92.24% बनाम 95% लक्ष्य — यह अंतर पहले से मौजूद था)।

---

*अपडेट: 2026-08-01 · स्रोत: source code, docs/MODULE_AUDIT.md, docs/REDESIGN.md, docs/TODO.md*
