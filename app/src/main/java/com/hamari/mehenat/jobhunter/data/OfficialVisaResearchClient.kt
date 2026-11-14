package com.hamari.mehenat.jobhunter.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class VisaResearchResult(
    val source: OfficialVisaSource,
    val retrievedAtEpochMs: Long,
    val statusCode: Int?,
    val text: String,
    val error: String? = null
)

class OfficialVisaResearchClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    fun fetch(source: OfficialVisaSource): VisaResearchResult {
        val now = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(source.url)
                .header("User-Agent", "HamariMehnatPersonalAndroid/1.0 (official-source research)")
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return VisaResearchResult(source, now, response.code, "", "HTTP ${response.code}")
                }
                val body = response.body ?: error("Empty page response")
                require(body.contentLength() < 0L || body.contentLength() <= MAX_HTML_BYTES) {
                    "Official page exceeds the 2 MB safety limit."
                }
                val output = ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (total <= MAX_HTML_BYTES) {
                        val count = input.read(buffer, 0, minOf(buffer.size, MAX_HTML_BYTES + 1 - total))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                    }
                    require(total <= MAX_HTML_BYTES) {
                        "Official page exceeds the 2 MB safety limit."
                    }
                }
                val document = Jsoup.parse(output.toString(Charsets.UTF_8.name()))
                document.select("script,style,noscript,nav,footer").remove()
                val plain = document.text().replace(Regex("\\s+"), " ").trim().take(MAX_TEXT_CHARS)
                if (plain.isBlank()) {
                    VisaResearchResult(source, now, response.code, "", "No readable page text was returned.")
                } else {
                    VisaResearchResult(source, now, response.code, plain)
                }
            }
        } catch (e: Exception) {
            VisaResearchResult(source, now, null, "", e.javaClass.simpleName + ": " + (e.message ?: "request failed"))
        }
    }

    companion object {
        private const val MAX_HTML_BYTES = 2 * 1024 * 1024
        private const val MAX_TEXT_CHARS = 12000
    }
}
