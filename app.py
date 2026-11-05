from __future__ import annotations

import hashlib
import io
import os
import re
from datetime import datetime, timezone
from urllib.parse import quote_plus

import pandas as pd
import streamlit as st

from job_sources import search_jobs
from matching import score_job, extract_profile_from_cv, parse_cv

st.set_page_config(page_title="Personal AI Job Hunter", page_icon="🌍", layout="wide")

# Private single-user prototype. Configure APP_PASSWORD before public deployment.
def setting(name: str, default: str = "") -> str:
    try:
        value = st.secrets.get(name, default)
        if value:
            return str(value)
    except Exception:
        pass
    return os.getenv(name, default)

APP_PASSWORD = setting("APP_PASSWORD")
if APP_PASSWORD:
    if not st.session_state.get("authenticated"):
        st.title("🔐 Personal AI Job Hunter")
        entered = st.text_input("Private access password", type="password")
        if st.button("Unlock workspace", type="primary"):
            if hashlib.sha256(entered.encode()).hexdigest() == hashlib.sha256(APP_PASSWORD.encode()).hexdigest():
                st.session_state.authenticated = True
                st.rerun()
            else:
                st.error("Incorrect password.")
        st.stop()
else:
    st.warning("Private prototype mode: set APP_PASSWORD in deployment secrets before exposing this app publicly.")

MONGODB_URI = setting("MONGODB_URI")
MONGODB_DB = setting("MONGODB_DB", "personal_job_hunter")

@st.cache_resource(show_spinner=False)
def get_db(uri: str, db_name: str):
    if not uri:
        return None
    from pymongo import MongoClient
    client = MongoClient(uri, serverSelectionTimeoutMS=4000)
    client.admin.command("ping")
    return client[db_name]

try:
    db = get_db(MONGODB_URI, MONGODB_DB) if MONGODB_URI else None
    db_status = "Connected" if db is not None else "Not connected — local session only"
except Exception as exc:
    db = None
    db_status = f"Connection failed: {type(exc).__name__}"

DEFAULT_PROFILE = {
    "full_name": "",
    "citizenship": "Pakistan",
    "current_country": "Pakistan",
    "current_city": "",
    "email": "",
    "phone": "",
    "experience_years": 0,
    "education": "",
    "english_level": "B1",
    "target_countries": ["Romania", "Serbia", "Germany", "Poland", "Australia", "Saudi Arabia", "UAE"],
    "target_roles": ["Heavy Diesel Mechanic", "Mobile Plant Mechanic", "Construction Worker", "Warehouse Assistant", "Cleaner", "Mining Utility Worker"],
    "skills": [],
    "certificates": [],
    "visa_route": "Employer-sponsored / work permit",
    "notes": "",
    "cv_text": "",
}

def load_profile():
    if "profile" in st.session_state:
        return st.session_state.profile
    if db is not None:
        doc = db.profiles.find_one({"_id": "owner"})
        if doc:
            doc.pop("_id", None)
            st.session_state.profile = {**DEFAULT_PROFILE, **doc}
            return st.session_state.profile
    st.session_state.profile = DEFAULT_PROFILE.copy()
    return st.session_state.profile

def save_profile(profile):
    st.session_state.profile = profile
    if db is not None:
        db.profiles.replace_one({"_id": "owner"}, {"_id": "owner", **profile, "updated_at": datetime.now(timezone.utc)}, upsert=True)

def save_jobs(jobs):
    if db is not None and jobs:
        for job in jobs:
            db.search_results.update_one({"job_id": job["job_id"]}, {"$set": {**job, "last_seen_at": datetime.now(timezone.utc)}}, upsert=True)

def load_saved_jobs():
    if db is not None:
        return list(db.saved_jobs.find({}, {"_id": 0}).sort("match_score", -1).limit(500))
    return st.session_state.get("saved_jobs", [])

profile = load_profile()

st.title("🌍 Personal AI Job Hunter")
st.caption("Private single-user prototype • International job discovery • Employer-sponsored opportunities")
metric_cols = st.columns(4)
metric_cols[0].metric("Database", "MongoDB" if db is not None else "Session")
metric_cols[1].metric("Saved jobs", len(load_saved_jobs()))
metric_cols[2].metric("Target countries", len(profile.get("target_countries", [])))
metric_cols[3].metric("Mode", "No payments")
with st.expander("System connections & safety", expanded=False):
    st.write("**MongoDB:**", db_status)
    st.write("**Job data:** Arbeitnow public job-board API; optional Adzuna and SerpAPI keys; direct links to official job-search pages.")
    st.write("**Outreach:** Draft-only. No messages are sent automatically.")
    st.write("**Payments:** Disabled in this prototype.")
    st.info("LinkedIn, Indeed and SEEK are not scraped or automated. Their search pages are opened as links; API access is used only when an authorized provider key is configured.")

