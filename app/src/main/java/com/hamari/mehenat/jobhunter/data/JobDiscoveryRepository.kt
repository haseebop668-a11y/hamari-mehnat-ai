package com.hamari.mehenat.jobhunter.data

import android.content.Context

class SourceRateLimiter(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("public_api_rate_limits", Context.MODE_PRIVATE)

    fun tryAcquire(
        source: String,
        dailyLimit: Int,
        minIntervalMs: Long = 0L,
        todayUtc: String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString(),
        nowEpochMs: Long = System.currentTimeMillis(),
        requestCost: Int = 1
    ): Boolean = synchronized(LOCK) {
        require(source.matches(Regex("[a-z0-9_-]{1,40}")))
        require(dailyLimit > 0 && minIntervalMs >= 0L && requestCost > 0)
        val dayKey = "$source.day"
        val countKey = "$source.count"
        val lastKey = "$source.last"
        val storedDay = prefs.getString(dayKey, null)
        val count = if (storedDay == todayUtc) prefs.getInt(countKey, 0) else 0
        val lastCall = prefs.getLong(lastKey, 0L)
        if (count + requestCost > dailyLimit || (lastCall > 0L && nowEpochMs - lastCall < minIntervalMs)) {
            return@synchronized false
        }
        prefs.edit()
            .putString(dayKey, todayUtc)
            .putInt(countKey, count + requestCost)
            .putLong(lastKey, nowEpochMs)
            .commit()
    }

    companion object { private val LOCK = Any() }
}

/**
 * Composition root for external job connectors. Adding a provider means implementing
 * JobProvider and registering it here; screens and matching code remain unchanged.
 */
class JobDiscoveryRepository(context: Context) {
    private val limiter = SourceRateLimiter(context)
    private val api = JobSourceClient()

    fun providerRegistry(
        greenhouseBoards: List<String> = emptyList(),
        leverSites: List<String> = emptyList(),
        adzunaCredentials: AdzunaCredentials? = null
    ): JobProviderRegistry {
        val providers = mutableListOf<JobProvider>(
            FunctionJobProvider(
                JobProviderDescriptor("arbeitnow", "Arbeitnow", ProviderKind.PUBLIC_API, false, setOf("AU", "NZ", "GB", "DE", "CA"), 6, 60 * 60 * 1000L),
                api::searchArbeitnow
            ),
            FunctionJobProvider(
                JobProviderDescriptor("remotive", "Remotive", ProviderKind.PUBLIC_API, false, setOf("AU", "NZ", "GB", "DE", "CA"), 4, 6 * 60 * 60 * 1000L),
                api::searchRemotive
            ),
            FunctionJobProvider(
                JobProviderDescriptor("himalayas", "Himalayas", ProviderKind.PUBLIC_API, false, setOf("AU", "NZ", "GB", "DE", "CA"), 1),
                api::searchHimalayas
            ),
            FunctionJobProvider(
                JobProviderDescriptor("jobicy", "Jobicy", ProviderKind.PUBLIC_API, false, setOf("AU", "NZ", "GB", "DE", "CA"), 4, 60 * 60 * 1000L),
                api::searchJobicy
            )
        )

        if (adzunaCredentials != null) {
            providers += FunctionJobProvider(
                JobProviderDescriptor("adzuna", "Adzuna", ProviderKind.AUTHENTICATED_API, true, setOf("AU", "NZ", "GB", "DE", "CA"), 10, maxRequestsPerSearch = 5)
            ) { query, countries ->
                api.searchAdzuna(query, countries, adzunaCredentials.appId, adzunaCredentials.appKey)
            }
        }
        if (greenhouseBoards.isNotEmpty()) {
            providers += FunctionJobProvider(
                JobProviderDescriptor("greenhouse", "Greenhouse company boards", ProviderKind.COMPANY_BOARD, false, setOf("AU", "NZ", "GB", "DE", "CA"), 12)
            ) { query, countries -> api.searchGreenhouse(query, countries, greenhouseBoards) }
        }
        if (leverSites.isNotEmpty()) {
            providers += FunctionJobProvider(
                JobProviderDescriptor("lever", "Lever company boards", ProviderKind.COMPANY_BOARD, false, setOf("AU", "NZ", "GB", "DE", "CA"), 12)
            ) { query, countries -> api.searchLever(query, countries, leverSites) }
        }
        return JobProviderRegistry(providers)
    }

    fun search(
        query: String,
        countries: List<String>,
        greenhouseBoards: List<String> = emptyList(),
        leverSites: List<String> = emptyList(),
        adzunaCredentials: AdzunaCredentials? = null
    ): List<SourceResult> {
        val registry = providerRegistry(greenhouseBoards, leverSites, adzunaCredentials)
        return registry.searchEnabled(
            query = query,
            countries = countries,
            enabledIds = registry.all().map { it.id }.toSet()
        ) { descriptor, requestCost ->
            limiter.tryAcquire(
                source = descriptor.id,
                dailyLimit = descriptor.dailyRequestLimit,
                minIntervalMs = descriptor.minimumIntervalMs,
                requestCost = requestCost
            )
        }
    }
}
