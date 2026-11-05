import unittest
from job_sources import _clean, _country_match, _job

class JobSourceTests(unittest.TestCase):
    def test_html_and_whitespace_cleanup(self):
        self.assertEqual(_clean("<p>Warehouse</p>   assistant"), "Warehouse assistant")
    def test_country_aliases(self):
        self.assertEqual(_country_match("Perth, Western Australia", ["Australia"]), "Australia")
        self.assertEqual(_country_match("Bucharest, Romania", ["Romania"]), "Romania")
        self.assertIsNone(_country_match("Toronto, Canada", ["Germany"]))
    def test_normalized_job_has_stable_identifier(self):
        a = _job("Cleaner", "Acme", "Sydney, Australia", "https://example.com/job/1", "Test", "Clean site", "Australia")
        b = _job("Cleaner", "Acme", "Sydney, Australia", "https://example.com/job/1", "Test", "Clean site", "Australia")
        self.assertEqual(a["job_id"], b["job_id"])
        self.assertEqual(a["country"], "Australia")
        self.assertEqual(a["sponsorship_status"], "not_mentioned")

if __name__ == "__main__":
    unittest.main()
