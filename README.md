# Personal AI Job Hunter — private prototype v1

Single-user international job discovery and application tracker. No payment gateway, no public user registration, and no automatic job applications.

## Included
- CV upload (PDF/DOCX/TXT), text extraction and review-first optional AI extraction.
- Role synonym matching, skill/country relevance score and cautious sponsorship signals.
- Public job feeds: Arbeitnow; optional Remotive remote feed; optional Adzuna; optional Google Jobs via SerpApi; company-specific public Greenhouse and Lever boards.
- MongoDB persistence when an authenticated URI is configured.
- Direct search links for LinkedIn, Indeed, SEEK, EURES and official career pages.
- Employer enquiry drafts only; user reviews and sends manually.
- Basic visa-pathway research links; no legal decision or visa guarantee.

## Coverage limitations
This is not a crawler of the entire web. There is no universal public API for all LinkedIn, Indeed, SEEK, EURES or employer vacancies. LinkedIn/Indeed/SEEK remain direct search links; no login scraping or automated account activity. Greenhouse and Lever public endpoints need known company board tokens/site names and are not global search endpoints. Adzuna and SerpApi require credentials and may have quotas/costs. Remotive is remote-only, delayed, and must be attributed with a link to the source. Every vacancy needs human verification; sponsorship is never assumed.

## Secrets (deployment only; never commit)
- MONGODB_URI (authenticated Atlas connection string)
- MONGODB_DB=personal_job_hunter
- APP_PASSWORD (long unique password; required before public exposure)
- Optional ADZUNA_APP_ID, ADZUNA_APP_KEY, SERPAPI_KEY, GROQ_API_KEY
- Optional GREENHOUSE_BOARD_TOKENS and LEVER_SITE_NAMES (comma-separated verified company board identifiers)

The ChatGPT Gmail connector is not automatically callable from a separately hosted app. This app creates message drafts only; app-side Gmail sending requires separate OAuth configuration and explicit authorization.

## Run
Python 3.11+: create venv, install requirements.txt, then run `streamlit run app.py`.

## MongoDB
Collections: profiles, jobs, applications, search_runs, employer_contacts, search_results, saved_jobs. Without MONGODB_URI, data is session-only. Atlas project/cluster and collections exist, but the hosted app still needs authenticated URI secret and network allowlisting before live persistence can be verified.

## Security
Keep repo private. Never commit CV/passport/certificates/secrets. Configure APP_PASSWORD before deployment. No bulk unsolicited outreach. Never submit job applications or send employer messages without user approval. Verify employer, vacancy, salary, contract and immigration pathway using official sources. Government authorities—not this app—issue work permits and visas.
