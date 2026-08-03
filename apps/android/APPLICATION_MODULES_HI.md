# CereBro Android Application — परिचय और मॉड्यूल दस्तावेज़

## 1. एप्लिकेशन क्या है?

**CereBro** एक native Android mental-wellness और self-care application है। इसका उद्देश्य user को अपनी मनःस्थिति समझने, mood और sleep track करने, journal लिखने, guided calming exercises करने, AI-based conversational support लेने और personal wellness plan follow करने में सहायता देना है।

यह application clinical diagnosis या emergency service का replacement नहीं है। Crisis और safety features user को grounding, safety-plan, trusted contact और human-support resources तक पहुँचाने के लिए हैं।

Application का Android package `com.cerebrozen.app` और वर्तमान version `0.1.0` है। यह Android 8.0 (API 26) और उसके बाद के versions को support करता है।

## 2. मुख्य user flow

1. App launch पर branded splash screen दिखाई जाती है।
2. नया या signed-out user onboarding, consent और authentication flow से गुजरता है।
3. Login के बाद पाँच मुख्य tabs मिलते हैं: **Home, Sleep, Talk, Journal और You**।
4. इन tabs से user daily plan, insights, audio, wellness tools, games, goals, safety और settings जैसे secondary modules खोल सकता है।

## 3. Functional modules

### 3.1 Onboarding और Authentication

- Welcome, wellness disclosure, language और current-state selection
- Reset preference, consent और notification setup
- Email/password sign-up और sign-in
- Email OTP request और verification
- Forgot-password flow
- Google sign-in (client configuration उपलब्ध होने पर)
- Access token और refresh-token आधारित session management

### 3.2 Home / Today

- Daily home dashboard
- Mood check-in: mood, symbol, intensity और optional note
- Current streak और recent mood activity
- Context-based banners, जैसे sleep check-in और wind-down
- Daily plan और recommended content तक quick access
- Search और personalized journey presentation

### 3.3 Sleep

- Sleep diary/check-in
- Bedtime, wake time और sleep-quality entry
- Previous sleep logs और summary/insights
- Android Health Connect से last-night sleep prefill (permission मिलने पर)
- Sleep sounds, soundscape mixer और wind-down content
- CBT-I offline program

### 3.4 Talk / Conversational Support

- Backend-connected chat conversation
- Suggested starter prompts और suggestion chips
- Server-sent events आधारित streamed response support
- Chat response में tool/widget recommendations
- Voice capability: speech-to-text और text-to-speech backend endpoints
- Human support और crisis resources की navigation

### 3.5 Journal

- Journal entry create और history view
- Entry title/body और server-side risk indicator
- Private journal area
- Screen-lock/biometric protection
- Support card, ताकि sensitive entry के समय सहायता का रास्ता उपलब्ध रहे

### 3.6 You / Profile और Settings

- User profile और preferences
- Companion style selection
- Appearance: System, Night और Dawn themes
- Reminder configuration
- Privacy और consent controls
- Crisis region और human-support settings
- Premium information
- Privacy policy
- User-data export
- Account deletion

### 3.7 Daily Plan, Goals और Habits

- Personalized active daily plan
- Plan steps को complete/incomplete करना
- Plan regeneration
- Goals create और status update
- Goal decomposition
- Habits create और daily completion tracking
- Backend recommendations accept या dismiss करना

### 3.8 Insights और Patterns

- Weekly insights
- Mood, sleep और activity से प्राप्त patterns
- Learned/suggested observations
- User memory देखना, जोड़ना, edit करना और delete करना
- किसी unwanted pattern को suppress करना

### 3.9 Programs

- Available wellness programs की listing
- Program enroll/leave और active-program state
- Offline **CBT-I** sleep program
- Offline **MBCT** mindfulness-based cognitive therapy content

### 3.10 Audio, Sounds और Player

- Central audio player
- Ambient sound service और foreground soundscape service
- Multi-layer soundscape mixer, presets, volume और timer
- Smooth volume ramp/crossfade
- Tool ambience, chime और water-drop sounds
- Voice engine तथा cloud voice support

### 3.11 Breathing और Offline Guidance Tools

- Box breathing और two-minute reset
- Multiple paced breathing patterns और session history
- Guided imagery
- Body scan
- 5-4-3-2-1 style crisis grounding
- Insight reel
- CBT thought reframe
- TIPP distress-tolerance tool
- Baseline assessment

### 3.12 Toolkit और Mindful Games

Toolkit games और practical wellness exercises को एक hub में जोड़ता है।

