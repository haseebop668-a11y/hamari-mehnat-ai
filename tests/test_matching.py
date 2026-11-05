import unittest

from matching import extract_profile_from_cv, parse_cv, score_job


class MatchingTests(unittest.TestCase):
    def test_parse_text_cv(self):
        text = parse_cv("candidate.txt", b"Heavy Diesel Mechanic\nEmail: test@example.com")
        self.assertIn("Heavy Diesel Mechanic", text)

    def test_extract_profile_without_api_key_is_safe_fallback(self):
        result = extract_profile_from_cv("Diesel mechanic with hydraulic maintenance experience. test@example.com", api_key="")
        self.assertIn("Diesel", result["detected_skill_keywords"])
        self.assertEqual(result["email_candidate"], "test@example.com")
        self.assertIn("not configured", result["notice"])

    def test_relevant_job_scores_above_unrelated_job(self):
        profile = {
            "target_roles": ["Heavy Diesel Mechanic"],
            "skills": ["hydraulics", "diagnostics"],
            "target_countries": ["Australia"],
        }
        good = {
            "title": "Heavy Diesel Mechanic",
            "description": "Hydraulics and diagnostics. Visa sponsorship available.",
            "country": "Australia",
            "tags": [],
        }
        bad = {"title": "Graphic Designer", "description": "Brand design", "country": "France", "tags": []}
        good_score, _ = score_job(good, profile)
        bad_score, _ = score_job(bad, profile)
        self.assertGreater(good_score, bad_score)
        self.assertLessEqual(good_score, 100)


if __name__ == "__main__":
    unittest.main()
