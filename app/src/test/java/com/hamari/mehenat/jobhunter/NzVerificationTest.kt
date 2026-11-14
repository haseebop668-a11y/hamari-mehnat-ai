package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.data.NzEmployerLookupStatus
import com.hamari.mehenat.jobhunter.data.NzEmployerVerificationClient
import com.hamari.mehenat.jobhunter.domain.ContractCheckStatus
import com.hamari.mehenat.jobhunter.domain.EmploymentContractAnalyzer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class NzVerificationTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun exactNzbnWithFutureExpiryIsReportedAsAccreditedMatch() {
        val fields = JSONArray()
            .put(JSONObject().put("APIColumn", "employerName").put("Value", "Example Civil Limited"))
            .put(JSONObject().put("APIColumn", "tradingName").put("Value", "Example Civil"))
            .put(JSONObject().put("APIColumn", "nzbn").put("Value", "9429030665699"))
            .put(JSONObject().put("APIColumn", "expiryDateOfAccreditation").put("Value", "2027-06-30T00:00:00"))
        val record = JSONObject().put("field_schema", JSONObject().put("raw", fields))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            JSONObject().put("totalResults", 1).put("results", JSONArray().put(record).toString()).toString()
        ))
        val client = NzEmployerVerificationClient(
            http = OkHttpClient(),
            endpoint = server.url("/inz").toString(),
            clock = { LocalDate.parse("2026-11-14") }
        )
        val result = client.lookup("9429030665699")
        assertEquals(NzEmployerLookupStatus.ACCREDITED_NZBN_MATCH, result.status)
        assertEquals("Example Civil Limited", result.employers.single().legalName)
        assertEquals("9429030665699", result.employers.single().nzbn)
        assertTrue(result.sourceUrl.contains("immigration.govt.nz"))
    }

    @Test fun noPublicMatchIsInconclusiveNotProofOfNoAccreditation() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            JSONObject().put("totalResults", 0).put("results", "[]").toString()
        ))
        val result = NzEmployerVerificationClient(
            http = OkHttpClient(), endpoint = server.url("/inz").toString()
        ).lookup("Unknown Civil Ltd")
        assertEquals(NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE, result.status)
        assertTrue(result.message.contains("not proof"))
    }

    @Test fun recognizesOfficialInzNoResultsHttp400AsInconclusive() {
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            JSONObject().put("Title", "No Results")
                .put("Message", "Your search found no results. You may wish to refine your search.")
                .toString()
        ))
        val result = NzEmployerVerificationClient(
            http = OkHttpClient(), endpoint = server.url("/inz").toString()
        ).lookup("No Such Employer Limited")
        assertEquals(NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE, result.status)
    }

    @Test fun rejectsInvalidNzbnLengthBeforeNetworkCall() {
        val result = NzEmployerVerificationClient().lookup("123456")
        assertEquals(NzEmployerLookupStatus.LOOKUP_FAILED, result.status)
        assertTrue(result.message.contains("13 digits"))
    }

    @Test fun contractAnalyzerFlagsTrialPeriodAndUnderThirtyHours() {
        val report = EmploymentContractAnalyzer().analyze(
            "Employer: Example Civil Limited. Job title: Labourer. Place of work: Auckland. Duties: site preparation. " +
                "Guaranteed hours: 20 hours per week. Pay rate: NZD $24 per hour. Paid fortnightly. " +
                "90-day trial period applies. Annual leave and sick leave included. Fixed-term contract.",
            expectedEmployerName = "Example Civil Limited",
            expectedJobTitle = "Labourer"
        )
        assertTrue(report.hasHighRiskFlags)
        assertEquals(20.0, report.detectedWeeklyHours!!, 0.0)
        assertEquals(24.0, report.detectedHourlyPayNzd!!, 0.0)
        assertTrue(report.findings.any { it.code == "prohibited_trial_period" })
        assertTrue(report.findings.any { it.code == "aewv_hours_below_minimum" })
    }

    @Test fun explicitNoTrialStatementIsNotFlaggedAsATrialClause() {
        val report = EmploymentContractAnalyzer().analyze(
            "The agreement states that no trial period applies. Guaranteed hours: 40 hours per week."
        )
        assertFalse(report.findings.any { it.code == "prohibited_trial_period" })
    }

    @Test fun contractCrossChecksAdvertisedPayAndWorkLocation() {
        val report = EmploymentContractAnalyzer().analyze(
            "Employer: Example Civil. Position: Civil Labourer. Place of work: Christchurch. " +
                "Guaranteed hours: 40 hours per week. Pay rate: NZD $25 per hour.",
            expectedEmployerName = "Example Civil",
            expectedJobTitle = "Civil Labourer",
            expectedWorkLocation = "Auckland",
            advertisedHourlyPayNzd = 30.0
        )
        assertTrue(report.findings.any { it.code == "work_location_mismatch" })
        assertTrue(report.findings.any { it.code == "pay_mismatch" })
    }

    @Test fun probationaryPeriodIsNotAutomaticallyClassifiedAsTrialPeriod() {
        val report = EmploymentContractAnalyzer().analyze(
            "Position: Mechanic. Probationary period applies. Guaranteed hours: 40 hours per week. Pay rate: $32 per hour."
        )
        assertFalse(report.findings.any { it.code == "prohibited_trial_period" })
        assertEquals(ContractCheckStatus.REVIEW_REQUIRED, report.findings.first { it.code == "probation_needs_review" }.status)
        assertEquals(40.0, report.detectedWeeklyHours!!, 0.0)
    }
}
