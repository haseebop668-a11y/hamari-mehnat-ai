from __future__ import annotations
import hashlib, html, os, re
from typing import Any
from urllib.parse import quote_plus
import requests

TIMEOUT=18
HEADERS={"User-Agent":"PersonalJobHunterPrototype/0.2"}
COUNTRY_CODES={"Australia":"au","United Kingdom":"gb","Germany":"de","France":"fr","Netherlands":"nl","Poland":"pl","Italy":"it","Spain":"es","Austria":"at","Canada":"ca","New Zealand":"nz","Romania":"ro","Serbia":"rs","Croatia":"hr","Czechia":"cz","Hungary":"hu","Portugal":"pt","Belgium":"be","Ireland":"ie","Sweden":"se","Norway":"no","Finland":"fi","Denmark":"dk","Switzerland":"ch","Saudi Arabia":"sa","UAE":"ae","Qatar":"qa","Oman":"om","Bahrain":"bh","Kuwait":"kw"}
ALIASES={"uk":["united kingdom","england","scotland","wales"],"uae":["uae","united arab emirates","dubai","abu dhabi"],"serbia":["serbia","belgrade","novi sad"],"romania":["romania","bucharest","cluj","timisoara"],"australia":["australia","perth","sydney","brisbane","melbourne","queensland"],"germany":["germany","berlin","munich","hamburg","frankfurt"],"poland":["poland","warsaw","krakow","wroclaw"],"croatia":["croatia","zagreb","split"],"saudi arabia":["saudi arabia","riyadh","jeddah","dammam"]}

def _id(*parts:str)->str:
    return hashlib.sha256("|".join(str(x or "") for x in parts).encode()).hexdigest()[:24]
def _clean(v:Any)->str:
    return re.sub(r"\\s+"," ",html.unescape(re.sub(r"<[^>]+>"," ",str(v or "")))).strip()
def _country_match(location:str,countries:list[str])->str|None:
    text=(location or "").lower()
    for c in countries:
        aliases=ALIASES.get(c.lower(),[c.lower()])
        if any(re.search(r"(?<![a-z])"+re.escape(a)+r"(?![a-z])",text) for a in aliases): return c
    return None
def _job(title,company,location,url,source,description="",country="",tags=None,posted="",remote=False):
    return {"job_id":_id(source,url,title),"title":_clean(title) or "Untitled","company":_clean(company) or "Not specified","location":_clean(location) or "Not specified","country":country,"url":url,"source":source,"description":_clean(description)[:12000],"tags":tags or [],"posted_at":posted,"remote":bool(remote),"sponsorship_status":"not_mentioned","sponsorship_note":"Sponsorship is not confirmed; verify with employer."}

def _arbeitnow(query,countries,limit):
    jobs=[]
    for page in range(1,6):
        r=requests.get("https://www.arbeitnow.com/api/job-board-api",params={"page":page},timeout=TIMEOUT,headers=HEADERS); r.raise_for_status()
        items=r.json().get("data",[])
        if not items: break
        for x in items:
            loc=x.get("location",""); country=_country_match(loc,countries); title=x.get("title",""); desc=_clean(x.get("description",""))
            hay=f"{title} {desc} {' '.join(x.get('tags') or [])}".lower()
            if country and (not query or any(t in hay for t in query.lower().split() if len(t)>2)) and x.get("url"):
                jobs.append(_job(title,x.get("company_name"),loc,x["url"],"Arbeitnow",desc,country,x.get("tags"),x.get("created_at"),x.get("remote",False)))
            if len(jobs)>=limit:return jobs
    return jobs

def _remotive(query,countries,limit):
    r=requests.get("https://remotive.com/api/remote-jobs",params={"search":query,"limit":min(limit,100)},timeout=TIMEOUT,headers=HEADERS); r.raise_for_status()
    jobs=[]
    for x in r.json().get("jobs",[]):
        loc=x.get("candidate_required_location") or "Worldwide"
        country=_country_match(loc,countries)
        if country or loc.lower() in {"worldwide","anywhere","worldwide (remote)"}:
            if x.get("url"): jobs.append(_job(x.get("title"),x.get("company_name"),loc,x["url"],"Remotive",x.get("description"),country or "Remote",[x.get("category","")],x.get("publication_date"),True))
    return jobs[:limit]

def _adzuna(query,countries,limit):
    aid=os.getenv("ADZUNA_APP_ID",""); key=os.getenv("ADZUNA_APP_KEY","")
    if not aid or not key:return [],["Adzuna not connected: API credentials required."]
    jobs=[]; notes=[]
    for country in countries:
        code=COUNTRY_CODES.get(country)
        if not code:continue
        try:
            p={"app_id":aid,"app_key":key,"results_per_page":min(limit,50),"what":query,"where":country,"content-type":"application/json"}
            r=requests.get(f"https://api.adzuna.com/v1/api/jobs/{code}/search/1",params=p,timeout=TIMEOUT,headers=HEADERS); r.raise_for_status()
            for x in r.json().get("results",[]):
                loc=(x.get("location") or {}).get("display_name") or country
                url=x.get("redirect_url","")
                if url:jobs.append(_job(x.get("title"),(x.get("company") or {}).get("display_name"),loc,url,"Adzuna",x.get("description"),country,[(x.get("category") or {}).get("label","")],x.get("created")))
        except Exception as e:notes.append(f"Adzuna {country}: {type(e).__name__}")
    return jobs,notes

