from __future__ import annotations
import io,json,os,re
from typing import Any
import requests

ROLE_SYNONYMS={
"heavy diesel mechanic":["diesel mechanic","heavy vehicle mechanic","heavy equipment mechanic","plant mechanic"],
"mobile plant mechanic":["mobile equipment mechanic","earthmoving mechanic"],
"construction worker":["construction labourer","construction laborer","site worker","general construction"],
"general labourer":["general laborer","trade assistant","site labourer"],
"warehouse assistant":["warehouse worker","picker packer","storeperson","stores assistant"],
"warehouse operative":["warehouse worker","picker packer","distribution operative"],
"cleaner":["cleaning operative","industrial cleaner","housekeeping"],
"mining utility worker":["mine site utility","utility worker","village utility","mine services"],
"driller offsider":["drillers assistant","drilling assistant"],
"welder":["fabricator","metal fabricator","welding"],
"forklift operator":["forklift driver"]}
POSITIVE=("visa sponsorship","sponsorship available","visa support","work permit support","relocation support","international applicants","overseas applicants","sponsor licence","employer sponsored")
NEGATIVE=("no sponsorship","without sponsorship","cannot sponsor","unable to sponsor","not able to sponsor","must have the right to work","no visa support","not considering overseas applicants")

def parse_cv(filename:str,content:bytes)->str:
    name=filename.lower()
    if name.endswith(".pdf"):
        from pypdf import PdfReader
        text="\n".join(p.extract_text() or "" for p in PdfReader(io.BytesIO(content)).pages)
    elif name.endswith(".docx"):
        from docx import Document
        d=Document(io.BytesIO(content)); text="\n".join(p.text for p in d.paragraphs)
        for table in d.tables:
            for row in table.rows:text+="\n"+" | ".join(c.text for c in row.cells)
    elif name.endswith(".txt"):text=content.decode("utf-8",errors="replace")
    else:raise ValueError("Supported formats: PDF, DOCX, TXT.")
    text=re.sub(r"\n{3,}","\n\n",text).strip()
    if not text:raise ValueError("No readable text found. Scanned PDFs need OCR before extraction.")
    return text[:50000]

def extract_profile_from_cv(text:str,api_key:str="")->dict[str,Any]:
    api_key=api_key or os.getenv("GROQ_API_KEY","")
    if api_key:
        try:
            r=requests.post("https://api.groq.com/openai/v1/chat/completions",headers={"Authorization":f"Bearer {api_key}","Content-Type":"application/json"},json={"model":"llama-3.3-70b-versatile","temperature":0.1,"response_format":{"type":"json_object"},"messages":[{"role":"system","content":"Extract only facts explicitly stated in the CV. Never infer qualifications, licenses, experience, citizenship or language. Return JSON: full_name,email_candidate,phone_candidate,experience_summary,education,skills(array),certificates(array),job_titles(array),missing_or_unclear(array)."},{"role":"user","content":text[:18000]}]},timeout=35)
            r.raise_for_status(); data=json.loads(r.json()["choices"][0]["message"]["content"])
            data["notice"]="AI extraction draft; verify every field against source documents. Nothing is independently credential-verified."; return data
        except Exception as e:err=f"AI extraction unavailable ({type(e).__name__}); rule-based extraction shown."
    else:err="GROQ_API_KEY not configured; rule-based extraction shown."
    lines=[x.strip(" •\t-") for x in text.splitlines() if x.strip()]
    email=re.search(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}",text,re.I)
    phone=re.search(r"(?<!\w)(?:\+?\d[\d\s().-]{7,}\d)",text)
    terms=["diesel","mechanic","hydraulic","excavator","forklift","welding","construction","warehouse","cleaning","electrical","driving","maintenance","diagnostics","mining","labourer","laborer"]
    return {"email_candidate":email.group(0) if email else "","phone_candidate":phone.group(0).strip() if phone else "","detected_skill_keywords":sorted({t.title() for t in terms if t in text.lower()}),"first_lines_for_review":lines[:25],"notice":err+" Review every field; this does not verify experience or qualifications."}

def sponsorship_signal(job:dict[str,Any])->str:
    text=f"{job.get('title','')} {job.get('description','')}".lower()
    if any(x in text for x in NEGATIVE):return "explicitly_not_confirmed_or_unavailable"
    if any(x in text for x in POSITIVE):return "possible_signal_verify_with_employer"
    return "not_mentioned"

def score_job(job:dict[str,Any],profile:dict[str,Any])->tuple[int,str]:
    title=(job.get("title") or "").lower(); text=f"{title} {job.get('description') or ''} {' '.join(job.get('tags') or [])}".lower()
    role_hits=[]
    for role in [x.lower() for x in profile.get("target_roles",[])]:
        if any(v in text for v in [role]+ROLE_SYNONYMS.get(role,[])):role_hits.append(role)
    skills=[s.lower() for s in profile.get("skills",[]) if s]
    skill_hits=[s for s in skills if s in text]
    score=20; reasons=[]
    if role_hits:score+=min(40,15+7*len(role_hits)); reasons.append("Role match: "+", ".join(role_hits[:3]))
    if skill_hits:score+=min(20,4*len(skill_hits)); reasons.append("CV skills mentioned: "+", ".join(skill_hits[:4]))
    country=(job.get("country") or "").lower()
    if country and country in [x.lower() for x in profile.get("target_countries",[])]:score+=15; reasons.append("Target country match")
    signal=sponsorship_signal(job); job["sponsorship_status"]=signal
    if signal=="possible_signal_verify_with_employer":score+=5; reasons.append("Sponsorship signal in listing; verify with employer")
    elif signal=="explicitly_not_confirmed_or_unavailable":score-=20; reasons.append("May require existing work rights or exclude sponsorship")
    score=max(0,min(100,score))
    reasons.append("Relevance estimate only; not a hiring or visa eligibility decision")
    return score," • ".join(reasons)