tabs = st.tabs(["Dashboard", "My profile & CV", "Find jobs", "Saved jobs", "Employer outreach", "Visa pathway"])

with tabs[0]:
    st.subheader("Your job-search command centre")
    st.write("The prototype prioritizes real vacancies that match your CV and selected countries. It does not promise sponsorship or visa approval.")
    jobs = load_saved_jobs()
    if jobs:
        df = pd.DataFrame(jobs)
        display_cols = [c for c in ["title", "company", "location", "country", "match_score", "source", "url"] if c in df.columns]
        st.dataframe(df[display_cols], use_container_width=True, hide_index=True)
    else:
        st.info("Add your CV, then run Find jobs to start building your shortlist.")
    st.markdown("**Current target roles**")
    st.write(", ".join(profile.get("target_roles", [])) or "No roles selected yet.")
    st.markdown("**Current target countries**")
    st.write(", ".join(profile.get("target_countries", [])) or "No countries selected yet.")

with tabs[1]:
    st.subheader("Candidate profile")
    st.caption("Your CV file was not present in the connected repository or the retrieved conversation files. Upload the actual CV here; the app will extract text and create a draft profile for your review.")
    with st.form("profile_form"):
        c1, c2 = st.columns(2)
        full_name = c1.text_input("Full name", profile.get("full_name", ""))
        citizenship = c2.text_input("Citizenship", profile.get("citizenship", "Pakistan"))
        current_country = c1.text_input("Current country", profile.get("current_country", "Pakistan"))
        current_city = c2.text_input("Current city", profile.get("current_city", ""))
        email = c1.text_input("Email", profile.get("email", ""))
        phone = c2.text_input("Phone", profile.get("phone", ""))
        experience_years = st.number_input("Total relevant experience (years)", min_value=0, max_value=50, value=int(profile.get("experience_years", 0)))
        education = st.text_input("Education", profile.get("education", ""))
        english_level = st.selectbox("English level", ["Beginner", "A1", "A2", "B1", "B2", "C1", "C2"], index=["Beginner", "A1", "A2", "B1", "B2", "C1", "C2"].index(profile.get("english_level", "B1")) if profile.get("english_level", "B1") in ["Beginner", "A1", "A2", "B1", "B2", "C1", "C2"] else 3)
        target_countries = st.multiselect("Target countries", ["Romania", "Serbia", "Germany", "Poland", "France", "Netherlands", "Italy", "Spain", "Portugal", "Czechia", "Croatia", "Hungary", "Austria", "Sweden", "Norway", "Finland", "Australia", "New Zealand", "United Kingdom", "Canada", "Saudi Arabia", "UAE", "Qatar", "Oman"], default=profile.get("target_countries", []))
        all_roles = ["Heavy Diesel Mechanic", "Mobile Plant Mechanic", "Diesel Fitter", "Hydraulic Mechanic", "Auto Electrician", "Welder", "Construction Worker", "General Labourer", "Warehouse Assistant", "Warehouse Operative", "Forklift Operator", "Cleaner", "Mining Utility Worker", "Driller Offsider", "Farm Worker", "Truck Driver", "Electrician", "Plumber"]
        target_roles = st.multiselect("Target job roles", all_roles, default=[x for x in profile.get("target_roles", []) if x in all_roles])
        skills_raw = st.text_area("Skills (comma-separated)", ", ".join(profile.get("skills", [])))
        certificates_raw = st.text_area("Certificates / licences (comma-separated)", ", ".join(profile.get("certificates", [])))
        notes = st.text_area("Additional details / work preferences", profile.get("notes", ""))
        cv_file = st.file_uploader("Upload CV (PDF, DOCX or TXT)", type=["pdf", "docx", "txt"])
        submitted = st.form_submit_button("Save profile", type="primary")
    if submitted:
        cv_text = profile.get("cv_text", "")
        if cv_file is not None:
            try:
                cv_text = parse_cv(cv_file.name, cv_file.getvalue())
                st.session_state.cv_filename = cv_file.name
            except Exception as exc:
                st.error(f"Could not read CV: {exc}")
        updated = {
            **profile, "full_name": full_name, "citizenship": citizenship, "current_country": current_country,
            "current_city": current_city, "email": email, "phone": phone, "experience_years": int(experience_years),
            "education": education, "english_level": english_level, "target_countries": target_countries,
            "target_roles": target_roles, "skills": [x.strip() for x in skills_raw.split(",") if x.strip()],
            "certificates": [x.strip() for x in certificates_raw.split(",") if x.strip()], "notes": notes, "cv_text": cv_text,
        }
        save_profile(updated)
        st.success("Profile saved.")
        st.rerun()
    if profile.get("cv_text"):
        with st.expander("Extracted CV text preview"):
            st.text(profile["cv_text"][:8000])
            if st.button("AI-assisted CV profile extraction"):
                extracted = extract_profile_from_cv(profile["cv_text"], api_key=setting("GROQ_API_KEY"))
                st.json(extracted)

