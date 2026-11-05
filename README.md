# Personal AI Job Hunter — private prototype v1

A single-user job discovery and application-tracking prototype built from the existing Hamari Mehnat AI repository.

## Current scope
- Candidate profile and CV upload (PDF, DOCX, TXT)
- Basic CV text extraction and keyword-based matching
- Public job discovery through Arbeitnow
- Optional Google Jobs results through SerpApi and Adzuna API (requires your own credentials)
- Direct search links for LinkedIn Jobs, Indeed, SEEK, EURES, Google and Bing
- Saved shortlist and application status tracking
- Employer enquiry email drafts (no automatic sending)
- Starter work-permit pathway notes for Romania, Serbia and Australia
- Optional MongoDB Atlas persistence
- No payment gateway, no multi-user registration, no automated application submission

## Important source limitations
LinkedIn, Indeed and SEEK are not scraped or automated. Use their official APIs/partner access only where you have authorization. The direct links open their search pages. Public job feeds may not include every country or occupation. A job listing is not proof of sponsorship, employer legitimacy, or visa eligibility.

## Run locally
Python 3.11+
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
streamlit run app.py
```

## Streamlit secrets
Set these in deployment secrets (never commit real values):
```toml
APP_PASSWORD = "use-a-long-unique-password"
MONGODB_URI = "mongodb+srv://..."
MONGODB_DB = "personal_job_hunter"
# Optional provider credentials:
SERPAPI_KEY = ""
ADZUNA_APP_ID = ""
ADZUNA_APP_KEY = ""
```

MongoDB collections used: `profiles`, `jobs`, `applications`. The app uses a single owner profile document with `_id=owner`.

## Security
- Keep the GitHub repository private before adding personal data or secrets.
- Never commit CVs, passports, certificates, API keys, MongoDB connection strings or passwords.
- Configure APP_PASSWORD before public deployment.
- Do not send employer messages without manually reviewing the recipient and content.
- Verify job, employer, contract, salary, sponsorship and immigration requirements through official sources before acting.
