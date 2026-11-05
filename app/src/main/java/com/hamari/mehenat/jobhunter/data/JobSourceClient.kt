package com.hamari.mehenat.jobhunter.data

import com.hamari.mehenat.jobhunter.domain.JobListing
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class SourceResult(val source: String, val jobs: List<JobListing>, val error: String? = null)

class JobSourceClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    fun searchArbeitnow(query: String, countries: List<String>, maxPages: Int = 3): SourceResult {
        val found = mutableListOf<JobListing>()
        return try {
            for (page in 1..maxPages.coerceIn(1, 5)) {
                val url = "https://www.arbeitnow.com/api/job-board-api?page=$page"
                val json = getJson(url)
                val data = json.optJSONArray("data") ?: break
                if (data.length() == 0) break
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val location = item.optString("location")
                    val country = countries.firstOrNull { location.contains(it, true) }
                    val description = item.optString("description")
                    if (country != null && matches(query, "$title $description") && item.optString("url").startsWith("http")) {
                        found += JobListing(
                            id = stableId("Arbeitnow", item.optString("url")),
                            title = title.ifBlank { "Untitled" },
                            company = item.optString("company_name", "Not specified"),
                            location = location,
                            country = country,
                            description = stripHtml(description).take(12000),
                            tags = item.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                            sourceUrl = item.optString("url"),
                            sourceName = "Arbeitnow",
                            retrievedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            }
            SourceResult("Arbeitnow", found.distinctBy { it.id })
        } catch (e: Exception) {
            SourceResult("Arbeitnow", found, e.javaClass.simpleName)
        }
    }

    fun searchRemotive(query: String, countries: List<String>, limit: Int = 50): SourceResult {
        return try {
            val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
            val json = getJson("https://remotive.com/api/remote-jobs?search=$encoded&limit=${limit.coerceIn(1, 100)}")
            val data = json.optJSONArray("jobs") ?: org.json.JSONArray()
            val jobs = (0 until data.length()).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title")
                val location = item.optString("candidate_required_location", "Worldwide")
                val country = countries.firstOrNull { location.contains(it, true) }
                val remoteWorldwide = location.contains("worldwide", true) || location.equals("anywhere", true)
                val url = item.optString("url")
                if ((!remoteWorldwide && country == null) || !url.startsWith("http")) return@mapNotNull null
                JobListing(
                    id = stableId("Remotive", url), title = title, company = item.optString("company_name"),
                    location = location, country = country ?: "Remote",
                    description = stripHtml(item.optString("description")).take(12000),
                    tags = listOf(item.optString("category")).filter { it.isNotBlank() },
                    sourceUrl = url, sourceName = "Remotive", retrievedAtEpochMs = System.currentTimeMillis()
                )
            }
            SourceResult("Remotive", jobs)
        } catch (e: Exception) {
            SourceResult("Remotive", emptyList(), e.javaClass.simpleName)
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "HamariMehnatPersonalAndroid/1.0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: error("Empty response body"))
        }
    }

    private fun matches(query: String, haystack: String): Boolean =
        query.isBlank() || query.trim().split(Regex("\\s+")).filter { it.length > 2 }
            .any { haystack.contains(it, true) }

    private fun stableId(source: String, url: String): String =
        MessageDigest.getInstance("SHA-256").digest("$source|$url".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}
