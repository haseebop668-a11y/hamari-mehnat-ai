# Implementation status — Personal AI Job Hunter

Branch: `feature/multi-country-visa-job-hunter`

This document separates source implementation, automated verification, and real-device acceptance. A successful cloud build does not prove behaviour on a physical phone.

## Phase 1 — Live jobs, employer verification and contract review

### Implemented
- Country registry for New Zealand, Australia, United Kingdom, Germany and Canada.
- Public job-feed adapters: Arbeitnow, Remotive, Himalayas and Jobicy; optional Adzuna country API; optional company-specific Greenhouse and Lever boards.
- Vacancy freshness filtering and stable-ID deduplication before display and scheduled saving.
- Browser hand-off links for SEEK NZ, Tradestaff, Trade Assist and Enterprise Recruitment; these are not represented as API integrations.
- New Zealand public accredited-employer lookup, exact 13-digit NZBN matching, accreditation expiry parsing, checked timestamp and official-source link.
- Conservative no-result handling: absence from the public list is inconclusive because some employers opt out of publication.
- AEWV contract checklist covering identity, role, location, duties, hours, pay, leave, duration, trial-period wording, deductions, bonding/repayment, worker-paid fees, passport retention and mismatches against expected vacancy details.
- Role synonyms for civil labour, roadworks, plant operation, painting, maintenance, logistics and scaffolding.
- Optional Adzuna API for AU/NZ/UK/DE/CA, with app ID/key encrypted locally using Android Keystore and a conservative 10-country-request UTC daily app cap.
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
- PDF, DOCX and TXT local text extraction; files up to 25 MB and extracted text capped at 50,000 characters. Scanned PDFs (up to 10 pages) and PNG/JPEG/WebP images use on-device Latin-script OCR with human-review warnings.
- DOCX ZIP-entry and expanded XML limits, disabled document type declarations, and safe extraction bounds.
- Imported filename and MIME metadata sanitization; temporary encrypted files are cleaned up after failed/interrupted writes.
- Local specialist workflows for CV analysis, job matching, CV tailoring, cover letters, employer outreach, interview preparation and official-source explanation.
- User-reviewed application draft export to multi-page PDF, including long-token wrapping and page footers.
- Contract review can compare extracted agreement text with expected employer, job title, location and advertised hourly pay.
- Local model download checks expected byte size and SHA-256, caps overlong downloads and cleans partial files.

### Limitations
- OCR is Latin-script only; Urdu and other non-Latin scripts are not supported. Names, dates and ID numbers must be checked manually because OCR may misread them.
- The local GGUF model is downloaded separately; it is not bundled into the APK. Urdu/Roman Urdu quality, RAM, speed, battery and thermal performance require phone testing.
- AI output is a draft only. The user reviews all details and manually approves applications and messages.

## Phase 3 — UI and Android APK

### Implemented
- Native Android Kotlin / Jetpack Compose UI with Home, Jobs, Files, Tracker and Local AI navigation.
- Original Fieldbook visual identity: warm paper surfaces, forest-green primary palette, restrained terracotta accents, serif editorial headings, passport masthead and custom bottom dock.
- Consistent visual language across all five screens while retaining the existing job, vault, tracker and local-agent workflows.
- UI status distinguishes an integrated local AI runtime from a model that has actually been downloaded.
- Deprecated Compose divider calls replaced with current `HorizontalDivider` API.
- GitHub Actions builds ARM64 debug and minified release variants, runs unit tests and public endpoint smoke checks, and uploads the installable debug APK.
- CI smoke checks are gating: a failed public endpoint/model metadata check fails the workflow rather than being silently ignored.

### Connector architecture
- External job sources now use a provider contract (`JobProvider`) and an in-process `JobProviderRegistry` with unique IDs, display metadata, provider type, credential requirement, supported-country metadata, per-provider daily request budgets and maximum per-search request costs.
- Public feeds, Adzuna, Greenhouse and Lever are registered as independent connectors. `JobDiscoveryRepository` is the composition root; adding a provider does not require changing the matching engine or UI call sites.
- The registry checks the local rate limiter before invoking a connector and isolates provider exceptions so one unavailable source does not cancel the entire search.
- This is a safe Android plugin-style connector architecture, not arbitrary executable plugin installation. It does not download or run third-party code. Adzuna is the current user-authenticated API connector; its credentials remain encrypted in Android Keystore.
- A limited remote MCP Streamable HTTP client and connection screen are implemented for protocol version 2025-11-25. It supports HTTPS POST initialization, negotiated protocol headers, session IDs where supplied, JSON responses, bounded 1 MiB SSE response bodies, multiline SSE data fields and JSON-RPC response-ID matching.
- This is not full MCP feature compatibility: standalone GET event streams, reconnect/resumption, server-initiated requests and OAuth are not implemented. Server tool annotations are untrusted metadata, not a security boundary. The UI filters discovery using readOnlyHint and requires a warning plus explicit user confirmation before every manually initiated tool call. Profile/documents are not automatically sent to the remote server.

