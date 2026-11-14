package com.hamari.mehenat.jobhunter

import com.hamari.mehenat.jobhunter.domain.CountryJobEligibility
import com.hamari.mehenat.jobhunter.domain.CountryJobPolicy
import com.hamari.mehenat.jobhunter.domain.CountryRegistry
import org.junit.Assert.*
import org.junit.Test

class CountryRegistryTest {
    @Test fun registryHasInitialCountries() {
        assertEquals(listOf("New Zealand","Australia","United Kingdom","Germany","Canada"), CountryRegistry.supportedNames())
        assertEquals("NZ", CountryRegistry.resolve("New Zealand")?.code)
        assertEquals("GB", CountryRegistry.resolve("UK")?.code)
    }
    @Test fun eachCountryHasHttpsOfficialSources() {
        CountryRegistry.all().forEach {
            assertTrue(it.immigrationAuthorityUrl.startsWith("https://"))
            assertTrue(it.officialSources.isNotEmpty())
            assertTrue(it.officialSources.all { source -> source.url.startsWith("https://") })
            assertTrue(it.jobSources.isNotEmpty())
            assertTrue(it.jobSources.all { source -> source.url.startsWith("https://") })
        }
    }
    @Test fun sponsorshipIsPotentialNotVerified() {
        val result = CountryJobPolicy().assess("New Zealand","Civil Labourer","Visa sponsorship available")
        assertEquals(CountryJobEligibility.POTENTIAL_REQUIRES_EMPLOYER_CONFIRMATION,result.status)
    }
    @Test fun restrictionIsFlagged() {
        val result = CountryJobPolicy().assess("New Zealand","Cleaner","Must have NZ residency. No visa sponsorship.")
        assertEquals(CountryJobEligibility.WORK_RIGHTS_RESTRICTION,result.status)
    }
    @Test fun unknownCountryIsNotAccepted() {
        assertEquals(CountryJobEligibility.COUNTRY_NOT_CONFIGURED,
            CountryJobPolicy().assess("Atlantis","Worker","Apply now").status)
    }
}
