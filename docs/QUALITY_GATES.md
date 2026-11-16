# Hamari Mehnat — Release Quality Gates

## Product and money boundary
- Android-only, single-owner, local-first application. No hosted website, public backend, SaaS accounts or cloud CV storage.
- No subscriptions, payment gateway, in-app purchases, bank/card collection, deposits, payroll or money transfers in this release.
- Salary, hourly pay, currency, deductions and benefits are employment-contract evidence only. Preserve provider/source values; do not silently convert currency or imply take-home pay.
- Adzuna app ID/key credentials authorize API use, not financial transactions.
- No automatic job applications, employer messages, login scraping, CAPTCHA bypass or downloaded executable plugins.

## How a phase is accepted
Every phase follows this sequence: map the user journey and ownership; implement the data/domain path and user interface; add deterministic tests; review security, privacy, failure handling, performance and accessibility; run the full Android CI workflow; inspect logs and artifact metadata; record the exact source SHA.

A beautiful screen is not complete if it contains inert controls, missing persistence, an unhandled error state, or a broken path to the actual data source. Do not move a phase to complete while a gate is failing.

## Phase 0 — Foundation and whole-system map
- Inventory every screen, navigation action, background worker, job provider, repository, persistence store, notification, external URL and JNI entry point.
- Trace each user action from Compose UI through domain/data processing and persistence back to loading, success, empty, error and recovery states.
- Record module ownership, dependency versions, Android SDK/NDK/CMake/Gradle versions, ABI, model revision and hash, permissions, build variants and data retention.
- Define redacted logging, timeout/cancellation rules, retry policies, data migration/rollback and reproducible build steps.
- Gate: no unowned feature entry point, unexplained permission, secret in source, or undocumented critical data flow.

## Phase 1 — Job discovery and country data pipeline
- Verify every provider response schema, normalization, source attribution, original listing URL, HTTPS validation, country aliases and supported market.
- Check all-token role matching, duplicate identity, freshness/expiry, salary/currency preservation and application-link handling.
- Enforce provider terms, quotas, daily budgets, rate limiting, request timeouts, cancellation and bounded retries with backoff.
- Verify user connector enable/disable controls affect both foreground search and scheduled background work.
- Test empty feed, malformed response, duplicate listing, expired job, HTTP error, quota/throttling, offline mode and one-provider-fails/others-succeed behavior.
- Gate: a broken source cannot erase valid results; disabled providers are never invoked; every result has an attributable source.

## Phase 2 — Employer, visa and contract evidence
- Separate official government evidence from job-board content and employer-supplied claims.
- Record source URL, retrieval time, important excerpt and confidence/uncertainty with every verification result.
- Require exact business identifiers where the official register supports them; distinguish exact match, no public result and inconclusive lookup.
- Contract review must show which text triggered each flag, identify missing information and explain that the output is not legal advice.
- Do not infer that employer accreditation guarantees a specific vacancy, Job Check, sponsorship or an applicant's visa eligibility.
- Gate: every important conclusion can be traced to source material and uncertainty remains visible.

## Phase 3 — Security, privacy and data lifecycle
- Review Android Keystore key creation, use, invalidation, AES-GCM IV uniqueness, authentication behavior, migration, corruption recovery and key rotation strategy.
- Review private files, SQLite fields, WorkManager inputs, notification payloads, logs, clipboard, app-switcher snapshots, Android backup and exported PDF/share behavior for PII leakage.
- Enforce TLS/HTTPS, redirect policy, endpoint validation, response-size bounds, timeouts and parser limits for XML, ZIP/DOCX, PDF, image and JSON content.
- Threat-model user-supplied MCP endpoints, bearer tokens, session identifiers, server tool schemas/descriptions and remote tool output. Server annotations are untrusted hints.
- Show the exact MCP server, tool and arguments before each call. Do not transmit profile/CV/document data unless a future explicit data-sharing action is designed and approved.
- Provide explicit clear/delete paths for profile, credentials, imported documents, drafts, tracker data, cache and model data. Explain irreversible key deletion where appropriate.
- Gate: no PII in logs/notifications/background input; migrations are tested; dangerous external/destructive actions cannot happen silently.

## Phase 4 — Local AI and application documents
- Verify model download size, SHA-256, free space, temporary-file cleanup, restart/failure behavior and atomic installation.
- Synchronize model load/generate/unload lifecycle; recover gracefully from low memory, process death, native-library failure and unavailable storage.
- Bound prompt input, extracted text, generation duration and output tokens. Treat job descriptions and uploaded documents as untrusted data, not instructions.
- Test the entire guided CV path: unanswered fields, skipped fields, follow-up questions, edited answers, vacancy changes, CV and cover-letter drafts, history, deletion and PDF export.
- Require user review of all facts. Never create qualifications, licenses, dates, salary, work rights, employers or experience that are absent from evidence.
- Gate: document data remains local unless the user explicitly exports/shares it; real-device profiling is still required for RAM, temperature, speed and battery.

## Phase 5 — Frontend and accessibility
- Connect Home, Jobs, Files, Tracker, Connector Hub and Local AI to their actual domain workflows and persistence.
- Every button, switch, tab, menu and navigation affordance must perform a real action and show loading, completion, empty and recoverable failure states.
- Verify back navigation, process recreation, configuration changes, keyboard/IME behavior, scrolling, touch targets, text scaling, contrast, screen-reader labels and focus order.
- Preserve the agreed warm Fieldbook paper/forest/sage/terracotta design while making job data, security warnings and action states easy to read.
- Gate: no placeholder content, decorative/inert controls, unreachable required actions or broken end-to-end journeys.

## Phase 6 — Build, release and acceptance
- Run unit tests, mocked API tests, debug assembly, minified release assembly, lint/static checks, dependency/license review and live public endpoint smoke checks.
- Inspect every CI job and its relevant logs; a green summary alone is insufficient when a test step was skipped or allowed to fail.
- Tie the debug APK artifact to the exact latest source SHA. Record workflow run ID, artifact ID, archive size, checksum and expiry. Never reuse an artifact from an older commit as current.
- Keep the owner's release signing key and passwords outside source control and CI logs. The release APK is not distribution-ready until signed using the user's own key.
- Perform physical Android acceptance for installation, model download/inference, encrypted document import/read/delete/export, notification permission and reminders, offline behavior, WorkManager/Doze, low memory, thermal/battery, and accessibility.
- Gate: report cloud CI and physical-device acceptance separately. Never claim production readiness from a cloud build alone.

## Explicitly out of scope until separately approved
- Payment gateway, subscriptions, paid placement, payroll, bank integrations, cards, wallets and money movement.
- Provider-specific OAuth unless the provider documents a supported flow and valid client registration exists.
- Full MCP feature compatibility including standalone GET streams, reconnect/resumption, server-initiated requests and OAuth.
- Automatic job applications, employer messages or autonomous external actions.