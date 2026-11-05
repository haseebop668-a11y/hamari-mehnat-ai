package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.data.OfficialVisaResearchClient
import com.hamari.mehenat.jobhunter.data.OfficialVisaSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfficialVisaResearchClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OfficialVisaResearchClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OfficialVisaResearchClient(OkHttpClient())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun extractsReadablePageTextAndRecordsRetrievalTime() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            "<html><head><title>Visa</title></head><body><nav>Menu</nav><main><h1>Skilled worker visa</h1><p>Official requirements.</p><script>ignore this</script></main></body></html>"
        ))
        val source = OfficialVisaSource("Test", "Visa page", server.url("/visa").toString(), "Test source")
        val result = client.fetch(source)
        assertEquals(200, result.statusCode)
        assertTrue(result.text.contains("Skilled worker visa"))
        assertTrue(result.text.contains("Official requirements"))
        assertTrue(!result.text.contains("ignore this"))
        assertTrue(result.retrievedAtEpochMs > 0L)
        assertNotNull(result.source.url)
    }

    @Test fun reportsNonSuccessHttpStatus() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val source = OfficialVisaSource("Test", "Visa page", server.url("/visa").toString(), "Test source")
        val result = client.fetch(source)
        assertEquals(503, result.statusCode)
        assertTrue(result.error?.contains("503") == true)
        assertTrue(result.text.isBlank())
    }
}
