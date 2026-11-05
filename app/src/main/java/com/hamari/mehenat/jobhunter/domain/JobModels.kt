package com.hamari.mehenat.jobhunter.domain

data class JobListing(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val country: String?,
    val description: String,
    val tags: List<String> = emptyList(),
    val sourceUrl: String,
    val sourceName: String,
    val retrievedAtEpochMs: Long,
    val sourcePageUrl: String? = null,
    val expiresAtEpochMs: Long? = null,
    val verificationUrl: String? = null
)

data class JobProfile(
    val targetRoles: List<String>,
    val targetCountries: List<String>,
    val skills: List<String>
)

enum class SponsorshipSignal {
    POSSIBLE_SIGNAL_REQUIRES_VERIFICATION,
    EXPLICITLY_UNAVAILABLE_OR_RIGHT_TO_WORK_REQUIRED,
    NOT_MENTIONED
}

data class JobMatch(
    val jobId: String,
    val score: Int,
    val sponsorship: SponsorshipSignal,
    val reasons: List<String>
)
