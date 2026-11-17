package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.domain.JobFreshness
import com.hamari.mehenat.jobhunter.domain.JobFreshnessService
import com.hamari.mehenat.jobhunter.domain.JobListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobFreshnessServiceTest {
    private fun job(id: String, expiry: Long? = null) = JobListing(
        id = id, title = "Civil Labourer", company = "Example", location = "Auckland",
        country = "New Zealand", description = "Roadworks", sourceUrl = "https://example.test/$id",
        sourceName = "Test", retrievedAtEpochMs = 1_000L, expiresAtEpochMs = expiry
    )

    @Test fun identifiesExpiredAndUnpublishedExpirySeparately() {
        assertEquals(JobFreshness.EXPIRED, JobFreshnessService.assess(job("old", 999L), 1_000L))
        assertEquals(JobFreshness.CURRENT_WITH_EXPIRY, JobFreshnessService.assess(job("new", 1_001L), 1_000L))
        assertEquals(JobFreshness.EXPIRY_NOT_PUBLISHED, JobFreshnessService.assess(job("unknown"), 1_000L))
    }

    @Test fun removesOnlyExpiredJobsAndDeduplicatesByStableId() {
        val results = JobFreshnessService.removeExpired(
            listOf(job("expired", 999L), job("current", 1_001L), job("unknown"), job("current", 2_000L)),
            1_000L
        )
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "current" })
        assertTrue(results.any { it.id == "unknown" })
        assertFalse(results.any { it.id == "expired" })
    }
}
