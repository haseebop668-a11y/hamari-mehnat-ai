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
    val officialSources: List<CountryOfficialSource>,
    val jobSources: List<CountryJobSource> = emptyList()
)
data class CountryOfficialSource(val title: String, val url: String, val purpose: String)
enum class CountrySourceAccess { PUBLIC_FEED, AUTHORIZED_API, BROWSER_HANDOFF, OFFICIAL_REGISTER }
data class CountryJobSource(
    val id: String,
    val displayName: String,
    val url: String,
    val access: CountrySourceAccess,
    val note: String
)

object CountryRegistry {
    private val configs = listOf(
        CountryConfig("NZ","New Zealand","Accredited Employer Work Visa (AEWV)","Immigration New Zealand",
            "https://www.immigration.govt.nz/visas/accredited-employer-work-visa/","Accredited employer check",
            "https://www.immigration.govt.nz/work/requirements-for-work-visas/approved-employers/accredited-employer-list/",
            listOf(
                CountryOfficialSource("AEWV","https://www.immigration.govt.nz/visas/accredited-employer-work-visa/","Visa and job-offer requirements."),
                CountryOfficialSource("Accredited employer list","https://www.immigration.govt.nz/work/requirements-for-work-visas/approved-employers/accredited-employer-list/","Public employer accreditation lookup; some employers opt out of publication."),
                CountryOfficialSource("AEWV job-check process","https://www.immigration.govt.nz/work/for-employers/getting-accreditation-or-approval-to-hire/employer-accreditation-for-the-aewv/aewv-employer-accreditation-and-job-check-process/","Job checks are vacancy-specific and distinct from employer accreditation."),
                CountryOfficialSource("AEWV employment agreement checklist","https://www.immigration.govt.nz/work/for-employers/getting-accreditation-or-approval-to-hire/employer-accreditation-for-the-aewv/applying-for-a-job-check-process-steps/applying-for-a-job-check-employer-checklist/","Required job, hours, pay, leave and agreement conditions.")),
            listOf(
                CountryJobSource("trade-assist","Trade Assist","https://tradeassist.net/find-work",CountrySourceAccess.BROWSER_HANDOFF,"Recruitment listings; ask recruiter whether the specific role supports an AEWV."),
                CountryJobSource("enterprise-nz","Enterprise Recruitment NZ","https://www.enterprise.co.nz/",CountrySourceAccess.BROWSER_HANDOFF,"Use the New Zealand agency site; confirm vacancy and work-rights policy."),
                CountryJobSource("tradestaff","Tradestaff","https://www.tradestaff.co.nz/",CountrySourceAccess.BROWSER_HANDOFF,"Check role-specific work-rights and sponsorship conditions."),
                CountryJobSource("seek-nz","SEEK New Zealand","https://www.seek.co.nz/",CountrySourceAccess.BROWSER_HANDOFF,"Use official browser hand-off unless authorized API access is granted.")
            )),
        CountryConfig("AU","Australia","Employer sponsored skilled visa pathways","Department of Home Affairs",
            "https://immi.homeaffairs.gov.au/visas/working-in-australia/skill-occupation-list","Approved sponsor and nomination checks",
            "https://immi.homeaffairs.gov.au/visas/employing-and-sponsoring-someone/sponsoring-workers",
            listOf(CountryOfficialSource("Skilled occupation list","https://immi.homeaffairs.gov.au/visas/working-in-australia/skill-occupation-list","Occupation and assessing-authority requirements."),
                CountryOfficialSource("Employer sponsored workers","https://immi.homeaffairs.gov.au/visas/employing-and-sponsoring-someone/sponsoring-workers","Sponsorship and nomination pathways.")),
            listOf(CountryJobSource("seek-au","SEEK Australia","https://www.seek.com.au/",CountrySourceAccess.BROWSER_HANDOFF,"Official job-search hand-off; no unauthorized scraping."))),
        CountryConfig("GB","United Kingdom","Skilled Worker visa","UK Visas and Immigration",
            "https://www.gov.uk/skilled-worker-visa","Register of licensed worker sponsors",
            "https://www.gov.uk/government/publications/register-of-licensed-sponsors-workers",
            listOf(CountryOfficialSource("Skilled Worker visa","https://www.gov.uk/skilled-worker-visa","Job, salary and applicant requirements."),
                CountryOfficialSource("Licensed worker sponsors","https://www.gov.uk/government/publications/register-of-licensed-sponsors-workers","Check sponsor licence.")),
            listOf(CountryJobSource("uk-find-a-job","Find a job","https://www.gov.uk/find-a-job",CountrySourceAccess.BROWSER_HANDOFF,"Official UK job-search service."))),
        CountryConfig("DE","Germany","Qualified professionals work visa and other applicable routes","Make it in Germany",
            "https://www.make-it-in-germany.com/en/visa-residence/types/work-qualified-professionals","Qualification and job-offer verification",null,
            listOf(CountryOfficialSource("Qualified professionals","https://www.make-it-in-germany.com/en/visa-residence/types/work-qualified-professionals","Qualification recognition and job requirements."),
                CountryOfficialSource("Visa and residence overview","https://www.make-it-in-germany.com/en/visa-residence","Official route overview.")),
            listOf(CountryJobSource("make-it-in-germany","Make it in Germany jobs","https://www.make-it-in-germany.com/en/working-in-germany/job-listings",CountrySourceAccess.BROWSER_HANDOFF,"Official international recruitment information and job listings."))),
        CountryConfig("CA","Canada","Employer-specific work permit (LMIA or exemption)","IRCC",
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada.html","LMIA and employer compliance information",
            "https://www.canada.ca/en/employment-social-development/services/foreign-workers.html",
            listOf(CountryOfficialSource("Work in Canada","https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada.html","Work permit routes."),
                CountryOfficialSource("Hire a temporary foreign worker","https://www.canada.ca/en/employment-social-development/services/foreign-workers.html","Employer LMIA and compliance.")),
            listOf(CountryJobSource("job-bank","Canada Job Bank","https://www.jobbank.gc.ca/findajob",CountrySourceAccess.BROWSER_HANDOFF,"Use filters for jobs open to international candidates; verify LMIA/employer claims.")))
    )
    fun all(): List<CountryConfig> = configs.toList()
    fun supportedNames(): List<String> = configs.map { it.displayName }
    fun findByCode(code: String): CountryConfig? = configs.firstOrNull { it.code.equals(code.trim(), true) }
    fun findByName(name: String): CountryConfig? = configs.firstOrNull { it.displayName.equals(name.trim(), true) }
    fun resolve(value: String): CountryConfig? = findByCode(value) ?: findByName(value) ?:
        configs.firstOrNull { it.code == "GB" && value.trim().equals("UK", true) }
}
