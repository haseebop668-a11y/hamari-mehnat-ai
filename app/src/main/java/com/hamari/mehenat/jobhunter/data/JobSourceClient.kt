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
        .build(),
    private val arbeitnowBaseUrl: String = "https://www.arbeitnow.com/api/job-board-api",
    private val remotiveBaseUrl: String = "https://remotive.com/api/remote-jobs",
    private val greenhouseBaseUrl: String = "https://boards-api.greenhouse.io/v1/boards",
    private val leverBaseUrl: String = "https://api.lever.co/v0/postings"
) {
    fun searchArbeitnow(query: String, countries: List<String>, maxPages: Int = 3): SourceResult {
        val found = mutableListOf<JobListing>()
        return try {
            for (page in 1..maxPages.coerceIn(1, 5)) {
                val url = "${arbeitnowBaseUrl}?page=$page"
                val json = getJson(url)
                val data = json.optJSONArray("data") ?: break
                if (data.length() == 0) break
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val location = item.optString("location")
                    val country = countryFromLocation(location, countries)
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
            val json = getJson("${remotiveBaseUrl}?search=$encoded&limit=${limit.coerceIn(1, 100)}")
            val data = json.optJSONArray("jobs") ?: org.json.JSONArray()
            val jobs = (0 until data.length()).mapNotNull { i ->
                val item = data.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title")
                val location = item.optString("candidate_required_location", "Worldwide")
                val country = countryFromLocation(location, countries)
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

    fun searchGreenhouse(query: String, countries: List<String>, boardTokens: List<String>): SourceResult {
        if (boardTokens.isEmpty()) return SourceResult("Greenhouse", emptyList(), "No company board IDs configured")
        val found = mutableListOf<JobListing>()
        val errors = mutableListOf<String>()
        boardTokens.map { it.trim() }.filter { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }.take(30).forEach { token ->
            try {
                val encoded = java.net.URLEncoder.encode(token, Charsets.UTF_8.name())
                val json = getJson("$greenhouseBaseUrl/$encoded/jobs?content=true")
                val jobs = json.optJSONArray("jobs") ?: org.json.JSONArray()
                for (i in 0 until jobs.length()) {
                    val item = jobs.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val location = item.optJSONObject("location")?.optString("name").orEmpty()
                    val country = countryFromLocation(location, countries) ?: continue
                    val description = item.optString("content")
                    val url = item.optString("absolute_url")
                    if (url.startsWith("https://") && matches(query, "$title $description")) {
                        found += JobListing(
                            id = stableId("Greenhouse", url), title = title,
                            company = token, location = location, country = country,
                            description = stripHtml(description).take(12000),
                            sourceUrl = url, sourceName = "Greenhouse public board",
                            retrievedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                errors += "$token: ${e.javaClass.simpleName}"
            }
        }
        return SourceResult("Greenhouse", found.distinctBy { it.id }, errors.takeIf { it.isNotEmpty() }?.joinToString())
    }

    fun searchLever(query: String, countries: List<String>, siteNames: List<String>): SourceResult {
        if (siteNames.isEmpty()) return SourceResult("Lever", emptyList(), "No company site names configured")
        val found = mutableListOf<JobListing>()
        val errors = mutableListOf<String>()
        siteNames.map { it.trim() }.filter { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }.take(30).forEach { site ->
            try {
                val encoded = java.net.URLEncoder.encode(site, Charsets.UTF_8.name())
                val jobs = getArray("$leverBaseUrl/$encoded?mode=json")
                for (i in 0 until jobs.length()) {
                    val item = jobs.optJSONObject(i) ?: continue
                    val title = item.optString("text")
                    val categories = item.optJSONObject("categories")
                    val location = categories?.optString("location").orEmpty()
                    val country = countries.firstOrNull { location.contains(it, true) } ?: continue
                    val description = item.optString("descriptionPlain").ifBlank { item.optString("description") }
                    val url = item.optString("hostedUrl").ifBlank { item.optString("applyUrl") }
                    if (url.startsWith("https://") && matches(query, "$title $description")) {
                        found += JobListing(
                            id = stableId("Lever", url), title = title,
                            company = site, location = location, country = country,
                            description = stripHtml(description).take(12000),
                            sourceUrl = url, sourceName = "Lever public board",
                            retrievedAtEpochMs = System.currentTimeMillis()
                        )
                    }
                }
            } catch (e: Exception) {
                errors += "$site: ${e.javaClass.simpleName}"
            }
        }
        return SourceResult("Lever", found.distinctBy { it.id }, errors.takeIf { it.isNotEmpty() }?.joinToString())
    }

    private fun getArray(url: String): org.json.JSONArray {
        val request = Request.Builder().url(url).header("User-Agent", "HamariMehnatPersonalAndroid/1.0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return org.json.JSONArray(response.body?.string() ?: error("Empty response body"))
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "HamariMehnatPersonalAndroid/1.0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: error("Empty response body"))
        }
    }

    private fun countryFromLocation(location: String, countries: List<String>): String? {
        val aliases = mapOf(
            "australia" to listOf("australia", "perth", "sydney", "brisbane", "melbourne", "queensland", "western australia", "new south wales", "victoria", "adelaide", "darwin"),
            "united kingdom" to listOf("united kingdom", "uk", "england", "scotland", "wales", "london", "manchester", "birmingham", "glasgow", "edinburgh", "cardiff"),
            "germany" to listOf("germany", "berlin", "munich", "münchen", "hamburg", "frankfurt", "cologne", "köln", "stuttgart"),
            "canada" to listOf("canada", "toronto", "vancouver", "calgary", "edmonton", "ottawa", "montreal", "montréal"),
            "new zealand" to listOf("new zealand", "auckland", "wellington", "christchurch", "hamilton"),
            "uae" to listOf("uae", "united arab emirates", "dubai", "abu dhabi"),
            "saudi arabia" to listOf("saudi arabia", "riyadh", "jeddah", "dammam")
        )
        val normalized = location.lowercase()
        return countries.firstOrNull { selected ->
            val candidates = aliases[selected.lowercase()] ?: listOf(selected.lowercase())
            candidates.any { alias -> normalized.contains(alias) }
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
