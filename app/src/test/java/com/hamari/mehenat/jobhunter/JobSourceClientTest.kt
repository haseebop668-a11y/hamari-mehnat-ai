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
            arbeitnowBaseUrl = server.url("/jobs").toString().trimEnd('/')
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun parsesValidArbeitnowResponseAndKeepsSourceUrl() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"data":[{"title":"Heavy Diesel Mechanic","company_name":"Example Mining","location":"Perth, Australia","description":"Hydraulics maintenance","url":"https://jobs.example/123","tags":["mechanic"],"created_at":"2026-11-05"}]}"""
        ))
        val result = client.searchArbeitnow("diesel mechanic", listOf("Australia"), 1)
        assertNull(result.error)
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
}
