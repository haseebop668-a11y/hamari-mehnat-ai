package com.hamari.mehenat.jobhunter.domain

class JobMatcher {
    private val roleSynonyms = mapOf(
        "heavy diesel mechanic" to listOf("diesel mechanic", "heavy vehicle mechanic", "heavy equipment mechanic", "plant mechanic"),
        "mobile plant mechanic" to listOf("mobile equipment mechanic", "earthmoving mechanic"),
        "construction worker" to listOf("construction labourer", "construction laborer", "site worker", "general construction"),
        "general labourer" to listOf("general laborer", "trade assistant", "site labourer"),
        "warehouse assistant" to listOf("warehouse worker", "picker packer", "storeperson", "stores assistant"),
        "warehouse operative" to listOf("warehouse worker", "picker packer", "distribution operative"),
        "cleaner" to listOf("cleaning operative", "industrial cleaner", "housekeeping"),
        "mining utility worker" to listOf("mine site utility", "utility worker", "village utility", "mine services"),
        "driller offsider" to listOf("drillers assistant", "drilling assistant"),
        "welder" to listOf("fabricator", "metal fabricator", "welding"),
        "forklift operator" to listOf("forklift driver")
    )

    private val positiveSignals = listOf(
        "visa sponsorship", "sponsorship available", "visa support", "work permit support",
        "relocation support", "international applicants", "overseas applicants",
        "sponsor licence", "employer sponsored"
    )
    private val negativeSignals = listOf(
        "no sponsorship", "without sponsorship", "cannot sponsor", "unable to sponsor",
        "not able to sponsor", "must have the right to work", "no visa support",
        "not considering overseas applicants"
    )

    fun evaluate(job: JobListing, profile: JobProfile): JobMatch {
        val text = listOf(job.title, job.description, job.tags.joinToString(" "))
            .joinToString(" ").lowercase()
        val matchedRoles = profile.targetRoles.map { it.trim().lowercase() }.filter { role ->
            role.isNotBlank() && (text.contains(role) ||
                roleSynonyms[role].orEmpty().any(text::contains))
        }.distinct()
        val matchedSkills = profile.skills.map { it.trim().lowercase() }
            .filter { it.isNotBlank() && text.contains(it) }.distinct()

        val sponsorship = detectSponsorship(text)
        val countryAssessment = CountryJobPolicy().assess(job.country, job.title, job.description)
        var score = 20
        val reasons = mutableListOf<String>()

        if (matchedRoles.isNotEmpty()) {
            score += minOf(40, 15 + 7 * matchedRoles.size)
            reasons += "Role match: " + matchedRoles.take(3).joinToString(", ")
        }
        if (matchedSkills.isNotEmpty()) {
            score += minOf(20, 4 * matchedSkills.size)
            reasons += "Profile skills mentioned: " + matchedSkills.take(4).joinToString(", ")
        }
        val country = job.country?.trim()?.lowercase()
        if (!country.isNullOrBlank() && profile.targetCountries.any { it.trim().equals(country, true) }) {
            score += 15
            reasons += "Target country match"
        }
        when (sponsorship) {
            SponsorshipSignal.POSSIBLE_SIGNAL_REQUIRES_VERIFICATION -> {
                score += 5
                reasons += "Listing mentions a sponsorship signal; verify directly with the employer"
            }
            SponsorshipSignal.EXPLICITLY_UNAVAILABLE_OR_RIGHT_TO_WORK_REQUIRED -> {
                score -= 20
                reasons += "Listing may exclude sponsorship or require existing work rights"
            }
            SponsorshipSignal.NOT_MENTIONED -> Unit
        }
        countryAssessment.evidence.forEach { reasons += "Country policy evidence: $it" }
        reasons += countryAssessment.disclaimer
        reasons += "Relevance estimate only; not a hiring, work-rights or visa eligibility decision"
        return JobMatch(job.id, score.coerceIn(0, 100), sponsorship, reasons, countryAssessment)
    }

    fun detectSponsorship(text: String): SponsorshipSignal {
        val normalized = text.lowercase()
        if (negativeSignals.any(normalized::contains)) {
            return SponsorshipSignal.EXPLICITLY_UNAVAILABLE_OR_RIGHT_TO_WORK_REQUIRED
        }
        if (positiveSignals.any(normalized::contains)) {
            return SponsorshipSignal.POSSIBLE_SIGNAL_REQUIRES_VERIFICATION
        }
        return SponsorshipSignal.NOT_MENTIONED
    }
}
