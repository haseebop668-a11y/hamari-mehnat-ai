package com.hamari.mehenat.jobhunter.domain

import java.util.Locale

enum class ContractFindingSeverity { INFO, WARNING, HIGH_RISK }
enum class ContractCheckStatus { PRESENT, MISSING, REVIEW_REQUIRED, FLAGGED }

data class ContractFinding(
    val code: String,
    val title: String,
    val detail: String,
    val severity: ContractFindingSeverity,
    val status: ContractCheckStatus
)

data class EmploymentContractReport(
    val findings: List<ContractFinding>,
    val detectedWeeklyHours: Double?,
    val detectedHourlyPayNzd: Double?,
    val hasHighRiskFlags: Boolean,
    val summary: String
)

/**
 * Deterministic first-pass text review for an AEWV employment agreement.
 * It is a checklist, not legal advice or proof that INZ has approved a job check.
 */
class EmploymentContractAnalyzer {
    fun analyze(
        contractText: String,
        expectedEmployerName: String? = null,
        expectedJobTitle: String? = null,
        advertisedHourlyPayNzd: Double? = null
    ): EmploymentContractReport {
        val text = contractText.trim()
        val normalized = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val findings = mutableListOf<ContractFinding>()
        if (text.isBlank()) {
            return EmploymentContractReport(
                findings = listOf(ContractFinding("empty_contract", "No contract text", "Paste readable contract text to run the checklist.", ContractFindingSeverity.WARNING, ContractCheckStatus.MISSING)),
                detectedWeeklyHours = null,
                detectedHourlyPayNzd = null,
                hasHighRiskFlags = false,
                summary = "No contract content was supplied."
            )
        }

        fun present(code: String, title: String, terms: List<String>, missingDetail: String) {
            val found = terms.any { normalized.contains(it) }
            findings += ContractFinding(
                code, title,
                if (found) "A matching section or keyword was found. Confirm the full clause and its meaning."
                else missingDetail,
                if (found) ContractFindingSeverity.INFO else ContractFindingSeverity.WARNING,
                if (found) ContractCheckStatus.PRESENT else ContractCheckStatus.MISSING
            )
        }

        present("employer_identity", "Employer legal name", listOf("employer", "company name", "trading as", "nzbn"), "Confirm the legal employer name and NZBN; compare them with the INZ accreditation result.")
        present("employee_identity", "Employee identity", listOf("employee", "worker name", "full name"), "Check that the agreement identifies you by your correct legal name.")
        present("job_title", "Job title", listOf("position", "job title", "role"), "The job title should be stated clearly.")
        present("work_location", "Work location", listOf("place of work", "work location", "work address", "site address"), "The actual work location or locations should be specified.")
        present("duties", "Duties and responsibilities", listOf("duties", "responsibilities", "description of work", "tasks"), "The work and responsibilities should be described in enough detail.")
        present("hours", "Hours of work", listOf("hours per week", "hours of work", "guaranteed hours", "minimum hours"), "Check the guaranteed weekly hours; AEWV work must be at least 30 hours per week.")
        present("pay", "Pay rate and method", listOf("hourly rate", "salary", "wages", "remuneration", "pay rate"), "The pay rate or salary must be clear and comparable with the job offer and market rate.")
        present("pay_period", "Pay period", listOf("pay period", "paid weekly", "paid fortnightly", "paid monthly", "pay frequency"), "The agreement should state how often you are paid.")
        present("overtime", "Overtime terms", listOf("overtime", "additional hours", "maximum hours"), "Check when overtime begins, maximum hours, and the applicable overtime rate.")
        present("leave", "Paid leave entitlements", listOf("annual holidays", "annual leave", "sick leave", "public holidays"), "Check annual holidays, sick leave and public holiday entitlements.")
        present("duration", "Employment duration", listOf("permanent", "fixed term", "fixed-term", "employment period", "start date"), "Confirm whether the agreement is permanent or fixed-term and its start/duration.")
        present("problem_resolution", "Employment problem resolution", listOf("employment relationship problem", "personal grievance", "mediation"), "Check that the agreement explains how to get help with employment relationship problems.")

        val hours = Regex("""(?i)(?:minimum|guaranteed)?\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*(?:hours?|hrs?)\\s*(?:per|/)\\s*week""")
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (hours != null) {
            findings += if (hours < 30.0) {
                ContractFinding("aewv_hours_below_minimum", "Weekly hours below AEWV minimum", "Detected $hours hours per week. AEWV employment must provide at least 30 hours per week.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
            } else {
                ContractFinding("aewv_hours_threshold", "Weekly hours threshold", "Detected $hours hours per week, meeting the 30-hour minimum threshold. Confirm these are guaranteed hours.", ContractFindingSeverity.INFO, ContractCheckStatus.PRESENT)
            }
        }

        val payMatches = Regex("""(?i)(?:NZD\\s*)?\\$\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?)\\s*(?:nzd)?\\s*(?:per|/)\\s*(?:hour|hr)""").findAll(text).toList()
        val hourlyPay = payMatches.firstOrNull()?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        if (hourlyPay == null) {
            findings += ContractFinding("hourly_pay_not_detected", "Hourly pay not detected", "No clear NZD hourly rate was detected. If paid a salary, compare the guaranteed hours and effective hourly rate manually.", ContractFindingSeverity.WARNING, ContractCheckStatus.REVIEW_REQUIRED)
        } else if (advertisedHourlyPayNzd != null && kotlin.math.abs(hourlyPay - advertisedHourlyPayNzd) >= 0.01) {
            findings += ContractFinding("pay_mismatch", "Advertised and contract pay differ", "Detected NZD $hourlyPay/hour, but the advertised rate supplied was NZD $advertisedHourlyPayNzd/hour.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
        }

        val trialPatterns = listOf("trial period", "90-day trial", "90 day trial", "trial clause")
        if (trialPatterns.any(normalized::contains)) {
            findings += ContractFinding("prohibited_trial_period", "Trial-period clause found", "AEWV employment agreements must not include a statutory trial-period clause. Ask a licensed adviser or INZ to review this exact clause before signing.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
        }
        if (normalized.contains("probationary period") || normalized.contains("probation period")) {
            findings += ContractFinding("probation_needs_review", "Probationary period mentioned", "A probationary period is not automatically the same as a statutory trial period. Review the exact wording; this tool does not classify it as a prohibited trial clause.", ContractFindingSeverity.WARNING, ContractCheckStatus.REVIEW_REQUIRED)
        }

        val riskTerms = listOf(
            Triple("unlawful_deductions", "Deductions or repayments", listOf("deduct from wages", "deduction from wages", "repay recruitment", "repay employer costs", "deduct recruitment fee")),
            Triple("bonding_clause", "Bonding or leaving penalty", listOf("repay if you leave", "repayment if you resign", "bonding clause", "pay back training costs if", "penalty for leaving")),
            Triple("worker_fee", "Payment requested for job or visa", listOf("pay for job offer", "fee for job offer", "recruitment fee payable by employee", "pay the employer for sponsorship", "buy the job")),
            Triple("passport_retention", "Passport retention", listOf("retain your passport", "keep your passport", "surrender your passport", "hold your passport"))
        )
        riskTerms.forEach { (code, title, terms) ->
            if (terms.any(normalized::contains)) {
                findings += ContractFinding(code, title, "Potentially unlawful or serious exploitation indicator found. Do not pay or surrender identity documents; seek independent advice and verify with official authorities.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
            }
        }

        if (expectedEmployerName.isNullOrBlank()) {
            findings += ContractFinding("employer_crosscheck_pending", "Employer identity cross-check pending", "Enter the expected employer legal name and compare it with the contract and official accreditation evidence.", ContractFindingSeverity.WARNING, ContractCheckStatus.REVIEW_REQUIRED)
        } else if (!normalized.contains(expectedEmployerName.lowercase(Locale.ROOT))) {
            findings += ContractFinding("employer_name_mismatch", "Employer name mismatch", "The supplied expected employer name was not found in the contract text. Confirm the legal entity and any trading-name relationship.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
        }
        if (!expectedJobTitle.isNullOrBlank() && !normalized.contains(expectedJobTitle.lowercase(Locale.ROOT))) {
            findings += ContractFinding("job_title_mismatch", "Job title mismatch", "The expected job title was not found in the contract text. Compare the signed agreement, job description and job-check role.", ContractFindingSeverity.HIGH_RISK, ContractCheckStatus.FLAGGED)
        }
        findings += ContractFinding("job_check_status", "Specific job-check approval", "A contract cannot prove that this exact role, location and conditions have an approved INZ job check. Request the employer's job-check evidence and confirm with INZ.", ContractFindingSeverity.WARNING, ContractCheckStatus.REVIEW_REQUIRED)
        findings += ContractFinding("market_rate", "Market-rate comparison", "This tool does not fetch a live occupation-specific market-rate benchmark. Compare the guaranteed pay against current official guidance and the actual role.", ContractFindingSeverity.WARNING, ContractCheckStatus.REVIEW_REQUIRED)

        val highRisk = findings.any { it.severity == ContractFindingSeverity.HIGH_RISK }
        val summary = when {
            highRisk -> "High-risk wording or a material mismatch was detected. Do not sign or pay until independently checked."
            findings.any { it.status == ContractCheckStatus.MISSING } -> "Some required contract information was not detected. Request clarification or a corrected agreement."
            else -> "No high-risk pattern was detected by this text scan. This is not legal clearance or proof of job-check approval."
        }
        return EmploymentContractReport(findings, hours, hourlyPay, highRisk, summary)
    }
}
