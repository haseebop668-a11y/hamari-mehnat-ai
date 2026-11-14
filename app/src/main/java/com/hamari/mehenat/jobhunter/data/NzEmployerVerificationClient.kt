package com.hamari.mehenat.jobhunter.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class NzEmployerLookupStatus {
    ACCREDITED_NZBN_MATCH,
    EXPIRED_NZBN_MATCH,
    EXACT_NZBN_MATCH_EXPIRY_UNKNOWN,
    EXACT_NAME_MATCH_REQUIRES_NZBN_CONFIRMATION,
    NO_PUBLIC_MATCH_INCONCLUSIVE,
    LOOKUP_FAILED
}

data class NzAccreditedEmployer(
    val legalName: String,
    val tradingName: String?,
    val nzbn: String?,
    val accreditationExpiryDate: LocalDate?,
    val rawFields: Map<String, String>
)

data class NzEmployerLookupResult(
    val query: String,
    val status: NzEmployerLookupStatus,
    val employers: List<NzAccreditedEmployer>,
    val checkedAtEpochMs: Long,
    val sourceUrl: String,
    val message: String,
    val error: String? = null
)

/**
 * Queries the public search endpoint used by Immigration New Zealand's own accredited-employer
 * search page. The endpoint is not a documented public API and may change; every result links
 * back to the official INZ search page for human confirmation.
 *
 * A missing public result is deliberately inconclusive because some accredited employers opt
 * out of publication. This lookup does not verify a specific job check or visa eligibility.
 */
class NzEmployerVerificationClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = SEARCH_ENDPOINT,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {
    fun lookup(query: String): NzEmployerLookupResult {
        val normalizedQuery = query.trim()
        val now = System.currentTimeMillis()
        if (normalizedQuery.length < 3) {
            return failure(normalizedQuery, now, "Enter at least 3 characters or the full 13-digit NZBN.")
        }
        if (normalizedQuery.all(Char::isDigit) && normalizedQuery.length != 13) {
            return failure(normalizedQuery, now, "An NZBN must contain exactly 13 digits.")
        }

        return try {
            val body = FormBody.Builder()
                .add("query", normalizedQuery)
                .add("collection", "2")
                .add("page", "1")
                .build()
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "HamariMehnatPersonalAndroid/1.0 (INZ public employer lookup)")
                .header("Accept", "application/json")
                .post(body)
                .build()

            http.newCall(request).execute().use { response ->
                val responseText = response.body?.let(::readBodyLimited).orEmpty()
                if (!response.isSuccessful) {
                    val noResults = response.code == 400 && runCatching {
                        val errorBody = JSONObject(responseText)
                        errorBody.optString("Title") == "No Results" &&
                            errorBody.optString("Message").trimStart().startsWith("Your search found no results.")
                    }.getOrDefault(false)
                    if (noResults) {
                        return NzEmployerLookupResult(
                            normalizedQuery, NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE,
                            emptyList(), now, OFFICIAL_SEARCH_URL,
                            "INZ returned no published match. This is not proof that the employer is unaccredited; some employers opt out of public search."
                        )
                    }
                    return failure(normalizedQuery, now, "INZ search returned HTTP ${response.code}.", "HTTP ${response.code}")
                }
                val root = JSONObject(responseText)
                val rawResults = root.opt("results")
                val resultArray = when (rawResults) {
                    is JSONArray -> rawResults
                    is String -> runCatching { JSONArray(rawResults) }.getOrDefault(JSONArray())
                    else -> JSONArray()
                }
                val employers = (0 until resultArray.length()).mapNotNull { index ->
                    val item = resultArray.optJSONObject(index) ?: return@mapNotNull null
                    val raw = item.optJSONObject("field_schema")?.optJSONArray("raw") ?: JSONArray()
                    val fields = buildMap {
                        for (fieldIndex in 0 until raw.length()) {
                            val field = raw.optJSONObject(fieldIndex) ?: continue
                            val key = field.optString("APIColumn").trim()
                            val value = field.optString("Value").trim()
                            if (key.isNotBlank() && value.isNotBlank()) put(key, value)
                        }
                    }
                    val legalName = fields["employerName"].orEmpty()
                    if (legalName.isBlank()) return@mapNotNull null
                    NzAccreditedEmployer(
                        legalName = legalName,
                        tradingName = fields["tradingName"]?.takeIf(String::isNotBlank),
                        nzbn = fields["nzbn"]?.filter(Char::isDigit)?.takeIf(String::isNotBlank),
                        accreditationExpiryDate = parseDate(fields["expiryDateOfAccreditation"]),
                        rawFields = fields
                    )
                }.distinctBy { it.nzbn ?: it.legalName.lowercase(Locale.ROOT) }

                val exactNzbn = normalizedQuery.all(Char::isDigit) &&
                    employers.any { it.nzbn == normalizedQuery }
                val exactNameMatches = employers.filter {
                    normalize(it.legalName) == normalize(normalizedQuery) ||
                        normalize(it.tradingName.orEmpty()) == normalize(normalizedQuery)
                }
                val status = when {
                    exactNzbn -> {
                        val match = employers.first { it.nzbn == normalizedQuery }
                        when {
                            match.accreditationExpiryDate == null -> NzEmployerLookupStatus.EXACT_NZBN_MATCH_EXPIRY_UNKNOWN
                            match.accreditationExpiryDate.isBefore(clock()) -> NzEmployerLookupStatus.EXPIRED_NZBN_MATCH
                            else -> NzEmployerLookupStatus.ACCREDITED_NZBN_MATCH
                        }
                    }
                    exactNameMatches.isNotEmpty() -> NzEmployerLookupStatus.EXACT_NAME_MATCH_REQUIRES_NZBN_CONFIRMATION
                    else -> NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE
                }
                val message = when (status) {
                    NzEmployerLookupStatus.ACCREDITED_NZBN_MATCH ->
                        "Exact NZBN match found in the public INZ accredited-employer search. Recheck the official page before relying on it; this does not approve a particular vacancy."
                    NzEmployerLookupStatus.EXPIRED_NZBN_MATCH ->
                        "Exact NZBN match found, but the published accreditation expiry date has passed. Do not treat this employer as currently accredited."
                    NzEmployerLookupStatus.EXACT_NZBN_MATCH_EXPIRY_UNKNOWN ->
                        "Exact NZBN match found, but no expiry date was returned. Current accreditation cannot be confirmed from this response."
                    NzEmployerLookupStatus.EXACT_NAME_MATCH_REQUIRES_NZBN_CONFIRMATION ->
                        "Name result(s) found. Confirm the legal entity and 13-digit NZBN with the employer before treating this as a match."
                    NzEmployerLookupStatus.NO_PUBLIC_MATCH_INCONCLUSIVE ->
                        "No public result was returned. This is not proof that the employer is unaccredited; some employers opt out of public search."
                    NzEmployerLookupStatus.LOOKUP_FAILED -> "The INZ lookup could not be completed."
                }
                NzEmployerLookupResult(
                    normalizedQuery, status, employers, now, OFFICIAL_SEARCH_URL, message
                )
            }
        } catch (e: Exception) {
            failure(normalizedQuery, now, "The official search could not be read. Open the INZ page and check manually.", e.javaClass.simpleName + ": " + (e.message ?: "request failed"))
        }
    }

    private fun readBodyLimited(body: okhttp3.ResponseBody): String {
        require(body.contentLength() < 0L || body.contentLength() <= MAX_RESPONSE_BYTES) {
            "INZ employer response exceeds the 1 MB safety limit."
        }
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (total <= MAX_RESPONSE_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_BYTES + 1 - total))
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
            }
            require(total <= MAX_RESPONSE_BYTES) {
                "INZ employer response exceeds the 1 MB safety limit."
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun failure(query: String, now: Long, message: String, error: String? = null) =
        NzEmployerLookupResult(query, NzEmployerLookupStatus.LOOKUP_FAILED, emptyList(), now, OFFICIAL_SEARCH_URL, message, error)

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val datePart = value.substringBefore("T")
        return runCatching { LocalDate.parse(datePart) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
        }
    }

    companion object {
        const val OFFICIAL_SEARCH_URL = "https://www.immigration.govt.nz/work/requirements-for-work-visas/approved-employers/accredited-employer-list/"
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private const val SEARCH_ENDPOINT = "https://www.immigration.govt.nz/list-api/getAPIResults/"
    }
}
