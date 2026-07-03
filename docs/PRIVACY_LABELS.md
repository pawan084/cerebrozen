# CereBro — App Store Privacy Nutrition Labels

What to enter in App Store Connect → App Privacy. Derived from what the app
actually collects. "Linked to you" applies only when the user signs in (Apple /
Google / email); a fully anonymous local user has **no** data collection.

## Summary
- **Data used to track you:** None. CereBro has no advertising or third-party
  analytics SDKs and does not track users across apps/sites.
- **Data linked to you:** only when signed in, for app functionality (below).
- **Data not collected:** location, contacts, browsing history, search history,
  identifiers for advertising, purchases history beyond subscription status.

## Data types collected (signed-in accounts)

| Data type | Collected | Linked | Tracking | Purpose |
|---|---|---|---|---|
| Email address | Yes | Yes | No | App Functionality (account) |
| Name | Optional | Yes | No | App Functionality (personalization) |
| Health & Fitness (mood check-ins, wellness journal, sleep diary; optional opt-in Apple Health sleep read — pre-fill only, never written back, never shared) | Yes | Yes | No | App Functionality |
| User Content (journal text, chat/voice transcripts) | Yes | Yes | No | App Functionality |
| Audio Data (voice recording) | Optional | Yes | No | App Functionality (speech-to-text; not stored unless voice storage consent is on) |
| Sensitive Info (mental-health themes in content) | Yes | Yes | No | App Functionality |
| Coarse region (country, for crisis resources) | Yes | Yes | No | App Functionality |
| Purchases (subscription tier) | Yes | Yes | No | App Functionality |
| Crash / performance data | No | — | — | — |

Notes for the questionnaire:
- **Third-party AI processors:** transcripts/text may be sent to AI sub-processors
  (LLM, speech-to-text, text-to-speech) to generate responses. Disclosed in the
  in-app privacy policy. These are processors for app functionality, not tracking.
- **Consent gating:** AI memory, voice storage, and model-training are explicit,
  off-by-default where sensitive, and enforced server-side (AI memory off drops
  long-term recall). Voice audio is transcribed and discarded unless the user
  turns on voice storage.
- **Data deletion / export:** in-app account deletion cascades all server data;
  full export is available (GDPR-style portability). Required by App Store 5.1.1(v).

## Age rating
17+ — the app gates to 18+ at first launch and records the attestation; mental-
health themes and unrestricted web access to crisis resources.