with tabs[2]:
    st.subheader("Find international jobs")
    st.write("Search public job feeds and build direct search links for major job boards and employer career pages.")
    query = st.text_input("Job search keywords", value=", ".join(profile.get("target_roles", [])[:3]))
    selected_countries = st.multiselect("Search countries", profile.get("target_countries", []), default=profile.get("target_countries", []))
    include_remote = st.checkbox("Include remote listings where relevant", value=False)
    max_results = st.slider("Maximum results per source", 10, 100, 40, 10)
    if st.button("⚡ Search jobs now", type="primary"):
        if not query.strip() or not selected_countries:
            st.error("Enter job keywords and select at least one country.")
        else:
            with st.spinner("Searching public job feeds and scoring matches..."):
                found, source_notes = search_jobs(query, selected_countries, max_results=max_results, include_remote=include_remote)
                for item in found:
                    item["match_score"], item["match_reasons"] = score_job(item, profile)
                found.sort(key=lambda x: x["match_score"], reverse=True)
                st.session_state.jobs = found
                save_jobs(found)
                st.session_state.source_notes = source_notes
    if st.session_state.get("source_notes"):
        for note in st.session_state.source_notes:
            st.caption(note)
    results = st.session_state.get("jobs", [])
    if results:
        st.success(f"Found {len(results)} listings from connected public sources.")
        for job in results:
            with st.container(border=True):
                h1, h2 = st.columns([4, 1])
                h1.markdown(f"**{job.get('title','Untitled')}**")
                h1.caption(f"{job.get('company','Company not listed')} • {job.get('location','Location not listed')} • {job.get('source','Source')}")
                h2.metric("Match", f"{job.get('match_score',0)}%")
                st.write(job.get("match_reasons", ""))
                if job.get("sponsorship_note"):
                    st.caption(job["sponsorship_note"])
                st.link_button("Open original vacancy", job["url"])
                if st.button("Save / track this job", key=f"save_{job['job_id']}"):
                    if db is not None:
                        db.saved_jobs.update_one({"job_id": job["job_id"]}, {"$set": job}, upsert=True)
                    else:
                        current = st.session_state.get("saved_jobs", [])
                        if not any(x["job_id"] == job["job_id"] for x in current):
                            current.append(job)
                        st.session_state.saved_jobs = current
                    st.success("Saved to your shortlist.")
    st.divider()
    st.markdown("**Search directly on major job platforms**")
    roles_for_links = query or "construction warehouse mining cleaner"
    country_for_links = selected_countries[0] if selected_countries else "Australia"
    q = quote_plus(f"{roles_for_links} {country_for_links} visa sponsorship")
    links = [
        ("LinkedIn Jobs", f"https://www.linkedin.com/jobs/search/?keywords={q}"),
        ("Indeed", f"https://www.indeed.com/jobs?q={q}"),
        ("SEEK Australia", f"https://www.seek.com.au/{quote_plus(roles_for_links)}-jobs/in-All-Australia"),
        ("EURES Europe", f"https://eures.europa.eu/index_en"),
        ("Google Jobs search", f"https://www.google.com/search?q={q}+jobs+visa+sponsorship"),
        ("Bing Jobs search", f"https://www.bing.com/search?q={q}+jobs+visa+sponsorship"),
    ]
    for label, url in links:
        st.markdown(f"- [{label}]({url})")

