package com.hamari.mehenat.jobhunter.domain

enum class JobFreshness { CURRENT_WITH_EXPIRY, EXPIRY_NOT_PUBLISHED, EXPIRED }

object JobFreshnessService {
    fun assess(job: JobListing, nowEpochMs: Long = System.currentTimeMillis()): JobFreshness {
        val expiry = job.expiresAtEpochMs ?: return JobFreshness.EXPIRY_NOT_PUBLISHED
        return if (expiry <= nowEpochMs) JobFreshness.EXPIRED else JobFreshness.CURRENT_WITH_EXPIRY
    }

    fun removeExpired(
        jobs: List<JobListing>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<JobListing> = jobs.filter { assess(it, nowEpochMs) != JobFreshness.EXPIRED }
        .distinctBy { it.id }
}
