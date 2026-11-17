# Multi-country architecture

## Goal
Keep the Android client, candidate profile, matching pipeline, encrypted document vault and application tracker country-neutral. Immigration and employer verification differences belong in country modules.

## Initial country modules
- NZ: AEWV and Immigration New Zealand employer-accreditation research.
- AU: employer-sponsored skilled visa pathways and Department of Home Affairs sources.
- GB: Skilled Worker route and licensed sponsor register.
- DE: qualified-professional work route and Make it in Germany.
- CA: employer-specific work permits, LMIA/exemption and IRCC/ESDC sources.

These are official research entry points, not legal determinations. Current route eligibility, occupation lists, salary thresholds, employer status, job checks, qualifications, language and applicant-specific rules must be rechecked against official sources.

## Add a country
1. Add a CountryConfig in CountryRegistry with ISO-3166-1 alpha-2 code, display name, official immigration authority URL, work route, employer verification method/URL and official source records.
2. Add a country-specific policy adapter only for rules not covered by the shared conservative classifier.
3. Add official-source parser tests with saved fixtures. Never claim a register match unless the authoritative source was queried and employer identity matched.
4. Add country aliases and location parsing tests. Never infer citizenship or work rights from a location string.
5. Add source adapters only for permitted APIs/feeds. Never bypass login, CAPTCHA, paywalls, rate limits or access restrictions.
6. Register the country; profile, CV, tracker and Android navigation remain unchanged.

## Shared job states
- Potential: sponsorship wording exists but employer and job checks remain.
- Needs confirmation: generic work-rights wording or incomplete evidence.
- Rejected: explicit restriction or verified lack of sponsorship, with reason.
- Unverified: source or employer could not be reliably checked.

## Safety boundaries
Employer accreditation/licensing does not mean every vacancy can sponsor. Sponsorship phrases are not proof of visa eligibility. ATS keyword coverage is not a pass probability. Applications and messages remain user-reviewed and user-confirmed; CAPTCHA/OTP is completed by the applicant. Never invent qualifications, licences, employment history or work rights.
