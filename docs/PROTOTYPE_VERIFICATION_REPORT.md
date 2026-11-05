# Personal AI Job Hunter — Prototype Verification Report
Date: 2026-11-05
Branch: prototype/personal-ai-job-hunter-v1

## Intended use
Private, single-user job discovery and application tracking for the owner. No payment gateway, no public sign-up, no multi-tenant recruitment service, and no automatic application submission or employer messaging.

## Implemented in the branch
1. Streamlit workspace with Dashboard, Candidate Profile/CV, Find Jobs, Saved Jobs, Employer Outreach and Visa Pathway sections.
2. CV parsing for text-based PDF, DOCX and TXT. Optional Groq-based structured extraction is review-first; extraction is not proof of qualifications.
3. Job matching uses selected roles, role synonyms, CV skill terms and target country. The score is a relevance estimate, not an eligibility or hiring probability.
4. Sponsorship language is classified as a possible signal, not mentioned, or explicitly restricted. Positive signals still require employer confirmation.
5. Search adapters: Arbeitnow public feed; optional Remotive remote feed; optional Adzuna API; optional Google Jobs via SerpApi; public Greenhouse and Lever boards when verified company board identifiers are configured.
6. Direct search links for LinkedIn, Indeed, SEEK, EURES, Google and Bing. No authenticated scraping or account automation.
7. Saved jobs and application status can persist to MongoDB when the deployed runtime has a valid authenticated MONGODB_URI and network access.
8. Employer enquiry email drafting only. The user reviews and sends. The ChatGPT Gmail connector is not automatically available inside the deployed Streamlit app.
9. Starter visa pathway notes and official links. Immigration law and permit requirements need live official-source verification for each case.

## External infrastructure observed
- Atlas project: Personal Job Hunter Prototype
- Cluster: JobHunterM0 (M0/free, AWS EU_WEST_1, state IDLE)
- Database: personal_job_hunter
- Collections: profiles, jobs, applications, search_runs, employer_contacts, search_results, saved_jobs
- Indexes: 12 total reported by Atlas.
- App-specific database user: jobhunter_app with readWrite limited to personal_job_hunter.
- Atlas network access list was empty at last inspection.
- No authenticated MONGODB_URI was configured in a hosted app during this verification.

## Test status
GitHub Actions workflow: .github/workflows/prototype-checks.yml
It installs requirements, compiles Python modules and runs unittest discovery. Previous pipeline runs completed successfully before the latest regression additions. The latest commit must be checked for a final green run before calling the branch fully tested.

## Not yet verified / prerequisites
- Live hosted deployment and public preview.
- Streamlit runtime to Atlas: add authenticated MONGODB_URI and MONGODB_DB secret, plus a narrowly scoped Atlas network access rule suitable for the chosen host. Do not put credentials in Git.
- APP_PASSWORD secret before exposing the app. The repository is currently public; make it private before storing any personal files or sensitive configuration.
- CV is not yet available in the current connected files/repository. Owner must upload a readable PDF/DOCX/TXT and review extracted details.
- Adzuna, SerpApi, Groq, Greenhouse and Lever are optional integrations; they are not connected until credentials/board identifiers are configured and a live provider test passes.
- Direct Gmail sending from the hosted app is not configured. The app only produces drafts.
- No global search API exists for all LinkedIn, Indeed, SEEK and EURES vacancies. Search coverage is necessarily partial and provider-specific.
- Live employer outreach, employer verification, sponsorship confirmation, visa decision, permit processing and job placement are not performed by this prototype.

## Safe end-to-end test procedure
1. Set APP_PASSWORD and authenticated MONGODB_URI in the hosting provider's secret manager.
2. Upload the actual CV; review every extracted field and correct it.
3. Select target countries and roles; run searches with at least one source configured.
4. Open each original vacancy and verify employer, current vacancy, location, salary, work-rights wording and application channel.
5. Save only suitable jobs; update application status manually.
6. Generate employer enquiry draft; verify recipient on the employer's official site; manually send only after review.
7. For a selected country, follow official immigration authority guidance and verify the employer-led work authorisation route before paying any fee.

## Safety boundary
No guarantee of job, sponsorship, work permit, visa or processing time. No false credentials, bulk unsolicited outreach, automatic application submission, or payment collection. Never upload passport or other sensitive identity documents to an untrusted AI service.
