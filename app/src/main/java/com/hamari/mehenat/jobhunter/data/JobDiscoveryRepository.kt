package com.hamari.mehenat.jobhunter.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneOffset

class SourceRateLimiter(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("public_api_rate_limits", Context.MODE_PRIVATE)

    fun tryAcquire(source: String, dailyLimit: Int, todayUtc: String = LocalDate.now(ZoneOffset.UTC).toString()): Boolean =
        synchronized(LOCK) {
            require(source.matches(Regex("[a-z0-9_-]{1,40}")))
            require(dailyLimit > 0)
            val dayKey = "$source.day"
            val countKey = "$source.count"
            val storedDay = prefs.getString(dayKey, null)
            val count = if (storedDay == todayUtc) prefs.getInt(countKey, 0) else 0
            if (count >= dailyLimit) return@synchronized false
            prefs.edit().putString(dayKey, todayUtc).putInt(countKey, count + 1).commit()
        }

    companion object { private val LOCK = Any() }
}

class JobDiscoveryRepository(context: Context) {
    private val limiter = SourceRateLimiter(context)
    private val api = JobSourceClient()

    fun search(
        query: String,
        countries: List<String>,
        greenhouseBoards: List<String> = emptyList(),
        leverSites: List<String> = emptyList()
    ): List<SourceResult> {
        val results = mutableListOf<SourceResult>()
        results += api.searchArbeitnow(query, countries)
        results += if (limiter.tryAcquire("remotive", 4)) {
            api.searchRemotive(query, countries)
        } else {
            SourceResult("Remotive", emptyList(), "Daily public API limit reached; retry after UTC midnight.")
        }
        if (greenhouseBoards.isNotEmpty()) results += api.searchGreenhouse(query, countries, greenhouseBoards)
        if (leverSites.isNotEmpty()) results += api.searchLever(query, countries, leverSites)
        return results
    }
}
