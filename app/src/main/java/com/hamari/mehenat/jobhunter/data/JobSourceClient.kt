package com.hamari.mehenat.jobhunter.data

import com.hamari.mehenat.jobhunter.domain.JobListing
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
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
    private val leverBaseUrl: String = "https://api.lever.co/v0/postings",
    private val himalayasBaseUrl: String = "https://himalayas.app/jobs/api/search",
    private val jobicyBaseUrl: String = "https://jobicy.com/api/v2/remote-jobs"
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

    fun searchHimalayas(query: String, countries: List<String>): SourceResult {
        return try {
            val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
            val json = getJson("$himalayasBaseUrl?q=$encoded&page=1")
            val jobs = json.optJSONArray("jobs") ?: org.json.JSONArray()
            val found = (0 until jobs.length()).mapNotNull { i ->
                val item = jobs.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("title")
                val description = item.optString("description").ifBlank { item.optString("excerpt") }
                val locations = item.optJSONArray("locationRestrictions") ?: org.json.JSONArray()
                val locationNames = (0 until locations.length()).mapNotNull { index ->
                    locations.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
                }
                val worldwide = locations.length() == 0
                val country = countries.firstOrNull { selected ->
                    val code = when (selected.lowercase()) {
                        "australia" -> "AU"
                        "united kingdom", "uk" -> "GB"
                        "germany" -> "DE"
                        "canada" -> "CA"
                        "new zealand" -> "NZ"
                        "united states", "usa" -> "US"
                        else -> ""
                    }
                    locationNames.any { it.equals(selected, true) || it.contains(selected, true) } ||
                        (code.isNotBlank() && (0 until locations.length()).any { locations.optJSONObject(it)?.optString("alpha2") == code })
                }
                if (!worldwide && country == null) return@mapNotNull null
                val expiry = item.optLong("expiryDate", Long.MAX_VALUE)
                if (expiry > 0L && expiry <= System.currentTimeMillis()) return@mapNotNull null
                val applicationLink = item.optString("applicationLink")
                if (!applicationLink.startsWith("https://") || !matches(query, "$title $description")) return@mapNotNull null
                val guid = item.optString("guid").ifBlank { applicationLink }
                val categories = item.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                JobListing(
                    id = stableId("Himalayas", guid),
                    title = title,
                    company = item.optString("companyName"),
                    location = locationNames.joinToString(", ").ifBlank { "Worldwide remote" },
                    country = country ?: "Remote",
                    description = stripHtml(description).take(12000),
                    tags = categories,
                    sourceUrl = applicationLink,
                    sourceName = "Himalayas",
                    retrievedAtEpochMs = System.currentTimeMillis(),
                    sourcePageUrl = "https://himalayas.app",
                    expiresAtEpochMs = expiry.takeIf { it > 0L && it != Long.MAX_VALUE }
                )
            }
            SourceResult("Himalayas", found.distinctBy { it.id })
        } catch (e: Exception) {
            SourceResult("Himalayas", emptyList(), e.javaClass.simpleName)
        }
    }

    fun searchJobicy(query: String, countries: List<String>, count: Int = 100): SourceResult {
        return try {
            val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
            val json = getJson("$jobicyBaseUrl?count=${count.coerceIn(1, 100)}&tag=$encoded")
            val jobs = json.optJSONArray("jobs") ?: org.json.JSONArray()
            val found = (0 until jobs.length()).mapNotNull { i ->
                val item = jobs.optJSONObject(i) ?: return@mapNotNull null
                val title = item.optString("jobTitle")
                val company = item.optString("companyName")
                val description = item.optString("jobDescription").ifBlank { item.optString("jobExcerpt") }
                val location = item.optString("jobGeo", "Anywhere")
                val worldwide = location.contains("anywhere", true) || location.contains("worldwide", true)
                val country = countryFromLocation(location, countries)
                if (!worldwide && country == null) return@mapNotNull null
                val url = item.optString("url")
                if (!url.startsWith("https://") || !matches(query, "$title $description")) return@mapNotNull null
                val tags = item.optJSONArray("jobIndustry")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                val id = item.optString("id").ifBlank { url }
                JobListing(
                    id = stableId("Jobicy", id),
                    title = title,
                    company = company,
                    location = location,
                    country = country ?: "Remote",
                    description = stripHtml(description).take(12000),
                    tags = tags,
                    sourceUrl = url,
                    sourceName = "Jobicy",
                    retrievedAtEpochMs = System.currentTimeMillis()
                )
            }
            SourceResult("Jobicy", found.distinctBy { it.id })
        } catch (e: Exception) {
            SourceResult("Jobicy", emptyList(), e.javaClass.simpleName)
        }
    }

    fun searchGreenhouse(query: String, countries: List<String>, boardTokens: List<String>): SourceResult {
        if (boardTokens.isEmpty()) return SourceResult("Greenhouse", emptyList(), "No company board IDs configured")
        val found = mutableListOf<JobListing>()
        val errors = mutableListOf<String>()
        boardTokens.map { it.trim() }.filter { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }.take(10).forEach { token ->
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
        siteNames.map { it.trim() }.filter { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }.take(10).forEach { site ->
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
            return org.json.JSONArray(readBodyLimited(response.body ?: error("Empty response body")))
        }
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "HamariMehnatPersonalAndroid/1.0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return JSONObject(readBodyLimited(response.body ?: error("Empty response body")))
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

    private fun readBodyLimited(body: ResponseBody, maxBytes: Int = 8 * 1024 * 1024): String {
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (total < maxBytes) {
                val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
            }
            if (total == maxBytes && input.read() != -1) error("Job source response exceeded the 8 MB safety limit.")
        }
        return output.toString(Charsets.UTF_8.name())
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
