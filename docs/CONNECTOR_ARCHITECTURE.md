# Connector architecture — API providers and MCP-ready boundary

## Goal

Treat each external job source as an independently managed connector, similar to an app/plugin integration, while keeping the product Android-only, single-user and local-first.

## Current implementation

- `JobProvider`: common connector contract. Every provider returns normalized `SourceResult` / `JobListing` objects.
- `JobProviderDescriptor`: stable ID, display name, provider kind, credential requirement, supported-country metadata, daily request budget and per-search request-cost ceiling.
- `JobProviderRegistry`: validates unique IDs, exposes provider metadata, runs only explicitly enabled IDs, applies a rate-limit gate and isolates individual connector failures.
- `JobDiscoveryRepository`: composition root that registers built-in providers. The UI and matcher call the repository, not provider-specific APIs.
- `SourceRateLimiter`: local UTC-day counters and minimum intervals, keyed by provider ID.
- `AdzunaCredentialsStore`: user API credentials encrypted with Android Keystore AES-GCM. Disconnect clears ciphertext and deletes its key.

## Existing connectors

| Connector | Kind | Credentials | Coverage |
|---|---|---|---|
| Arbeitnow | Public API | No | Multi-country filtering |
| Remotive | Public API | No | Remote roles |
| Himalayas | Public API | No | Remote roles |
| Jobicy | Public API | No | Remote roles |
| Adzuna | Authenticated API | User app ID/key | AU, NZ, GB, DE, CA |
| Greenhouse | Company board | Board slug | Per-company |
| Lever | Company board | Site slug | Per-company |

LinkedIn, Indeed, SEEK and selected NZ recruitment sites remain browser hand-offs; they are not falsely represented as API connectors.

## Add a new API connector

1. Implement `JobProvider` or use `FunctionJobProvider`.
2. Validate endpoint scheme/host and all returned application URLs (HTTPS only).
3. Parse into the shared `JobListing` model; retain attribution and original application URL.
4. Add a descriptor with stable ID, countries, credential requirement and conservative quota.
5. Register it in `JobDiscoveryRepository.providerRegistry()`.
6. If authenticated, store credentials using a dedicated Android Keystore alias; never put keys in source, logs, URLs displayed to the user or analytics.
7. Add fixture/mock-server tests for valid response, malformed response, insecure links, query matching, country filtering, quota and credential redaction.
8. Add a bounded live smoke check only when the provider permits it without exposing credentials.

## MCP compatibility boundary

The current code is a plugin-style **API connector framework**, not a generic MCP client. An MCP server is not just an API key: it exposes tool schemas and may request actions or access data. A safe remote MCP implementation would need:

- HTTPS Streamable HTTP transport only (no arbitrary stdio process execution on Android).
- MCP initialization, protocol-version negotiation, server/session handling and bounded JSON-RPC responses.
- A user-visible server identity and explicit connection/disconnect controls.
- Tool discovery separated from execution.
- Per-tool allowlists and confirmation before any tool that transmits CV/profile data, contacts an employer or mutates application state.
- Strict response-size, timeout, redirect, host and rate limits.
- Credentials encrypted with Android Keystore and never copied into tool output or logs.
- A job-search-only permission profile by default; no access to passport/document vault unless separately approved.

This boundary is intentional: current API providers can be connected safely without adding a remote code execution surface or changing the agreed Android-only plan. MCP transport and the connection-management UI remain a separate, explicitly tested extension; no MCP connection is claimed as implemented today.

## Non-goals

- No backend, website, cloud database or multi-user service.
- No arbitrary downloadable APK/plugin code.
- No login scraping, CAPTCHA bypass or automatic job applications/messages.
- No sharing private CV/document content with a provider by default.