> **अपडेट 2026-08-01:** games को 23 से घटाकर **12** कर दिया गया है। पहले 23 titles के पीछे सिर्फ़ 7 mechanics थीं और हर जवाब `round % n` से तय होता था — यानी कुछ भी random नहीं था, कुछ भी कठिन नहीं होता था, और 12 games असल में एक ही function थे। अब हर game की अपनी mechanic है (test इसे pin करता है), हर session seeded और अलग होता है, कठिनाई बढ़ती है (समय घटता है, memory span बढ़ता है), समय खत्म होना असली miss गिना जाता है, और हर game की अपनी synthesized आवाज़ है। Calm games (breathing, zen sand, still point) जानबूझकर बिना score के हैं। हटाए गए ids नए games पर redirect होते हैं, इसलिए पुराने shortcut काम करते रहते हैं।

अतिरिक्त interactive activities में Bubble Pop, Pattern Glow, Zen Ripples और Gratitude Garden शामिल हैं।

### 3.13 Safety और Crisis Support

- Personal safety plan create/update
- Trusted contact add, update और remove
- Crisis grounding और immediate coping tools
- Region-specific crisis resource selection
- Human-support information

> Safety content सहायता के लिए है; immediate danger में local emergency service या qualified professional से संपर्क करना चाहिए।

### 3.14 Search और Content Catalogue

- Served content catalogue में search
- Programs, sounds और wellness content discovery
- Content image और audio URLs
- Loading, empty, error और cached/fallback states

### 3.15 Notifications और Analytics

- **Remote push (FCM) — code पूरा, 2026-08-01।** Server per-device tokens रखता है (`/users/me/devices`),
  और नुज हर live install पर जाते हैं। App में यह तब तक निष्क्रिय रहता है जब तक `google-services.json`
  न हो — build और app दोनों उसके बिना भी सामान्य चलते हैं। चालू करने के लिए सिर्फ़ Firebase project +
  server-side `FCM_CREDENTIALS_PATH` चाहिए, नया app release नहीं।
- Local daily reminders
- Device reboot के बाद reminder rescheduling
- Consent-aware anonymous product analytics
- Onboarding और application events tracking

## 4. Technical modules और architecture

Gradle की दृष्टि से project में अभी **एक Android application module `:app`** है। Functional separation packages और source files के माध्यम से की गई है:

| Package/area | जिम्मेदारी |
|---|---|
| `MainActivity.kt` | App entry point, edge-to-edge UI, session/haptics initialization |
| `ui/CereBroApp.kt` | Splash, auth gate, five-tab navigation और complete route graph |
| `ui/screens/` | Main screens, settings, plan, insights, safety और tools |
| `ui/breathing/` | Breathing engine, rules, history और ViewModel |
| `ui/games/` | Mindful game registry और game screens |
| `ui/offline/` | Offline guidance तथा CBT-I/MBCT content |
| `ui/theme/` | Material 3 design tokens, colors, typography और themes |
| `net/` | API client, authentication session, caching, SSE और analytics |
| `audio/` | Player, audio services, soundscape mixer और voice features |
| `auth/` | Google Credential Manager integration |
| `health/` | Health Connect sleep integration |
| `notify/` | Reminders, notifications और boot receiver |

## 5. Technology stack

- Kotlin 2.0 और Java 17 target
- Jetpack Compose + Material 3 UI
- Navigation Compose
- Kotlin Coroutines
- DataStore preferences
- Android Security Crypto for encrypted token/cache storage
- Credential Manager for Google authentication
- Media3 ExoPlayer for soundscapes
- Coil for remote images
- AndroidX Biometric for journal lock
- Health Connect for sleep data
- `HttpURLConnection` + `org.json` आधारित lightweight API client
- JUnit, Robolectric और JaCoCo tests/coverage

## 6. Backend और data behavior

- Debug default API: `http://10.0.2.2:8000`
- Release API: `https://api.cerebrozen.in`
- Authenticated requests access token use करती हैं; refresh token rotation supported है।
- Offline response cache encrypted-at-rest रखा जाता है और network failure पर selected screens fallback/cache state दिखा सकती हैं।
- Mood, journal, sleep, chat, plans, goals, habits, insights, safety plan और profile backend APIs से जुड़े हैं।

## 7. Permissions और device integrations

Feature के अनुसार application notification, microphone/voice, biometric/device lock और Health Connect sleep permissions माँग सकती है। Integration configure या permission grant न होने पर संबंधित feature को graceful fallback के साथ काम करना चाहिए।

## 8. संक्षिप्त निष्कर्ष

