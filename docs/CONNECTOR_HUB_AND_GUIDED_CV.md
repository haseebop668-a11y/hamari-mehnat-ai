# Connector Hub and Guided CV update

## Product boundary

This update keeps Hamari Mehnat Android-only, single-user and local-first. Connector settings and candidate data remain on the device. No website, backend, multi-user service, arbitrary executable plugin download, automatic application submission or automatic employer messaging is introduced.

## Connector Hub

- Public feeds (Arbeitnow, Remotive, Himalayas and Jobicy) can be enabled or disabled individually. They do not require a user login.
- Adzuna can be connected/disconnected from the hub. Its current API authorization is a user-supplied app ID and app key obtained from Adzuna's developer portal; credentials are encrypted with Android Keystore AES-GCM.
- Greenhouse and Lever are company-specific public ATS board connectors. The user supplies board IDs/site names and enables the relevant connector.
- Enabled connector IDs are persisted locally and passed into the provider registry. A disabled provider is not queried.
- LinkedIn, Indeed, SEEK and EURES remain official browser hand-offs where an unrestricted authorized API is not available. They are not shown as connected API accounts and the app does not scrape login-protected pages.

### OAuth/login limitation

There is no universal OAuth login that can be applied to every job provider. Each provider must explicitly support OAuth or another documented authorization flow. The providers currently registered in this app do not all expose OAuth: public feeds need no login, Adzuna currently uses app ID/key credentials, and Greenhouse/Lever are public board endpoints. The hub must not collect third-party passwords or pretend a browser hand-off is an API connection. A future provider with documented OAuth 2.0 Authorization Code + PKCE can be added with a provider-specific authorization descriptor and callback, without embedding a client secret or adding a backend.

## Guided CV Builder

- The user pastes a target job description or selection criteria.
- The app asks for candidate facts one at a time: name, contact details, education, work history, skills, certifications/licences and languages.
- Each answer is stored in the existing encrypted local profile. The user may skip a field; skipped data is marked "Not provided" and is never fabricated.
- On the user's Generate action, the app automatically downloads the separately packaged local GGUF model if it is not already installed, verifies the pinned size and SHA-256, loads it into the on-device llama.cpp runtime and generates a criteria-targeted CV draft.
- CV generation remains on-device. The user must review the result and can export the draft as PDF using the existing document export flow.

## Verification still required

- Android CI must pass for the new branch head, including debug/release assembly, unit tests and live smoke checks.
- Physical-device testing is still required for OAuth-free connector toggles, Adzuna credential save/disconnect, company ATS board configuration, model download/inference, CV question progression, PDF export, memory pressure, thermal behaviour and battery use.
- The connector hub supports current MCP 2026-07-28 stateless Streamable HTTP discovery and per-request metadata/headers. A constrained fallback supports legacy 2025-11-25/2025-06-18/2025-03-26 initialize/session servers. Requests and results are size-bounded; response IDs and tool-list cursors are checked. Current tool schemas with supported x-mcp-header primitive parameters are validated before they are mapped to request headers.
- Unsupported MCP features remain explicit: standalone GET event streams, reconnection/resumption, server-initiated requests, input-required/elicitation round trips, resources, prompts, subscriptions and OAuth.

## Final Studio and MCP connector update

- The Local AI tab now contains a warm editorial CV and application studio using the existing Fieldbook palette: warm ivory paper, soft sage/forest green, restrained terracotta, rounded paper panels and serif editorial headings.
- The studio collects candidate basics progressively, asks the local model for up to five vacancy-specific follow-up questions, and stores answers in the existing Android Keystore-encrypted profile.
- It can draft either a tailored CV or a cover letter, stores up to eight local document versions, and exports the selected draft as PDF. Every draft remains user-reviewed; no application or message is sent.
- Added an MCP Streamable HTTP client with current 2026-07-28 stateless discovery, per-request metadata and method headers; a limited legacy initialize/session fallback is retained for older supported revisions. Optional bearer authorization is held in screen memory only.
- MCP tool discovery filters on `annotations.readOnlyHint=true` and not `destructiveHint=true`, but these are untrusted server-provided hints, not a safety guarantee. The screen warns the user to trust the server and inspect the tool/arguments before approving each call. The app does not automatically transmit profile or document data. Remote code is never downloaded or executed.
- MCP bearer tokens are held only in the current screen's memory and cleared on disconnect. Legacy sessions receive a DELETE on disconnect where supported; the current stateless protocol has no session to delete. The connector does not collect account passwords.
- Provider-specific OAuth login is not enabled: the current job data sources do not share one OAuth system. Adzuna uses developer-issued app ID/key credentials; public feeds and company ATS boards do not require user login. MCP servers that require OAuth rather than a bearer token are not supported by this release and must not be labelled as connected.
- Unit tests cover modern discovery/metadata, legacy fallback, bounded responses, response-ID matching, SSE framing, pagination, custom parameter headers, HTTPS validation and read-only filtering. The exact-head Android CI run remains the release gate.

## Document OCR

- PDF extraction first uses selectable text. If no selectable text exists, the app renders up to 10 pages locally and runs the bundled ML Kit Latin text-recognition model on-device.
- PNG, JPEG and WebP image files can also be OCR-processed locally after bounded downsampling.
- Imported source files and extracted text remain in the existing encrypted local vault/profile. OCR output carries a visible review warning because names, dates, IDs and numbers can be misread.
- OCR is Latin-script only in this release; Urdu and other non-Latin scripts are not claimed as supported. OCR is bounded by the 25 MB source limit, 10-page scanned-PDF limit, 2,200-pixel render dimension and per-page timeout.
