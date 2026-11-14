# Implementation status — Personal AI Job Hunter

Branch: `feature/multi-country-visa-job-hunter`

This document separates source implementation, automated verification, and real-device acceptance. A successful cloud build does not prove behaviour on a physical phone.

## Phase 1 — Live jobs, employer verification and contract review

### Implemented
- Country registry for New Zealand, Australia, United Kingdom, Germany and Canada.
- Public job-feed adapters: Arbeitnow, Remotive, Himalayas and Jobicy; optional company-specific Greenhouse and Lever boards.
- Vacancy freshness filtering and stable-ID deduplication before display and scheduled saving.
- Browser hand-off links for SEEK NZ, Tradestaff, Trade Assist and Enterprise Recruitment; these are not represented as API integrations.
- New Zealand public accredited-employer lookup, exact 13-digit NZBN matching, accreditation expiry parsing, checked timestamp and official-source link.
- Conservative no-result handling: absence from the public list is inconclusive because some employers opt out of publication.
- AEWV contract checklist covering identity, role, location, duties, hours, pay, leave, duration, trial-period wording, deductions, bonding/repayment, worker-paid fees, passport retention and mismatches against expected vacancy details.
- Role synonyms for civil labour, roadworks, plant operation, painting, maintenance, logistics and scaffolding.
- Unit tests for employer parsing, invalid NZBN, no-result handling, contract rules, freshness filtering, job matching and public source adapters.

### Verification boundaries
- The INZ employer-search endpoint is used by the official public search page but is not a documented public API; it may change.
- Employer accreditation does not prove a specific vacancy has an approved Job Check.
- INZ does not expose a public worker-facing Job Check lookup by vacancy. Job Check approval must be confirmed from employer evidence and official channels.
- Name-only employer matches require NZBN confirmation. No public match is not proof of non-accreditation.
- Job-board hand-off links open the original site. Login/CAPTCHA scraping and access-control bypass are not used.
- Contract analysis is a first-pass checklist, not legal advice or a visa decision.

## Phase 2 — PDF and application assistant

### Implemented
- Private document vault encrypted with Android Keystore AES-GCM.
- PDF, DOCX and TXT local text extraction; files up to 25 MB and extracted text capped at 50,000 characters.
- DOCX ZIP-entry and expanded XML limits, disabled document type declarations, and safe extraction bounds.
- Imported filename and MIME metadata sanitization; temporary encrypted files are cleaned up after failed/interrupted writes.
- Local specialist workflows for CV analysis, job matching, CV tailoring, cover letters, employer outreach, interview preparation and official-source explanation.
- User-reviewed application draft export to multi-page PDF, including long-token wrapping and page footers.
- Contract review can compare extracted agreement text with expected employer, job title, location and advertised hourly pay.
- Local model download checks expected byte size and SHA-256, caps overlong downloads and cleans partial files.

### Limitations
- OCR for scanned PDFs and images is not enabled.
- The local GGUF model is downloaded separately; it is not bundled into the APK. Urdu/Roman Urdu quality, RAM, speed, battery and thermal performance require phone testing.
- AI output is a draft only. The user reviews all details and manually approves applications and messages.

## Phase 3 — UI and Android APK

### Implemented
- Native Android Kotlin / Jetpack Compose UI with Home, Jobs, Files, Tracker and Local AI navigation.
- Dark visual theme with gold accents, private profile, document management, job search, application tracking and interview reminders.
- UI status distinguishes an integrated local AI runtime from a model that has actually been downloaded.
- Deprecated Compose divider calls replaced with current `HorizontalDivider` API.
- GitHub Actions builds ARM64 debug and minified release variants, runs unit tests and public endpoint smoke checks, and uploads the installable debug APK.
- CI smoke checks are gating: a failed public endpoint/model metadata check fails the workflow rather than being silently ignored.

### Automated verification — latest Phase 1/2/3 head
- Previously verified source commit: `6faeebe311ad42fec30fb7bb539927b1cec13ee3`.
- GitHub Actions run: https://github.com/haseebop668-a11y/hamari-mehnat-ai/actions/runs/43603338348.
- Result: successful.
- `assembleDebug`, `assembleRelease`, and `testDebugUnitTest`: passed.
- Release minification/R8 and release packaging: passed.
- Live smoke checks: Arbeitnow HTTP 200 (300 items), Remotive HTTP 200 (16), Himalayas HTTP 200 (20), Jobicy HTTP 200 (5), Hugging Face model metadata HTTP 200 and model binary range request HTTP 206 with GGUF header verified.
- Debug APK artifact: `hamari-mehnat-debug-apk`, 28,344,632 bytes.
- Artifact SHA-256: `2615665a85ea2355b1a3808999f2545655ba28b154f0cf331fdcc5bc8e604f67`.
- Artifact: https://github.com/haseebop668-a11y/hamari-mehnat-ai/actions/runs/43603338348/artifacts/14117024547.

### Remaining acceptance gates
- Install and exercise the debug APK on a physical Android phone. Cloud CI cannot perform this on the user's device.
- Verify document picker/import, encrypted read/delete, PDF export, notification permission and reminder delivery.
- Download the ~696 MB model on-device, verify checksum and exercise inference while monitoring storage, RAM, temperature and battery.
- The release variant is built but is not signed with a user-owned release key. A distributable release APK requires the user's own signing key; never commit its keystore or passwords.
- Confirm usability and local AI language quality on the target phone before calling the whole product production-ready.

## Security and product boundaries
- Do not commit CVs, passports, certificates, profile data, model weights, keystores or credentials.
- No automatic application submission, email sending, account scraping or CAPTCHA bypass.
- Public job-feed and visa information is triage/research support, not a promise of sponsorship, hiring or visa eligibility.
- Keep the repository private before adding personal project material or secrets.
