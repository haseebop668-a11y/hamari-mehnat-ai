package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.domain.JobListing
import com.hamari.mehenat.jobhunter.domain.JobMatcher
import com.hamari.mehenat.jobhunter.domain.JobProfile
import com.hamari.mehenat.jobhunter.domain.SponsorshipSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobMatcherTest {
    private val matcher = JobMatcher()
    private val profile = JobProfile(
        targetRoles = listOf("heavy diesel mechanic"),
        targetCountries = listOf("Australia"),
        skills = listOf("hydraulics", "maintenance")
    )

    private fun job(description: String, country: String? = "Australia") = JobListing(
        id = "job-1", title = "Heavy Equipment Mechanic", company = "Example Co",
        location = "Perth", country = country, description = description,
        sourceUrl = "https://example.com/job", sourceName = "fixture",
        retrievedAtEpochMs = 1L
    )

    @Test fun recognizesRoleSynonymAndTargetCountry() {
        val result = matcher.evaluate(job("Hydraulics and maintenance experience preferred"), profile)
        assertTrue(result.score >= 70)
        assertTrue(result.reasons.any { it.contains("Role match") })
        assertTrue(result.reasons.contains("Target country match"))
    }

    @Test fun sponsorshipSignalIsOnlyPossibleNotGuaranteed() {
        val result = matcher.evaluate(job("Visa sponsorship available for suitable applicants"), profile)
        assertEquals(SponsorshipSignal.POSSIBLE_SIGNAL_REQUIRES_VERIFICATION, result.sponsorship)
        assertTrue(result.reasons.any { it.contains("verify directly") })
    }

    @Test fun negativeSponsorshipSignalIsPenalized() {
        val result = matcher.evaluate(job("Must have the right to work. No sponsorship."), profile)
        assertEquals(SponsorshipSignal.EXPLICITLY_UNAVAILABLE_OR_RIGHT_TO_WORK_REQUIRED, result.sponsorship)
        assertTrue(result.score < 70)
    }

    @Test fun missingCountryDoesNotReceiveCountryPoints() {
        val result = matcher.evaluate(job("General duties", country = null), profile)
        assertTrue(result.reasons.none { it == "Target country match" })
    }
}
