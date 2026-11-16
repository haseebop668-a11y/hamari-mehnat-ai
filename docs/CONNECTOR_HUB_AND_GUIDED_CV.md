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
- The current UI is a plugin-style connector hub for the integrations actually supported by this release; it is not a generic remote MCP client. Generic MCP transport, dynamic third-party tool discovery and OAuth login for providers that do not document OAuth are not implemented.
