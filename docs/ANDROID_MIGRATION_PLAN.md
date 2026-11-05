# Android-only Personal AI Job Hunter — V1

## Product boundary
- One owner and one private Android installation. No website, web dashboard, public registration, multi-user SaaS or payments.
- Online access is used for permitted vacancy discovery and current employer/immigration research. Offline mode uses local documents, cached vacancies, notes, tracker data and the on-device model.
- No automatic applications or employer messages without explicit owner approval.

## Architecture
- Native Android UI: Kotlin + Jetpack Compose.
- Local persistence: Room/SQLite; app-private files; Android Keystore-backed encryption.
- Local inference: llama.cpp Android native runtime (JNI/NDK), GGUF model stored in app-private files.
- Documents: Storage Access Framework import, file validation and local parsing.
- Background refresh: WorkManager with network constraints.
- Sources: official APIs/feeds and permitted public employer endpoints. No login scraping, CAPTCHA bypass or restricted-site crawling.

## Security
- Never commit CVs, passports, certificates, profile data, model files or credentials.
- Encrypt sensitive files at rest; redact personal data from logs.
- No ads or analytics SDK in V1.
- Provide export and deletion controls.
- Require human approval before any application submission or employer message.

## Delivery phases
1. Android foundation and CI.
2. Private profile, encrypted document vault and local database.
3. Offline model runtime, verified model download and on-device benchmark.
4. Job discovery, deduplication, matching explanations and cached search.
5. Truthful CV/cover-letter drafting grounded in reviewed profile facts.
6. Employer research, outreach drafts, application tracker and interview reminders.
7. Official-source visa/work-permit research with URL and checked-at timestamp.
8. Privacy, offline, performance and device tests; signed APK.

## Definition of done
- Reproducible release APK; unit and instrumentation tests pass.
- App opens and core local features work offline.
- Online search fails gracefully offline and resumes when connected.
- Local inference passes a fixed prompt suite on the target phone without memory/thermal instability.
- Vacancies retain source URL and retrieval timestamp; uncertain sponsorship remains unknown.
