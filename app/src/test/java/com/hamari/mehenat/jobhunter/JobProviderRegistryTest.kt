package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.data.FunctionJobProvider
import com.hamari.mehenat.jobhunter.data.JobProviderDescriptor
import com.hamari.mehenat.jobhunter.data.JobProviderRegistry
import com.hamari.mehenat.jobhunter.data.ProviderKind
import com.hamari.mehenat.jobhunter.data.SourceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JobProviderRegistryTest {
    private fun provider(id: String, sourceName: String = id) = FunctionJobProvider(
        JobProviderDescriptor(
            id = id,
            displayName = sourceName,
            kind = ProviderKind.PUBLIC_API,
            requiresCredentials = false,
            supportedCountryCodes = setOf("AU", "NZ"),
            dailyRequestLimit = 3
        )
    ) { _, _ -> SourceResult(sourceName, emptyList()) }

    @Test fun rejectsDuplicateProviderIds() {
        assertThrows(IllegalArgumentException::class.java) {
            JobProviderRegistry(listOf(provider("sample"), provider("sample")))
        }
    }

    @Test fun onlyRunsExplicitlyEnabledProviders() {
        val registry = JobProviderRegistry(listOf(provider("one"), provider("two")))
        val results = registry.searchEnabled(
            query = "diesel mechanic",
            countries = listOf("AU"),
            enabledIds = setOf("two")
        ) { _, _ -> true }

        assertEquals(1, results.size)
        assertEquals("two", results.single().source)
    }

    @Test fun enforcesConnectorRateLimitBeforeCallingProvider() {
        var called = false
        val registry = JobProviderRegistry(listOf(
            FunctionJobProvider(
                JobProviderDescriptor("sample", "Sample", ProviderKind.PUBLIC_API, false, setOf("AU"), 1)
            ) { _, _ ->
                called = true
                SourceResult("Sample", emptyList())
            }
        ))
        val result = registry.searchEnabled("diesel", listOf("AU"), setOf("sample")) { _, _ -> false }

        assertTrue(!called)
        assertTrue(result.single().error?.contains("safety limit") == true)
    }
}
