package com.hamari.mehenat.jobhunter.domain

/** Country plug-in contract: add a country without changing shared matching or UI. */
data class CountryConfig(
    val code: String,
    val displayName: String,
    val workRouteName: String,
    val immigrationAuthorityName: String,
    val immigrationAuthorityUrl: String,
    val employerVerificationName: String,
    val employerVerificationUrl: String?,
    val officialSources: List<CountryOfficialSource>
)
data class CountryOfficialSource(val title: String, val url: String, val purpose: String)

object CountryRegistry {
    private val configs = listOf(
        CountryConfig("NZ","New Zealand","Accredited Employer Work Visa (AEWV)","Immigration New Zealand",
            "https://www.immigration.govt.nz/visas/accredited-employer-work-visa/","Accredited employer check",
            "https://www.immigration.govt.nz/employ-migrants/accredited-employer-check/",
            listOf(CountryOfficialSource("AEWV","https://www.immigration.govt.nz/visas/accredited-employer-work-visa/","Visa and job-offer requirements."),
                CountryOfficialSource("Accredited employer check","https://www.immigration.govt.nz/employ-migrants/accredited-employer-check/","Employer accreditation; not proof that a specific vacancy can sponsor."))),
        CountryConfig("AU","Australia","Employer sponsored skilled visa pathways","Department of Home Affairs",
            "https://immi.homeaffairs.gov.au/visas/working-in-australia/skill-occupation-list","Approved sponsor and nomination checks",
            "https://immi.homeaffairs.gov.au/visas/employing-and-sponsoring-someone/sponsoring-workers",
            listOf(CountryOfficialSource("Skilled occupation list","https://immi.homeaffairs.gov.au/visas/working-in-australia/skill-occupation-list","Occupation and assessing-authority requirements."),
                CountryOfficialSource("Employer sponsored workers","https://immi.homeaffairs.gov.au/visas/employing-and-sponsoring-someone/sponsoring-workers","Sponsorship and nomination pathways."))),
        CountryConfig("GB","United Kingdom","Skilled Worker visa","UK Visas and Immigration",
            "https://www.gov.uk/skilled-worker-visa","Register of licensed worker sponsors",
            "https://www.gov.uk/government/publications/register-of-licensed-sponsors-workers",
            listOf(CountryOfficialSource("Skilled Worker visa","https://www.gov.uk/skilled-worker-visa","Job, salary and applicant requirements."),
                CountryOfficialSource("Licensed worker sponsors","https://www.gov.uk/government/publications/register-of-licensed-sponsors-workers","Check sponsor licence."))),
        CountryConfig("DE","Germany","Qualified professionals work visa and other applicable routes","Make it in Germany",
            "https://www.make-it-in-germany.com/en/visa-residence/types/work-qualified-professionals","Qualification and job-offer verification",null,
            listOf(CountryOfficialSource("Qualified professionals","https://www.make-it-in-germany.com/en/visa-residence/types/work-qualified-professionals","Qualification recognition and job requirements."),
                CountryOfficialSource("Visa and residence overview","https://www.make-it-in-germany.com/en/visa-residence","Official route overview."))),
        CountryConfig("CA","Canada","Employer-specific work permit (LMIA or exemption)","IRCC",
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada.html","LMIA and employer compliance information",
            "https://www.canada.ca/en/employment-social-development/services/foreign-workers.html",
            listOf(CountryOfficialSource("Work in Canada","https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada.html","Work permit routes."),
                CountryOfficialSource("Hire a temporary foreign worker","https://www.canada.ca/en/employment-social-development/services/foreign-workers.html","Employer LMIA and compliance.")))
    )
    fun all(): List<CountryConfig> = configs.toList()
    fun supportedNames(): List<String> = configs.map { it.displayName }
    fun findByCode(code: String): CountryConfig? = configs.firstOrNull { it.code.equals(code.trim(), true) }
    fun findByName(name: String): CountryConfig? = configs.firstOrNull { it.displayName.equals(name.trim(), true) }
    fun resolve(value: String): CountryConfig? = findByCode(value) ?: findByName(value) ?:
        configs.firstOrNull { it.code == "GB" && value.trim().equals("UK", true) }
}
