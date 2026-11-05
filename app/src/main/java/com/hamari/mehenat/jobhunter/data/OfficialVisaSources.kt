package com.hamari.mehenat.jobhunter.data

data class OfficialVisaSource(
    val country: String,
    val title: String,
    val url: String,
    val purpose: String,
    val checkedOn: String = "2026-11-05"
)

object OfficialVisaSources {
    val sources = listOf(
        OfficialVisaSource(
            "Australia", "Skilled occupation list",
            "https://immi.homeaffairs.gov.au/visas/working-in-australia/skill-occupation-list",
            "Check occupation lists and assessing authorities for relevant visa subclasses."
        ),
        OfficialVisaSource(
            "Australia", "How employers sponsor a worker",
            "https://immi.homeaffairs.gov.au/visas/employing-and-sponsoring-someone/sponsoring-workers/learn-about-sponsoring/how-to-sponsor-a-worker",
            "Employer sponsorship requires an eligible occupation, an approved sponsor/nomination pathway and other conditions."
        ),
        OfficialVisaSource(
            "United Kingdom", "Skilled Worker visa — eligible job and employer",
            "https://www.gov.uk/skilled-worker-visa/your-job",
            "Check job requirements and the approved sponsor requirement."
        ),
        OfficialVisaSource(
            "United Kingdom", "Register of licensed worker sponsors",
            "https://www.gov.uk/government/publications/register-of-licensed-sponsors-workers",
            "Official sponsor register; confirm the employer name, route and rating in the latest downloadable register."
        ),
        OfficialVisaSource(
            "Germany", "Work visa for qualified professionals",
            "https://www.make-it-in-germany.com/en/visa-recognition/visa-types/qualified-professionals",
            "Check qualification recognition, qualified job offer and residence requirements."
        ),
        OfficialVisaSource(
            "Canada", "Employer-specific work permits",
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada/employer-specific.html",
            "Check employer-specific permit conditions and whether an LMIA or exemption applies."
        ),
        OfficialVisaSource(
            "Canada", "Work permits — official overview",
            "https://www.canada.ca/en/immigration-refugees-citizenship/services/work-canada.html",
            "Official IRCC overview of temporary work permits and pathways."
        ),
        OfficialVisaSource(
            "New Zealand", "Accredited Employer Work Visa",
            "https://www.immigration.govt.nz/visas/accredited-employer-work-visa/",
            "Check job offer, accredited employer, work experience/qualification and other AEWV requirements."
        )
    )
}
