# Hamari Mehnat — Personal AI Job Hunter (Android-only V1)

A private, single-owner Android application for international job discovery and application preparation.

## Product boundary

- Android application only. No website, web dashboard, public registration, multi-user SaaS, subscription or cloud AI.
- Personal profile, CV text and imported documents stay in app-private storage. Profile and document content are encrypted with Android Keystore AES-GCM.
- Local AI runs through the pinned llama.cpp native runtime. The GGUF model is downloaded separately by the user and SHA-256 verified before it can be loaded.
- Internet is used only for public job feeds, official government source research, and the initial model download. Local documents, cached profile, saved jobs, tracker, reminders and an installed local model remain available offline.
- Applications and employer messages are never submitted automatically. The user reviews and confirms every draft/action.

## Implemented modules

- **Profile:** encrypted owner profile with target roles, countries, skills and experience summary.
- **Document vault:** PDF, DOCX, TXT and image import into app-private AES-GCM encrypted files. PDF/DOCX/TXT text extraction is local. Scanned-PDF/image OCR is not enabled.
- **Application PDF export:** local CV-tailoring and cover-letter drafts can be exported to a user-selected PDF location after review.
- **Job discovery:** Arbeitnow, Remotive, Himalayas, Jobicy, Greenhouse public company boards and Lever public company boards. LinkedIn, Indeed, SEEK and EURES are browser hand-off links; login-protected pages are not scraped.
- **Source compliance:** rate limits for public APIs, direct source/application URLs, source attribution, expiry filtering for Himalayas listings and no onward job-feed redistribution.
- **Matching:** deterministic role/skill/country relevance scoring with cautious sponsorship wording. A sponsorship signal is not a guarantee of sponsorship or visa eligibility.
- **Local specialist agents:** CV analyst, job-match analyst, CV tailoring, cover-letter drafting, employer outreach drafting, interview coaching and official-source visa explanation. These are prompt-specialized workflows using one local model; the app does not claim to train a foundation model from personal documents.
- **Employer outreach:** browser-based employer research and user-reviewed email drafts opened in the user's email app. The app does not send automatically or read the user's inbox.
- **Tracker:** saved jobs, application stages, employer contact/response notes and local interview history.
- **Interview reminders:** local notification scheduled one hour before the interview in the entered time zone.
- **Visa/work-permit research:** fetches bounded excerpts from registered official government pages, records retrieval timestamps and stores the evidence locally for the visa specialist agent.
- **New Zealand employer checks:** searches the public accredited-employer register endpoint used by Immigration New Zealand's own search page, checks exact NZBN matches and published accreditation expiry, and links back to INZ. Name-only matches require NZBN confirmation; no public match is inconclusive because some employers opt out of publication.
- **AEWV agreement review:** checks pasted or extracted contract text for missing core terms, weekly hours, pay, trial-period wording, employer/job-title mismatches and common exploitation red flags. This is a first-pass checklist, not legal advice.
- **Scheduled discovery:** WorkManager runs every 12 hours only while network connectivity is available, rotates configured target roles and stores matching vacancies locally.

## Multi-country support

The Android core is country-neutral. Immigration requirements and official verification entry points are registered as country modules rather than embedded in the shared matcher.

Initial country modules: New Zealand (AEWV), Australia (employer-sponsored skilled pathways), United Kingdom (Skilled Worker), Germany (qualified-professional routes), and Canada (employer-specific permits / LMIA or exemption). These are official research starting points, not visa decisions.

To add a country, register its ISO country code, official immigration authority, work route, employer verification method and official sources in CountryRegistry. Add a dedicated policy/source adapter and fixture-based tests only where local rules require it. Shared profile, CV, matching, tracker, encrypted storage and navigation remain reusable. See docs/MULTI_COUNTRY_ARCHITECTURE.md.

Country-specific sponsorship wording is triage only. Employer accreditation/licensing does not establish that a particular job can sponsor a particular applicant. Verify current rules and job-specific conditions at official sources.

## Job source notes

- Remotive listings are delayed by 24 hours; the app caps its public API use at four requests per UTC day and enforces a six-hour interval.
- Himalayas job data is refreshed daily; the app limits requests to one per UTC day, retains source attribution and uses the API-provided applicationLink for the application action.
- Jobicy public API use is limited to four requests per day with at least one hour between calls.
- Greenhouse and Lever endpoints are company-board-specific. Enter board IDs/site names in the Jobs tab; they are not global job-search APIs.
- LinkedIn, Indeed and SEEK do not provide an unrestricted public API for all logged-in listings. The app opens their official search pages in a browser rather than bypassing access controls.
- For New Zealand, SEEK NZ, Tradestaff, Trade Assist and Enterprise Recruitment are browser hand-off sources. They are not represented as live API integrations.

## Local AI model

Primary benchmark candidate:
- LiquidAI LFM2.5-1.2B-Instruct QAD Q4_0 GGUF.
- About 696 MB (695,755,488 bytes).
- QAD means quantization-aware distillation; it is the teacher-distilled checkpoint requested.
- Hugging Face revision is pinned; the app verifies exact byte size and SHA-256 before loading.
- LFM Open License v1.0 applies. Review its terms before redistributing the model; the app downloads the artifact directly to the user's private device storage.
- The model's published language list does not include Urdu. English, Urdu and Roman Urdu must be benchmarked on the target phone; quality is not assumed.
- The model is not bundled into the APK and no user documents are used to fine-tune its weights.

Runtime: upstream llama.cpp pinned as a Git submodule at vendor/llama.cpp, built for Android ARM64 with NDK/CMake.

## Build

Requirements:
- JDK 17
- Android SDK Platform 35
- Android NDK 27.0.12077973
- CMake 3.31.6
- Gradle 8.10.2

Build and test:

~~~bash
gradle --no-daemon assembleDebug assembleRelease testDebugUnitTest
~~~

The debug APK is installable and is the CI artifact. The release variant is built to verify R8/minification and JNI keep rules; it is not signed with a user-owned release key.

## CI verification

GitHub Actions:
- Checks out the pinned llama.cpp submodule.
- Builds debug and minified release variants.
- Runs Kotlin unit tests for matching, agent prompt safety, public job API adapters and official visa page extraction.
- Runs live public endpoint smoke checks for job APIs and the pinned Hugging Face model metadata endpoint. The model binary is not downloaded in CI.
- Uploads the debug APK as a workflow artifact.

A successful CI build does not replace a real-device test of model download, RAM, speed, battery, thermal behaviour, notification permission and Urdu/Roman Urdu output.

## Privacy and security

- Never commit CVs, passports, certificates, personal profile data, model weights or credentials.
- No API keys are required for the included public feeds.
- No analytics SDK or cloud AI provider is included.
- Android backup is disabled and cleartext HTTP is disabled.
- Personal documents are encrypted at rest in app-private storage.
- The repository currently contains application source only. Change repository visibility to Private before adding any private project materials or secrets.

## Existing prototype

The original Python/Streamlit files are retained temporarily as migration reference only. They are not the Android app runtime and no web application is part of V1.
