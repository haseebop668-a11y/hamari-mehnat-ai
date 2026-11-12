# Implementation status — multi-country job hunter

Branch: feature/multi-country-visa-job-hunter

## Existing foundation retained
Native Android Kotlin/Jetpack Compose app; encrypted local profile and document vault; local AI runtime; public job-source clients; relevance matcher; saved-job/application tracker; WorkManager discovery; official-source fetcher; GitHub Android CI.

## Added
- Country registry contract with ISO country codes and official immigration/employer-verification metadata.
- Initial country modules: New Zealand, Australia, United Kingdom, Germany and Canada.
- Conservative country-aware job-ad sponsorship classifier.
- Unit tests for registry completeness, aliases, sponsorship signals, restrictions and unknown countries.
- Documentation describing how to add future country modules without changing shared app architecture.

## Not yet verified or implemented
- Live employer-register integrations and job-specific verification for every country.
- Every portal's live search integration and legal API access.
- Production CV/cover-letter PDF generation and full trade-profile editor.
- Automated application submission. Current safety boundary remains human-reviewed and human-confirmed.
- Release signing key, real-device QA, and a green CI APK artifact.
