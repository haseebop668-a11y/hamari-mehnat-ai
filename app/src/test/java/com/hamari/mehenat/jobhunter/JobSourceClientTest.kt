package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.data.JobSourceClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JobSourceClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JobSourceClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = JobSourceClient(
            http = OkHttpClient(),
            arbeitnowBaseUrl = server.url("/jobs").toString().trimEnd('/'),
            greenhouseBaseUrl = server.url("/greenhouse").toString().trimEnd('/'),
            leverBaseUrl = server.url("/lever").toString().trimEnd('/'),
            himalayasBaseUrl = server.url("/himalayas").toString().trimEnd('/'),
            himalayasVerificationBaseUrl = server.url("/himalayas/companies").toString().trimEnd('/'),
            jobicyBaseUrl = server.url("/jobicy").toString().trimEnd('/')
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun parsesValidArbeitnowResponseAndKeepsSourceUrl() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"title":"Heavy Diesel Mechanic","company_name":"Example Mining","location":"Perth, Australia","description":"Hydraulics maintenance","url":"https://jobs.example/123","tags":["mechanic"],"created_at":"2026-11-05"}]}"""
        ))
        val result = client.searchArbeitnow("diesel mechanic", listOf("Australia"), 1)
        assertNull("Unexpected source error: ${result.error}", result.error)
        assertEquals(1, result.jobs.size)
        assertEquals("Heavy Diesel Mechanic", result.jobs.first().title)
        assertEquals("https://jobs.example/123", result.jobs.first().sourceUrl)
        assertEquals("Australia", result.jobs.first().country)
    }

    @Test fun ignoresJobsOutsideSelectedCountries() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"title":"Mechanic","company_name":"Example","location":"Berlin, Germany","description":"Diesel","url":"https://jobs.example/1"}]}"""
        ))
        val result = client.searchArbeitnow("diesel", listOf("Australia"), 1)
        assertTrue(result.jobs.isEmpty())
        assertNull(result.error)
    }

    @Test fun reportsHttpFailureInsteadOfPretendingSearchSucceeded() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
        val result = client.searchArbeitnow("mechanic", listOf("Australia"), 1)
        assertTrue(result.jobs.isEmpty())
        assertEquals("IllegalStateException", result.error)
    }
    @Test fun parsesHimalayasApplicationAndSourceAttributionLinks() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"jobs":[{"title":"Remote Diesel Mechanic","excerpt":"Heavy equipment maintenance","description":"Hydraulics and maintenance","companyName":"Example Mining","companySlug":"example-mining","locationRestrictions":[{"alpha2":"AU","name":"Australia","slug":"australia"}],"categories":["Mechanic"],"applicationLink":"https://careers.example/apply/1","guid":"example-remote-diesel","expiryDate":9999999999}]}"""
        ))
        val result = client.searchHimalayas("diesel", listOf("Australia"))
        assertNull(result.error)
        assertEquals(1, result.jobs.size)
        assertEquals("https://careers.example/apply/1", result.jobs.first().sourceUrl)
        assertEquals("https://himalayas.app/companies/example-mining/jobs/example-remote-diesel", result.jobs.first().sourcePageUrl)
        assertEquals("https://himalayas.app/jobs/api/companies/example-mining/jobs/example-remote-diesel", result.jobs.first().verificationUrl)
    }

    @Test fun ignoresExpiredHimalayasListings() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"jobs":[{"title":"Diesel Mechanic","description":"Diesel","companyName":"Example","locationRestrictions":[],"applicationLink":"https://careers.example/apply/1","guid":"expired-job","expiryDate":1}]}"""
        ))
        val result = client.searchHimalayas("diesel", listOf("Australia"))
        assertTrue(result.jobs.isEmpty())
    }

    @Test fun parsesJobicyPublicRemoteJobs() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"jobs":[{"id":123,"url":"https://jobicy.com/jobs/example","jobTitle":"Remote Diesel Mechanic","companyName":"Example Mining","jobGeo":"Anywhere","jobDescription":"Diesel maintenance","jobIndustry":["Mechanic"]}]}"""
        ))
        val result = client.searchJobicy("diesel", listOf("Australia"), 10)
        assertNull(result.error)
        assertEquals(1, result.jobs.size)
        assertEquals("Jobicy", result.jobs.first().sourceName)
        assertEquals("Remote", result.jobs.first().country)
    }

    @Test fun verifiesOpenHimalayasJob() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val url = server.url("/himalayas/companies/example/jobs/job-1").toString()
        assertTrue(client.isHimalayasJobOpen(url))
    }

    @Test fun removesClosedHimalayasJobAfterGoneResponse() {
        server.enqueue(MockResponse().setResponseCode(410).setBody("""{"error":"closed"}"""))
        val url = server.url("/himalayas/companies/example/jobs/job-1").toString()
        assertTrue(!client.isHimalayasJobOpen(url))
    }

}
