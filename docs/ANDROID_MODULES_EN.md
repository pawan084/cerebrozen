# CereBro Android Application — Product and Module Document

## 1. What is CereBro?

**CereBro** is a native Android mental-wellness and self-care application. It helps adults understand and record their emotional state, improve sleep routines, write private journal entries, follow guided calming exercises, receive conversational support, and work through a personalized wellness plan.

The application is not a replacement for clinical diagnosis, therapy, medication advice, or emergency services. Its crisis and safety features are intended to provide grounding tools and quicker access to trusted contacts and human-support resources.

Android package: `com.cerebrozen.app`  
Current version: `0.1.0`  
Minimum Android version: Android 8.0 / API 26

## 2. Intended users and reasons to use the app

| User group | Why they may use CereBro |
|---|---|
| Adults experiencing stress, anxiety, or overthinking | Immediate breathing, grounding, journaling, and calming-audio tools |
| People tracking emotional well-being | Daily mood entries, streaks, journal history, and weekly patterns |
| People improving their sleep routine | Sleep diary, Health Connect import, insights, soundscapes, stories, and CBT-I content |
| Users who prefer guided self-care | Daily plans, goals, habits, MBCT, and short wellness exercises |
| People wanting a private space to reflect | Conversational companion, reflection prompts, protected journal, and data export |
| Meditation and breathing beginners | Simple paced breathing, guided imagery, and body-scan exercises |
| Students and working professionals | Focus games, short resets, stress management, and routine building |
| Existing CereBro web or iOS users | Access to the same account and shared backend data on Android |

## 3. Main user journey

1. A branded splash screen appears on launch.
2. A new or signed-out user completes onboarding, disclosure, consent, and authentication.
3. A signed-in user sees five primary tabs: **Home, Sleep, Talk, Journal, and You**.
4. Secondary screens provide daily plans, insights, content search, audio, programs, goals, safety features, wellness tools, games, and settings.

## 4. Functional modules

### 4.1 Onboarding and authentication

- Welcome, wellness disclosure, language, and current-state selection
- Consent and notification choices
- Email/password registration and sign-in
- Email OTP request and verification
- Password recovery
- Google Sign-In when a valid client configuration is supplied
- Access-token and rotating refresh-token session management

### 4.2 Home / Today

- Daily dashboard and contextual greeting
- Mood, symbol, intensity, and optional-note check-in
- Current streak and recent mood activity
- Sleep-check-in, wind-down, offline, and active-program banners
- Personalized plan, recommendations, content, and search access

### 4.3 Sleep

- Bedtime, wake time, and sleep-quality diary
- Sleep history and summary
- Optional last-night prefill from Android Health Connect
- Sleep stories, ambient sounds, and a soundscape mixer
- Offline CBT-I program

### 4.4 Talk / conversational support

- Backend-connected conversation history and text chat
- Starter prompts and suggested actions
- Streamed responses using server-sent events
- Tool and navigation widgets inside responses
- Optional speech-to-text and text-to-speech
- Human-support and crisis-resource routes

### 4.5 Journal

- Create and browse journal entries
- Private journal area and server-provided risk indicator
- Screen-lock or biometric protection
- Support card for sensitive entries

### 4.6 Profile and settings

- Profile and companion-style preferences
- System, Night, and Dawn appearance modes
- Daily reminders
- Privacy, consent, and journal-lock controls
- Crisis region and human-support information
- Premium information
- Privacy policy, data export, and account deletion

### 4.7 Plans, goals, and habits

- Personalized active daily plan
- Complete or reopen individual plan steps
- Regenerate a plan
- Create and update goals
- Break goals into smaller steps
- Create habits and track daily completion
- Accept or dismiss backend recommendations

### 4.8 Insights and patterns

- Weekly insights and a local baseline
- Learned and suggested behavioral patterns
- View, add, edit, and delete user memories
- Suppress unwanted patterns

### 4.9 Programs

- Program catalogue and enrollment
- Active-program status and day-by-day journey
- Leave an active program
- Offline CBT-I and MBCT content

### 4.10 Audio and player

- Central audio player and lock-screen media controls
- Foreground ambient-audio and soundscape services
- Four-layer soundscape mixer, presets, volume, and timer
- Smooth volume ramps and crossfades
- Chimes, water-drop sound, tool ambience, and voice output

### 4.11 Breathing and offline guidance

- Box breathing, paced patterns, and a two-minute reset
- Breathing-session history
- Guided imagery and body scan
- Crisis grounding
- CBT reframing, TIPP, baseline assessment, CBT-I, and MBCT

### 4.12 Toolkit and mindful games

The toolkit combines wellness exercises and games. It includes focus, memory, resilience, flexibility, and calm categories, with approximately 23 game definitions. Additional activities include Bubble Pop, Pattern Glow, Zen Ripples, and Gratitude Garden.

### 4.13 Safety and crisis support

- Create and update a personal safety plan
- Add, update, or remove a trusted contact
- Crisis-grounding and immediate coping tools
- Region-specific resources and human-support links

### 4.14 Search, reminders, and analytics

- Search the backend content catalogue
- Loading, empty, error, cached, and fallback states
- Local daily notifications with reboot rescheduling
- Consent-aware anonymous product events

## 5. Technical architecture

The project currently contains one Gradle application module, `:app`. Functional responsibilities are separated through packages:

| Package or file | Responsibility |
|---|---|
| `MainActivity.kt` | Entry point, edge-to-edge UI, session and haptics initialization |
| `ui/CereBroApp.kt` | Splash, authentication gate, five-tab navigation, and route graph |
| `ui/screens/` | Main screens, settings, plans, insights, safety, and tools |
| `ui/breathing/` | Breathing rules, engine, state, and history |
| `ui/games/` | Mindful-game registry and game screens |
| `ui/offline/` | Offline guidance, CBT-I, and MBCT content |
| `ui/theme/` | Material 3 colors, typography, tokens, and themes |
| `net/` | API client, authentication, cache, SSE, and analytics |
| `audio/` | Player, media services, mixer, and voice features |
| `auth/` | Google Credential Manager integration |
| `health/` | Health Connect sleep integration |
| `notify/` | Notifications, alarms, and reboot receiver |

## 6. Technology stack

- Kotlin 2.0, Java 17, Jetpack Compose, and Material 3
- Navigation Compose and Kotlin Coroutines
- DataStore preferences and Android Security Crypto
- Credential Manager for Google authentication
- Media3 ExoPlayer for soundscapes
- Coil for remote images
- AndroidX Biometric and Health Connect
- Lightweight `HttpURLConnection` and `org.json` API layer
- JUnit, Robolectric, and JaCoCo

## 7. Current implementation status

This classification is based on source-code review. “Code-complete” means that UI, logic, and expected API or local-storage wiring are present. It does not mean that final acceptance testing has passed on every device and the production backend.

### 7.1 Code-complete core flows

| Module | Current evidence | Status |
|---|---|---|
| Splash, onboarding, and email authentication | Registration, sign-in, OTP, recovery, consent, and token refresh are wired | Code-complete |
| Home / Today | Profile, streak, mood, plan, program, banners, and add/delete APIs | Code-complete |
| Sleep diary | Summary, logs, create entry, and history | Code-complete |
| Text chat | History, starters, send, streaming, usage limits, and tool widgets | Code-complete; backend required |
| Journal | Create, history, private flow, and biometric/device lock | Code-complete |
| Daily plan, goals, and habits | Read, create, update, decompose, toggle, and regenerate flows | Code-complete |
| Patterns, memories, and recommendations | Read, add, edit, delete, accept, and dismiss | Code-complete |
| Programs | Catalogue, enrollment, active journey, path, and leave action | Code-complete; content required |
| Safety plan and trusted contact | Read, save, cached display, add, update, and remove | Code-complete |
| Breathing, offline guidance, and games | Local engines, content, history, and interactive screens | Code-complete/local |
| Appearance, consent, and reminders | Persisted settings and local notification scheduling | Code-complete; device QA required |
| Data export and account deletion | Backend requests, confirmation, progress, and errors | Code-complete; backend required |

### 7.2 Partial or configuration-dependent areas

| Module | Remaining dependency or limitation | Status |
|---|---|---|
| Google Sign-In | Requires a valid web client ID and Play Services configuration | Configuration-dependent |
| Voice Talk | Has local fallback; cloud STT/TTS requires server support and microphone permission | Conditional |
| Health Connect import | Requires availability, installation, and user permission | Conditional |
| Audio and sleep stories | Some media and narration depend on backend catalogue URLs | Content-dependent |
| Insights | Value depends on sufficient user history and backend analysis | Data-dependent |
| Search and content catalogue | Completeness depends on published backend content | Content-dependent |
| Offline operation | Cached reads exist, but there is no complete offline mutation queue and conflict sync | Partial |
| Hindi localization | Many strings exist, but safety/clinical text and professional review remain pending | Partial |
| Human support | Real helplines exist; a vetted coach directory is still a roadmap item | Partial |
| Clinical credibility | Disclosures exist; a formal clinical advisory process remains pending | Partial |

### 7.3 Clearly incomplete area

| Module | Evidence | Status |
|---|---|---|
| Premium subscription | Pricing UI exists, but the purchase button is disabled and Google Play Billing is not integrated | Incomplete |

## 8. Recommended additions

### Priority 1 — before a production launch

1. Integrate Google Play Billing, purchase restoration, entitlement status, and server verification.
2. Add Room-based offline storage, a pending-action queue, conflict handling, and reconnect synchronization.
3. Complete professional clinical and safety review.
4. Complete reviewed Hindi safety/consent translations, followed by other Indian languages.
5. Perform security hardening, privacy review, real-device QA, accessibility testing, and performance monitoring.
6. Add privacy-safe crash and ANR monitoring.

### Priority 2 — increase user value

1. Mood and sleep trend charts with weekly and monthly views.
2. Custom reminder times, multiple schedules, quiet hours, and habit reminders.
3. Journal tags, search, filters, editing, deletion, attachments, and encrypted local drafts.
4. Sleep trends, bedtime routines, wearable integrations, and offline media downloads.
5. Habit calendars, weekly goals, and missed-day recovery.
6. A verified professional-care directory and consent-controlled report sharing.
7. Improved location-aware emergency resources and safety-plan export.
8. A full accessibility audit including large text, TalkBack, captions, color contrast, and switch access.

### Priority 3 — platform growth

1. Android home-screen widgets for mood, breathing, and the daily plan.
2. Wear OS quick check-ins and breathing sessions.
3. Secure cross-device continuity across Android, web, and iOS.
4. Offline download manager for programs, stories, and soundscapes.
5. User-controlled PDF and CSV progress reports.
6. Carefully designed opt-in group challenges without exposing mental-health data.
7. A reviewed content-management portal.

## 9. Recommended delivery order

The recommended first phase is **billing, reliable offline sync, clinical/localization review, and real-device quality assurance**. Journal/search improvements, flexible reminders, and trend dashboards should follow. Larger features such as a therapist marketplace, community functions, and wearable support should be considered only after the privacy, safety, and reliability foundations are stable.
