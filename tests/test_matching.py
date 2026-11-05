import unittest
from matching import score_job,sponsorship_signal,parse_cv
class MatchingTests(unittest.TestCase):
 def setUp(self): self.profile={"target_roles":["Warehouse Assistant","Construction Worker"],"skills":["forklift","inventory"],"target_countries":["Australia"]}
 def test_role_country_skills_and_sponsor_signal(self):
  j={"title":"Warehouse Worker","description":"Forklift and inventory. Visa sponsorship available.","country":"Australia"}
  score,why=score_job(j,self.profile); self.assertGreaterEqual(score,60); self.assertIn("sponsorship",why.lower()); self.assertEqual(j["sponsorship_status"],"possible_signal_verify_with_employer")
 def test_no_sponsorship_penalty(self):
  j={"title":"Construction Labourer","description":"Must have right to work. No sponsorship available.","country":"Australia"}
  score,_=score_job(j,self.profile); self.assertLess(score,60); self.assertEqual(sponsorship_signal(j),"explicitly_not_confirmed_or_unavailable")
 def test_unknown_not_assumed(self):
  self.assertEqual(sponsorship_signal({"title":"Cleaner","description":"Cleaning duties"}),"not_mentioned")
 def test_txt_cv(self): self.assertIn("Welder",parse_cv("cv.txt",b"Welder workshop experience"))
 def test_unsupported_cv(self):
  with self.assertRaises(ValueError):parse_cv("cv.exe",b"bad")
if __name__=="__main__":unittest.main()