CereBro एक modular wellness application है जिसका core पाँच tabs पर आधारित है। इसके प्रमुख pillars हैं: **daily emotional check-in, sleep support, conversational companion, private journaling, personalized planning, calming tools और safety support**। Codebase में UI, network, audio, health, notification और offline-content concerns अलग packages में रखे गए हैं, लेकिन build level पर फिलहाल एक ही `:app` module है।

## 9. इस application का उपयोग कौन करेगा और क्यों?

### प्रमुख users

| User group | वे इसका उपयोग क्यों करेंगे? |
|---|---|
| Stress, anxiety या overthinking महसूस करने वाले adults | Breathing, grounding, journaling और calming audio से तुरंत self-help पाने के लिए |
| अपनी emotional health track करने वाले लोग | Daily mood check-in, streak, journal और weekly patterns देखने के लिए |
| Sleep routine सुधारने वाले users | Sleep diary, Health Connect import, sleep insights, stories, soundscapes और CBT-I content के लिए |
| Guided self-care पसंद करने वाले users | Daily plan, goals, habits, MBCT और छोटे wellness exercises के लिए |
| किसी से private तरीके से बात करना चाहने वाले users | Conversational companion, reflection prompts और journal export के लिए |
| Meditation/breathing beginners | आसान paced breathing, guided imagery और body scan के लिए |
| Students और working professionals | Focus games, short resets, stress management और routine building के लिए |
| Existing CereBro web/iOS users | उसी account और shared backend data को Android पर access करने के लिए |

### यह application क्यों उपयोगी हो सकता है?

- एक ही जगह mood, sleep, journal, habits और wellness tools मिलते हैं।
- छोटे exercises user को भारी course के बिना तुरंत शुरू करने देते हैं।
- Personalized plan और insights raw entries को actionable steps में बदल सकते हैं।
- Offline grounding, breathing, CBT-I और MBCT content weak network में भी उपयोगी है।
- Journal lock, consent controls, data export और account deletion user control बढ़ाते हैं।
- Crisis और trusted-contact paths कठिन समय में सहायता तक पहुँच आसान बनाते हैं।

### किसके लिए यह पर्याप्त नहीं है?

यह psychiatrist, psychologist, counsellor, diagnosis, medication advice या emergency response का replacement नहीं है। Severe symptoms, self-harm risk या immediate danger में qualified professional और local emergency/crisis service जरूरी है।

## 10. Module completion status

यह status **current source code review** पर आधारित है। “Code-complete” का अर्थ है कि UI, logic और expected API/local persistence wiring मौजूद है; इसका अर्थ यह नहीं कि हर production device और live backend पर final acceptance testing हो चुकी है।

### 10.1 Code-complete / मुख्य flow उपलब्ध

| Module | वर्तमान evidence | Status |
|---|---|---|
| Splash, onboarding और email authentication | Sign-up, sign-in, OTP, forgot password, consent और session refresh implemented | Code-complete |
| Home / Today | Profile, streak, moods, plan, program, banners, add/delete mood APIs wired | Code-complete |
| Sleep diary | Logs, summary, create sleep entry और history implemented | Code-complete |
| Text Talk/chat | Chat history, starters, send, SSE streaming, limits और tool widgets wired | Code-complete; backend required |
| Journal | Create/history/private flow, risk field और biometric/device lock present | Code-complete |
| Daily Plan | Active plan, toggle step और regenerate APIs present | Code-complete |
| Goals और Habits | Create/update/decompose और daily completion APIs present | Code-complete |
| Patterns, memories और recommendations | Read/add/edit/delete/accept/dismiss flows wired | Code-complete |
| Programs | Catalogue, enrollment, active journey, day path और leave flow present | Code-complete; server content required |
| Safety Plan | Load/save, cached offline display और stale-state indication present | Code-complete |
| Trusted Contact | Load, add/update और delete APIs present | Code-complete |
| Breathing और offline guidance | Breathing engine/history, guided imagery, body scan, grounding, CBT-I और MBCT present | Code-complete/local |
| Mindful games और interactive tools | Registry और playable mechanics/screens present | Code-complete/local |
| Appearance, privacy और consent | Theme persistence, consent read/update और journal-lock setting present | Code-complete |
| Reminders | Local AlarmManager notification, permission और reboot reschedule implemented | Code-complete; device QA required |
| Data export और account deletion | Backend calls, progress/error और confirmation behavior present | Code-complete; backend required |

### 10.2 Partial या configuration-dependent modules

