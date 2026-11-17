package com.hamari.mehenat.jobhunter.data

/**
 * A provider is a bounded connector to one external job source.
 * Implementations must normalize results, preserve source URLs and never submit applications.
 */
enum class ProviderKind { PUBLIC_API, AUTHENTICATED_API, COMPANY_BOARD }

data class JobProviderDescriptor(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val requiresCredentials: Boolean,
    val supportedCountryCodes: Set<String>,
    val dailyRequestLimit: Int,
    val minimumIntervalMs: Long = 0L,
    val maxRequestsPerSearch: Int = 1
) {
    init {
        require(id.matches(Regex("[a-z0-9_-]{1,40}")))
        require(displayName.isNotBlank() && displayName.length <= 80)
        require(dailyRequestLimit > 0 && minimumIntervalMs >= 0L)
        require(maxRequestsPerSearch in 1..10)
        require(supportedCountryCodes.all { it.matches(Regex("[A-Z]{2}")) })
    }
}

interface JobProvider {
    val descriptor: JobProviderDescriptor
    fun search(query: String, countries: List<String>): SourceResult
}

/**
 * In-process connector registry. Providers are explicitly registered by the app;
 * untrusted remote code is never downloaded or executed as a plugin.
 */
class JobProviderRegistry(providers: Collection<JobProvider>) {
    private val providersById: Map<String, JobProvider> = providers.associateBy { it.descriptor.id }.also { indexed ->
        require(indexed.size == providers.size) { "Provider IDs must be unique." }
    }

    fun all(): List<JobProviderDescriptor> =
        providersById.values.map { it.descriptor }.sortedBy { it.displayName }

    fun find(id: String): JobProvider? = providersById[id]

    fun searchEnabled(
        query: String,
        countries: List<String>,
        enabledIds: Set<String>,
        canCall: (JobProviderDescriptor, Int) -> Boolean
    ): List<SourceResult> {
        return providersById.values
            .filter { it.descriptor.id in enabledIds }
            .map { provider ->
                val descriptor = provider.descriptor
                val requested = countries.distinct()
                val requestCost = if (descriptor.kind == ProviderKind.AUTHENTICATED_API) {
                    requested.size.coerceAtMost(descriptor.maxRequestsPerSearch).coerceAtLeast(1)
                } else {
                    1
                }
                if (!canCall(descriptor, requestCost)) {
                    SourceResult(descriptor.displayName, emptyList(), "Local safety limit reached; retry after the provider quota resets.")
                } else {
                    runCatching { provider.search(query, requested) }
                        .getOrElse { SourceResult(descriptor.displayName, emptyList(), "Connector failed: ${it.javaClass.simpleName}") }
                }
            }
    }
}

class FunctionJobProvider(
    override val descriptor: JobProviderDescriptor,
    private val action: (String, List<String>) -> SourceResult
) : JobProvider {
    override fun search(query: String, countries: List<String>): SourceResult =
        action(query, countries.take(5))
}
