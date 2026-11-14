# Implementation status — Personal AI Job Hunter

Branch: `feature/multi-country-visa-job-hunter`

This document distinguishes code present in the branch from checks that still require a successful build or real-device test. A source file or UI control is not treated as verified merely because it exists.

## Phase 1 — Live jobs, employer verification and contract review

### Implemented in source
- Country registry for New Zealand, Australia, United Kingdom, Germany and Canada.
- Shared public-feed adapters: Arbeitnow, Remotive, Himalayas and Jobicy; optional company-specific Greenhouse and Lever public boards.
- Shared vacancy freshness policy removes expired listings and deduplicates stable job IDs before UI display and scheduled saving.
- NZ job-board browser hand-off links: SEEK NZ, Tradestaff, Trade Assist and Enterprise Recruitment. These are official/source-site links, not claimed API integrations.
- NZ public accredited-employer lookup using the search endpoint currently used by Immigration New Zealand's own public employer-search page.
- Exact 13-digit NZBN matching, accreditation-expiry parsing, exact-name results requiring NZBN confirmation, checked timestamp and official-source link.
- Conservative no-result handling: a missing public result is inconclusive because some accredited employers opt out of publication.
- AEWV contract text checklist: identity, role, work location, duties, hours, pay, pay period, overtime, leave, duration, missing fields, trial-period wording, potential unlawful deductions, bonding/repayment clauses, worker-paid job fees, passport-retention wording and employer/job-title/location/pay mismatches.
- Matching synonyms for civil labour, roadworks, plant operation, painting, maintenance, transport/logistics and scaffolding.
- Unit tests for NZ employer result parsing, no-result responses, invalid NZBN input and contract risk rules.

### Important verification boundaries
- The INZ search endpoint is used by the official public search page but is not a documented public API. It may change; the app links users back to the official INZ page for confirmation.
- An employer's accreditation does not establish that a particular vacancy has an approved job check.
- INZ does not provide a public worker-facing job-check lookup by vacancy. The app therefore marks job-check approval as requiring employer evidence and official confirmation; it never fabricates a verified state.
- A name-only employer result requires NZBN confirmation. A missing public result is not proof of non-accreditation.
- SEEK NZ, Tradestaff, Trade Assist and Enterprise Recruitment currently use browser hand-off. No login/CAPTCHA scraping or access-control bypass is used.
- Contract analysis is a deterministic first-pass checklist, not legal advice or a visa decision. Market-rate comparison and job-check approval remain evidence-based manual checks.

## Phase 2 — PDF and application assistant

### Implemented in source
- Encrypted private document vault using Android Keystore AES-GCM.
- PDF, DOCX and TXT text extraction stored in encrypted profile data.
- Local specialist agents for CV analysis, job matching, CV tailoring, cover letters, employer outreach, interview preparation and official-source explanation.
- PDF export for user-reviewed local-agent drafts, using Android's document-creation flow.
- Extracted employment-agreement text can be passed to the AEWV contract checklist.

### Limitations
- Scanned PDF/image OCR is not enabled; files are stored securely but OCR text extraction is not available.
- Local AI requires the user to download and verify the model on-device. Model quality, RAM use, thermal behaviour and Urdu/Roman Urdu output require real-device testing.
- AI output is a draft only. The user must verify every statement and approve every application or message.

## Phase 3 — UI and Android APK

### Implemented in source
- Native Android Kotlin/Jetpack Compose application with Home, Jobs, Files, Tracker and Local AI sections.
- Dark theme with gold accents, private profile, document vault, job discovery, tracker and interview reminders.
- GitHub Actions workflow builds ARM64 debug and minified release variants and uploads the debug APK artifact.
- R8 rule added for PDFBox's optional JPEG2000 decoder reference.

### Release gates not yet claimed complete
- Latest CI run must finish green with all unit tests and both Android variants built.
- Debug APK artifact must be present and downloaded/installed on a physical Android device.
- Real-device checks are required for document import/export, notifications, model download/inference, RAM, battery and thermal behaviour.
- A user-owned release signing key is required for a distributable signed release APK. Never commit the keystore or passwords.

## Latest verification

- GitHub Actions run `43601268058` completed successfully for commit `b402bd5cdb1c081f02cbd4a10739f84746b6714e`.
- ARM64 debug APK assembly and unit tests: passed.
- Live public API and model endpoint smoke checks: passed.
- Debug APK artifact `hamari-mehnat-debug-apk` uploaded (about 28.3 MB).
- Subsequent Phase 2 hardening commits are being revalidated by the latest CI run; do not treat the earlier successful run as validation of later commits.

## Phase 2 work started after Phase 1 CI passed

- Bound DOCX ZIP entry count and expanded document XML size to reduce ZIP-bomb/resource-exhaustion risk; disabled XML document type declarations.
- Sanitized imported filename/MIME metadata and ensured interrupted vault writes remove temporary files.
- Improved PDF export to wrap unusually long unbroken tokens rather than clipping them.
- Added expected employer, job title, work location and advertised hourly pay fields to the imported agreement review screen so contract text can be cross-checked against the vacancy.

## Phase 3

No new Phase 3 implementation is authorized yet. Existing navigation and UI remain in place. Await the user's review before adding Phase 3 features.

## Next acceptance gate

Run Android CI against the latest branch head. Require ARM64 debug assembly, all unit tests, live smoke checks and artifact upload to pass. Then inspect the APK artifact and provide the user the verified artifact link. Real-device QA remains required for model inference, document picker/import/export, and notifications.