| Module | क्या बाकी/निर्भर है? | Status |
|---|---|---|
| Google Sign-In | Code मौजूद है, लेकिन valid Google web client ID और Play Services configuration के बिना inactive रहेगा | Configuration-dependent |
| Voice Talk | On-device STT/TTS fallback मौजूद; cloud STT/TTS server capability और microphone permission पर निर्भर | Partial/conditional |
| Health Connect sleep import | Read flow मौजूद; Health Connect availability, install और user permission जरूरी | Conditional |
| Audio library और sleep stories | Player/mixer implemented, लेकिन कुछ items के actual media URLs/narration backend catalogue पर निर्भर हैं | Partial/content-dependent |
| Insights | Weekly API और baseline UI मौजूद; useful result पर्याप्त real user data और backend analysis पर निर्भर है | Data-dependent |
| Search/content catalogue | Multiple content APIs wired; completeness backend में published catalogue पर निर्भर है | Content-dependent |
| Offline behavior | **अपडेट 2026-08-01:** mood check-in और journal entry अब असली durable queue में जाते हैं (encrypted storage, per-item idempotency key, क्रम बनाए रखते हुए drain, offline undo सहित)। बाकी write actions अभी queue में नहीं हैं | Partial (core writes पूरे) |
| Hindi localization | बड़ी translation file मौजूद, लेकिन safety/clinical strings का English fallback और professional review pending है | Partial |
| Human Support | Real helpline/website links मौजूद, पर vetted coach directory roadmap में है | Partial |
| Privacy/clinical credibility | In-app disclosure मौजूद, लेकिन formal clinical advisory/review process roadmap में है | Partial |

### 10.3 स्पष्ट रूप से अधूरा module

| Module | Evidence | Status |
|---|---|---|
| Premium subscription | Plans और prices का UI है, लेकिन purchase CTA disabled है और Google Play Billing dependency/flow नहीं है | अधूरा |

### 10.4 “पूरा चल रहा है” प्रमाणित करने से पहले जरूरी QA

- Production backend के साथ सभी API flows का end-to-end test
- Real Android devices पर biometric, microphone, notification और Health Connect test
- Background audio, lock screen controls, timer और OEM battery restrictions test
- Poor network, expired token और offline-to-online recovery test
- Hindi content, विशेषकर crisis और clinical language, का human review
- Accessibility, TalkBack, large font और low-end device performance test
- Play Store privacy/data-safety और security review

## 11. Application में आगे क्या add किया जा सकता है?

### Priority 1 — launch से पहले

1. **Google Play Billing:** premium purchase, restore purchase, subscription status और server verification।
2. **Complete offline sync:** Room database, pending-action queue, conflict handling और reconnect sync।
3. **Clinical और safety review:** qualified mental-health professionals द्वारा content validation और escalation policy।
4. **Hindi localization review:** crisis, consent और clinical strings की verified translation; बाद में अन्य Indian languages।
5. **Security hardening:** penetration test, certificate pinning decision, sensitive-log audit और token migration/recovery tests।
6. **Crash/performance monitoring:** privacy-safe crash reports, ANR और audio failure monitoring।

### Priority 2 — user value बढ़ाने के लिए

1. **Personalized dashboard:** mood/sleep trends, correlations और user-controlled weekly/monthly charts।
2. **Flexible reminders:** custom time, multiple schedules, sleep reminder, habit reminder और quiet hours।
3. **Journal improvements:** tags, search, filters, edit/delete entry, attachments और encrypted local drafts।
4. **Sleep improvements:** smart bedtime routine, sleep debt/trend, wearable integrations और more offline audio downloads।
5. **Goals/habits calendar:** streak calendar, weekly targets और missed-day recovery।
6. **Professional care bridge:** verified therapist/coach directory, appointment booking और user-consented report sharing।
7. **Emergency enhancements:** quick-call button, location-aware verified resources और user-tested safety-plan export।
8. **Accessibility:** font scaling audit, voice navigation, captions/transcripts, color-blind checks और switch access।

### Priority 3 — engagement और platform growth

1. Android home-screen widgets for mood check-in, breathing और daily plan।
2. Wear OS quick check-in और breathing session।
3. Secure cross-device sync और web/iOS continuity।
4. Download manager for offline programs, stories और soundscapes।
5. Progress reports जिन्हें user PDF/CSV में export कर सके।
6. Carefully designed, opt-in community/group challenges—बिना public mental-health data expose किए।
7. Admin/content-management portal ताकि reviewed programs और media safely publish किए जा सकें।

## 12. सुझाया गया development order

पहले **Premium Billing + offline sync + clinical/localization review + real-device QA** पूरा करना चाहिए। उसके बाद journal/search, flexible reminders और analytics charts जैसे improvements करने चाहिए। Therapist marketplace, community या wearable जैसी बड़ी सुविधाएँ core safety, privacy और reliability stable होने के बाद जोड़ना बेहतर होगा।