### API audit and fixes in progress
- Job source links now require a valid HTTPS URL with a host and no embedded user-info.
- Keyword matching now requires every meaningful query token, reducing broad OR-match false positives.
- Lever and other adapters share country alias matching, including AU/UK/NZ/DE/CA codes and boundary-aware region aliases.
- Official visa page extraction rejects responses above 2 MB rather than silently analysing a truncated page.
- INZ employer responses are capped at 1 MB; unrelated fuzzy search results are no longer labelled as exact name matches.
- Expired vacancy cleanup now removes only records still in the `Saved` stage; progressed applications are preserved when a listing expires.
- Tracker and interview personal text fields are now encrypted with a dedicated Android Keystore AES-GCM key; existing SQLite records are migrated on database upgrade.
- Interview reminder work requests now persist only an interview UUID; reminder details are read from the encrypted local store. App startup refreshes older scheduled work to remove legacy plaintext input payloads.
- Adzuna request failures do not echo credential-bearing URL details.
- MCP response bodies are capped while streaming bytes into memory; the 1 MiB limit is checked before a larger response can be buffered.
- Local model SHA-256 validation is cached for an unchanged app-private file fingerprint after the first successful verification or a verified download. The NativeLlama facade serializes load/generate/unload operations and avoids reloading the GGUF model for each prompt.
- Provider registry regression tests cover unique IDs, explicit provider enablement and rate-limit enforcement.
- The earlier duplicate `KeyProperties` import in `AdzunaCredentialsStore.kt` was removed before the previous green baseline. The current hardening commit adds bounded MCP streaming, JSON-RPC ID matching, SSE regression coverage and a synchronized in-process local model runtime cache. Its own exact-commit CI run remains the verification gate.
- Regression tests cover all-token matching, ISO country aliases, insecure link rejection and oversized official-source pages.
- Android CI concurrency now groups push and pull-request runs by the same head branch to avoid duplicate builds.
- API architecture: Android-side public API clients only; no separate REST backend is deployed, consistent with the Android-only single-user scope. Adzuna is optional and requires the user to register for personal API credentials. Country-specific official job-board API coverage remains incomplete where providers require credentials or expose no public API. Browser hand-offs remain explicitly labelled as such.
- Security boundary: profile, document content, and personal tracker/interview text fields are encrypted with Android Keystore AES-GCM. Workflow metadata (IDs, stages, timestamps and time-zone identifiers) remains in app-private SQLite for querying; Android backup is disabled.

### Automated verification — prior baseline and current gate
- Previous verified baseline: commit `53e4e6d1b765c541ed0fd55e8bbaff9e2ab8c2b4`, GitHub Actions run https://github.com/haseebop668-a11y/hamari-mehnat-ai/actions/runs/43794068813, successful.
- The previous green run does not verify later hardening commits. The current branch head must pass a new workflow run on the exact same source SHA before its APK can be called verified.
- `assembleDebug`, `assembleRelease`, and `testDebugUnitTest`: passed.
- Release minification/R8 and release packaging: passed.
- Live smoke checks: Arbeitnow HTTP 200 (300 items), Remotive HTTP 200 (16), Himalayas HTTP 200 (20), Jobicy HTTP 200 (5), Hugging Face model metadata HTTP 200 and model binary range request HTTP 206 with GGUF header verified.
- Do not reuse an APK from an earlier source commit as evidence for a later commit. Record the artifact ID, archive size and SHA-256 from the exact-head successful run.

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


## Commercial and money-flow boundary

- This release does not accept payments, subscriptions, deposits, bank/card details or transfer money.
- Salary, hourly pay, deductions, leave, contract terms and currency are employment evidence for user review, not financial transactions or payroll calculations.
- Adzuna app credentials authorize API requests only; they are not payment credentials. Provider quotas and commercial terms must be checked against the provider's current documentation before changing request rates or adding paid integrations.
- Any future payment, premium tier or monetization feature requires a separate product decision and security/compliance review. It must not be silently added to this local-first release.