def _serpapi(query,countries,limit):
    key=os.getenv("SERPAPI_KEY","")
    if not key:return [],["Google Jobs not connected: SerpApi key required."]
    jobs=[]; notes=[]
    for country in countries[:12]:
        try:
            p={"engine":"google_jobs","q":f"{query} {country} overseas applicants visa sponsorship","api_key":key}
            r=requests.get("https://serpapi.com/search.json",params=p,timeout=TIMEOUT,headers=HEADERS); r.raise_for_status()
            for x in r.json().get("jobs_results",[])[:limit]:
                opts=x.get("apply_options") or []; url=next((o.get("link") for o in opts if o.get("link")),None) or x.get("share_link","")
                if url:jobs.append(_job(x.get("title"),x.get("company_name"),x.get("location") or country,url,"Google Jobs (SerpApi)",x.get("description"),country))
        except Exception as e:notes.append(f"Google Jobs {country}: {type(e).__name__}")
    return jobs,notes

def _greenhouse(query,countries,limit):
    tokens=[x.strip() for x in os.getenv("GREENHOUSE_BOARD_TOKENS","").split(",") if x.strip()]
    if not tokens:return [],["Greenhouse: add verified company board tokens to GREENHOUSE_BOARD_TOKENS."]
    jobs=[]; notes=[]
    for token in tokens[:100]:
        try:
            r=requests.get(f"https://boards-api.greenhouse.io/v1/boards/{quote_plus(token)}/jobs",params={"content":"true"},timeout=TIMEOUT,headers=HEADERS)
            if r.status_code==404:continue
            r.raise_for_status()
            for x in r.json().get("jobs",[]):
                loc=(x.get("location") or {}).get("name",""); country=_country_match(loc,countries); title=x.get("title",""); desc=_clean(x.get("content",""))
                if country and (not query or any(t in f"{title} {desc}".lower() for t in query.lower().split() if len(t)>2)) and x.get("absolute_url"):
                    jobs.append(_job(title,token,loc,x["absolute_url"],"Greenhouse public board",desc,country,[],x.get("updated_at")))
                if len(jobs)>=limit:return jobs,notes
        except Exception as e:notes.append(f"Greenhouse {token}: {type(e).__name__}")
    return jobs,notes

def _lever(query,countries,limit):
    sites=[x.strip() for x in os.getenv("LEVER_SITE_NAMES","").split(",") if x.strip()]
    if not sites:return [],["Lever: add verified company site names to LEVER_SITE_NAMES."]
    jobs=[]; notes=[]
    for site in sites[:100]:
        try:
            r=requests.get(f"https://api.lever.co/v0/postings/{quote_plus(site)}",params={"mode":"json"},timeout=TIMEOUT,headers=HEADERS); r.raise_for_status()
            for x in r.json():
                loc=(x.get("categories") or {}).get("location",""); country=_country_match(loc,countries); title=x.get("text",""); desc=_clean(x.get("descriptionPlain") or x.get("description",""))
                if country and (not query or any(t in f"{title} {desc}".lower() for t in query.lower().split() if len(t)>2)):
                    url=x.get("hostedUrl") or x.get("applyUrl","")
                    if url:jobs.append(_job(title,site,loc,url,"Lever public board",desc,country,[],str(x.get("createdAt",""))))
                if len(jobs)>=limit:return jobs,notes
        except Exception as e:notes.append(f"Lever {site}: {type(e).__name__}")
    return jobs,notes

def search_jobs(query:str,countries:list[str],max_results:int=40,include_remote:bool=False):
    jobs=[]; notes=[]
    sources=[("Arbeitnow",lambda:(_arbeitnow(query,countries,max_results),[])),("Remotive",lambda:(_remotive(query,countries,max_results) if include_remote else [],[])),("Adzuna",lambda:_adzuna(query,countries,max_results)),("Google Jobs",lambda:_serpapi(query,countries,max_results)),("Greenhouse",lambda:_greenhouse(query,countries,max_results)),("Lever",lambda:_lever(query,countries,max_results))]
    for name,fn in sources:
        try:
            batch,extra=fn(); jobs.extend(batch); notes.append(f"{name}: {len(batch)} results."); notes.extend(extra)
        except Exception as e:notes.append(f"{name}: unavailable ({type(e).__name__}).")
    unique={j["job_id"]:j for j in jobs if j.get("url")}
    return list(unique.values()),notes