with tabs[3]:
    st.subheader("Saved jobs & application tracker")
    saved = load_saved_jobs()
    if not saved and st.session_state.get("saved_jobs"):
        saved = st.session_state.saved_jobs
    if saved:
        for job in saved:
            with st.container(border=True):
                st.markdown(f"**{job.get('title')}** — {job.get('company')} ({job.get('location')})")
                st.caption(f"Match {job.get('match_score', 0)}% • {job.get('source')} • {job.get('url')}")
                status = st.selectbox("Application status", ["New", "Shortlisted", "CV prepared", "Applied", "Employer replied", "Interview", "Rejected", "Offer"], key=f"status_{job.get('job_id')}")
                if st.button("Update status", key=f"status_save_{job.get('job_id')}"):
                    if db is not None:
                        db.applications.update_one({"job_id": job["job_id"]}, {"$set": {"job": job, "status": status, "updated_at": datetime.now(timezone.utc)}}, upsert=True)
                    st.success("Status updated.")
    else:
        st.info("No saved jobs yet. Search and save vacancies first.")

with tabs[4]:
    st.subheader("Employer outreach — draft only")
    st.warning("The app will not send messages automatically. Review every recipient and message yourself. Never send bulk unsolicited messages.")
    company = st.text_input("Employer / company name")
    contact = st.text_input("Public recruitment contact email or careers URL")
    job_title = st.text_input("Role you are asking about", value=profile.get("target_roles", ["Worker"])[0] if profile.get("target_roles") else "Worker")
    if st.button("Generate employer enquiry draft"):
        if not company.strip():
            st.error("Enter an employer name.")
        else:
            name = profile.get("full_name") or "Candidate"
            skills = ", ".join(profile.get("skills", [])) or "relevant practical skills (details available on request)"
            draft = f"""Subject: Enquiry about overseas applicants — {job_title}

Dear Recruitment Team at {company},

I am contacting you to ask whether you currently consider applicants based outside your country for {job_title} opportunities.

Candidate: {name}
Current location: {profile.get('current_country', 'Pakistan')}
Citizenship: {profile.get('citizenship', 'Pakistan')}
Relevant experience: {profile.get('experience_years', 0)} years
Skills: {skills}

Could you please confirm whether this vacancy is open to overseas applicants and whether the employer can support the applicable work authorisation process? I would be happy to share my CV and further documents if there is a suitable vacancy.

Kind regards,
{name}
{profile.get('email', '')}
{profile.get('phone', '')}
"""
            st.text_area("Review and copy your message", draft, height=300)
            st.download_button("Download email draft", draft, file_name="employer_enquiry.txt")

with tabs[5]:
    st.subheader("Work permit & visa pathway notes")
    st.caption("This is a research checklist, not legal advice or a visa eligibility decision.")
    destination = st.selectbox("Destination", profile.get("target_countries", ["Romania", "Serbia", "Australia"]))
    pathway = {
        "Romania": ["Find a genuine Romanian employer and obtain a written offer.", "Employer applies for the required work authorisation where applicable.", "After approval, check the Romanian long-stay employment visa (D/AM) requirements with the official consular portal.", "Prepare documents, apply, complete biometrics/interview if required, then follow residence-permit steps after arrival."],
        "Serbia": ["Find a genuine Serbian employer and written employment offer.", "Confirm the applicable single-permit / temporary residence and work authorisation route.", "Submit through the official Serbian government process and follow the consulate's visa-entry requirements for your nationality.", "Do not travel to work until the required authorisation is granted."],
        "Australia": ["Identify an employer and a role eligible for an applicable sponsored visa pathway.", "Employer must meet sponsorship and nomination requirements; occupation, experience, English and skills assessment may matter.", "Check current Department of Home Affairs rules and the exact visa subclass before paying any fee.", "Apply through the official ImmiAccount process if eligible."],
    }
    for step in pathway.get(destination, ["Find a genuine employer and verify the destination country's official work-permit and visa route.", "Check eligibility and documents on the official immigration website.", "Apply only through the official government channel."]):
        st.markdown(f"- {step}")
    official = {
        "Romania": "https://igi.mai.gov.ro/en/",
        "Serbia": "https://welcometoserbia.gov.rs/",
        "Australia": "https://immi.homeaffairs.gov.au/",
        "Germany": "https://www.make-it-in-germany.com/en/",
        "Poland": "https://www.gov.pl/web/family/work-permit",
        "Saudi Arabia": "https://www.hrsd.gov.sa/",
        "UAE": "https://u.ae/en/information-and-services/jobs",
    }
    if destination in official:
        st.link_button(f"Open {destination} official information", official[destination])

st.divider()
st.caption("Prototype limitations: job-feed coverage varies by country; a listing does not prove visa sponsorship. Verify employer identity, vacancy status, salary, contract and immigration route before taking action. Never pay an intermediary for a job offer.")
