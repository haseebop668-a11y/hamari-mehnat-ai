from __future__ import annotations

import hashlib
import os
import re
from typing import Any
from urllib.parse import quote_plus

import requests

TIMEOUT = 18
HEADERS = {"User-Agent": "PersonalJobHunterPrototype/0.1 (personal research tool)"}

def _id(*parts: str) -> str:
    raw = "|".join(str(x or "") for x in parts)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:20]

def _country_match(location: str, countries: list[str]) -> str | None:
    text = (location or "").lower()
    aliases = {
        "uk": ["united kingdom", "uk", "england", "scotland"],
        "uae": ["uae", "united arab emirates", "dubai", "abu dhabi"],
        "serbia": ["serbia", "belgrade", "novi sad"],
        "romania": ["romania", "bucharest", "cluj"],
        "australia": ["australia", "perth", "sydney", "brisbane", "melbourne"],
        "germany": ["germany", "berlin", "munich", "hamburg"],
    }
    for country in countries:
        candidates = aliases.get(country.lower(), [country.lower()])
        if any(a in text for a in candidates):
            return country
    return None

def _arbeitnow(countries: list[str], max_results: int) -> list[dict[str, Any]]:
    response = requests.get("https://www.arbeitnow.com/api/job-board-api", timeout=TIMEOUT, headers=HEADERS)
    response.raise_for_status()
    payload = response.json()
    jobs = []
    for item in payload.get("data", []):
        location = item.get("location") or ""
        country = _country_match(location, countries)
        if not country:
            continue
        url = item.get("url") or ""
        title = item.get("title") or ""
        company = item.get("company_name") or "Not specified"
        jobs.append({
            "job_id": _id("arbeitnow", url, title), "title": title, "company": company,
            "location": location, "country": country, "url": url, "source": "Arbeitnow",
            "description": re.sub(r"<[^>]+>", " ", item.get("description") or "")[:4000],
            "tags": item.get("tags") or [], "remote": bool(item.get("remote")),
            "sponsorship_note": "Sponsorship is not confirmed; check with employer.",
        })
        if len(jobs) >= max_results:
            break
    return jobs

def _serpapi(query: str, countries: list[str], max_results: int) -> list[dict[str, Any]]:
    key = os.getenv("SERPAPI_KEY", "")
    try:
        import streamlit as st
        key = st.secrets.get("SERPAPI_KEY", key)
    except Exception:
        pass
    if not key:
        return []
    jobs = []
    for country in countries[:8]:
        params = {"engine": "google_jobs", "q": f"{query} {country} visa sponsorship overseas applicants", "api_key": key}
        response = requests.get("https://serpapi.com/search.json", params=params, timeout=TIMEOUT, headers=HEADERS)
        response.raise_for_status()
        for item in response.json().get("jobs_results", [])[:max_results]:
            title = item.get("title") or ""
            company = item.get("company_name") or "Not specified"
            location = item.get("location") or country
            apply = item.get("apply_options") or []
            url = (apply[0].get("link") if apply else None) or item.get("share_link") or ""
            if not url:
                continue
            jobs.append({
                "job_id": _id("google_jobs", url, title), "title": title, "company": company,
                "location": location, "country": country, "url": url, "source": "Google Jobs (SerpApi)",
                "description": item.get("description") or "", "tags": [],
                "sponsorship_note": "Sponsorship is not confirmed; verify directly with employer.",
            })
    return jobs

def _adzuna(query: str, countries: list[str], max_results: int) -> list[dict[str, Any]]:
    app_id = os.getenv("ADZUNA_APP_ID", "")
    app_key = os.getenv("ADZUNA_APP_KEY", "")
    try:
        import streamlit as st
        app_id = st.secrets.get("ADZUNA_APP_ID", app_id)
        app_key = st.secrets.get("ADZUNA_APP_KEY", app_key)
    except Exception:
        pass
    if not app_id or not app_key:
        return []
    country_codes = {"Australia":"au","United Kingdom":"gb","Germany":"de","France":"fr","Netherlands":"nl","Poland":"pl","Italy":"it","Spain":"es","Austria":"at","Canada":"ca","New Zealand":"nz"}
    jobs = []
    for country in countries:
        code = country_codes.get(country)
        if not code:
            continue
        params = {"app_id": app_id, "app_key": app_key, "results_per_page": min(max_results, 50), "what": query, "where": country, "content-type": "application/json"}
        response = requests.get(f"https://api.adzuna.com/v1/api/jobs/{code}/search/1", params=params, timeout=TIMEOUT, headers=HEADERS)
        response.raise_for_status()
        for item in response.json().get("results", []):
            title = item.get("title") or ""
            company = (item.get("company") or {}).get("display_name") or "Not specified"
            location = (item.get("location") or {}).get("display_name") or country
            url = item.get("redirect_url") or ""
            if not url:
                continue
            jobs.append({
                "job_id": _id("adzuna", url, title), "title": title, "company": company,
                "location": location, "country": country, "url": url, "source": "Adzuna",
                "description": item.get("description") or "", "tags": [],
                "sponsorship_note": "Sponsorship is not confirmed; verify directly with employer.",
            })
    return jobs

def search_jobs(query: str, countries: list[str], max_results: int = 40, include_remote: bool = False):
    jobs, notes = [], []
    for name, fn in [
        ("Arbeitnow public feed", lambda: _arbeitnow(countries, max_results)),
        ("Google Jobs API (optional SerpApi)", lambda: _serpapi(query, countries, max_results)),
        ("Adzuna API (optional credentials)", lambda: _adzuna(query, countries, max_results)),
    ]:
        try:
            batch = fn()
            jobs.extend(batch)
            notes.append(f"{name}: {len(batch)} results.")
        except Exception as exc:
            notes.append(f"{name}: unavailable ({type(exc).__name__}).")
    if include_remote:
        notes.append("Remote listings are not yet queried separately; remote flag is retained when a provider returns it.")
    unique = {}
    for job in jobs:
        unique[job["job_id"]] = job
    return list(unique.values()), notes
