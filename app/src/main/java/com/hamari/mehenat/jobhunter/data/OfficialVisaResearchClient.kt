package com.hamari.mehenat.jobhunter.data

import android.text.Html
import okhttp3.OkHttpClient
import okhttp3.Request
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
                val html = response.body?.string().orEmpty()
                val visible = html
                    .replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("(?is)<[^>]+>"), " ")
                val plain = Html.fromHtml(visible, Html.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(12000)
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
}
