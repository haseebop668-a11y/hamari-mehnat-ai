from __future__ import annotations

import io
import re
from typing import Any

def parse_cv(filename: str, content: bytes) -> str:
    name = filename.lower()
    if name.endswith(".pdf"):
        from pypdf import PdfReader
        reader = PdfReader(io.BytesIO(content))
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    elif name.endswith(".docx"):
        from docx import Document
        doc = Document(io.BytesIO(content))
        text = "\n".join(p.text for p in doc.paragraphs)
        for table in doc.tables:
            for row in table.rows:
                text += "\n" + " | ".join(cell.text for cell in row.cells)
    elif name.endswith(".txt"):
        text = content.decode("utf-8", errors="replace")
    else:
        raise ValueError("Supported formats: PDF, DOCX, TXT.")
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    if not text:
        raise ValueError("No readable text found. If the PDF is scanned, upload a text-based PDF or DOCX.")
    return text[:50000]

def extract_profile_from_cv(text: str) -> dict[str, Any]:
    lines = [x.strip(" •\t-") for x in text.splitlines() if x.strip()]
    email = re.search(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", text, re.I)
    phone = re.search(r"(?<!\w)(?:\+?\d[\d\s().-]{7,}\d)", text)
    skill_terms = ["diesel", "mechanic", "hydraulic", "excavator", "forklift", "welding", "construction", "warehouse", "cleaning", "electrical", "driving", "maintenance", "diagnostics"]
    skills = sorted({term.title() for term in skill_terms if term in text.lower()})
    return {
        "email_candidate": email.group(0) if email else "",
        "phone_candidate": phone.group(0).strip() if phone else "",
        "detected_skill_keywords": skills,
        "first_lines_for_review": lines[:25],
        "notice": "Draft extraction only. Review and correct every field; this does not verify qualifications or work experience.",
    }

def score_job(job: dict[str, Any], profile: dict[str, Any]) -> tuple[int, str]:
    title = (job.get("title") or "").lower()
    description = (job.get("description") or "").lower()
    tags = " ".join(job.get("tags") or []).lower()
    roles = [x.lower() for x in profile.get("target_roles", [])]
    skills = [x.lower() for x in profile.get("skills", [])]
    text = f"{title} {description} {tags}"
    role_hits = [r for r in roles if r in title or r in text]
    skill_hits = [s for s in skills if s in text]
    score = 25
    reasons = []
    if role_hits:
        score += min(40, 15 + 8 * len(role_hits))
        reasons.append("Role match: " + ", ".join(role_hits[:3]))
    if skill_hits:
        score += min(20, 5 * len(skill_hits))
        reasons.append("Skills mentioned: " + ", ".join(skill_hits[:4]))
    country = (job.get("country") or "").lower()
    targets = [x.lower() for x in profile.get("target_countries", [])]
    if country in targets:
        score += 15
        reasons.append("Target country match")
    if any(x in text for x in ["visa sponsorship", "sponsorship available", "work permit", "relocation"]):
        score += 10
        reasons.append("Listing mentions sponsorship / work authorisation (verify)")
    score = min(100, score)
    return score, " • ".join(reasons) if reasons else "General listing; review duties and eligibility manually."
