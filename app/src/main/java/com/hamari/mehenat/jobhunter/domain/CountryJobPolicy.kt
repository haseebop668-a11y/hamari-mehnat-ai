package com.hamari.mehenat.jobhunter.domain

enum class CountryJobEligibility {
    POTENTIAL_REQUIRES_EMPLOYER_CONFIRMATION, WORK_RIGHTS_RESTRICTION,
    NO_SPONSORSHIP_EVIDENCE, COUNTRY_NOT_CONFIGURED
}
data class CountryJobAssessment(
    val countryCode: String?,
    val status: CountryJobEligibility,
    val evidence: List<String>,
    val disclaimer: String
)

/** Conservative ad-language triage. It never verifies an employer or grants visa eligibility. */
class CountryJobPolicy {
    private val hardRestrictions = listOf(
        "citizens only", "nz citizens only", "permanent residents only",
        "must have nz residency", "must be a new zealand resident",
        "must already hold the right to work", "must have unrestricted work rights",
        "no visa sponsorship", "unable to sponsor", "cannot sponsor",
        "not considering overseas applicants"
    )
    private val sponsorshipSignals = listOf(
        "visa sponsorship available", "sponsorship available", "accredited employer",
        "overseas applicants welcome", "international applicants welcome",
        "employer sponsored", "work visa support"
    )
    private val ambiguousSignals = listOf(
        "right to work", "valid work visa required", "eligible to work",
        "work authorization required", "work authorisation required"
    )
    fun assess(countryName: String?, title: String, description: String): CountryJobAssessment {
        val country = countryName?.let(CountryRegistry::resolve)
            ?: return CountryJobAssessment(null, CountryJobEligibility.COUNTRY_NOT_CONFIGURED,
                emptyList(), "No country-specific policy is configured.")
        val text = "$title $description".lowercase().replace(Regex("\\s+"), " ")
        val restrictions = hardRestrictions.filter(text::contains)
        if (restrictions.isNotEmpty()) return CountryJobAssessment(country.code,
            CountryJobEligibility.WORK_RIGHTS_RESTRICTION, restrictions,
            "Strong restriction wording found. Confirm generic work-rights wording with the employer.")
        val signals = sponsorshipSignals.filter(text::contains)
        if (signals.isNotEmpty()) return CountryJobAssessment(country.code,
            CountryJobEligibility.POTENTIAL_REQUIRES_EMPLOYER_CONFIRMATION, signals,
            "A sponsorship phrase is a lead only. Verify employer status and this specific vacancy.")
        val ambiguous = ambiguousSignals.filter(text::contains)
        return CountryJobAssessment(country.code,
            if (ambiguous.isNotEmpty()) CountryJobEligibility.POTENTIAL_REQUIRES_EMPLOYER_CONFIRMATION
            else CountryJobEligibility.NO_SPONSORSHIP_EVIDENCE, ambiguous,
            "No visa decision is made from ad wording. Check official rules and applicant requirements.")
    }
}
