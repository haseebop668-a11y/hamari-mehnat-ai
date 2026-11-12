package com.hamari.mehenat.jobhunter.data

import com.hamari.mehenat.jobhunter.domain.CountryRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class OfficialVisaSource(
    val country: String,
    val title: String,
    val url: String,
    val purpose: String,
    val checkedOn: String = utcToday()
)

private fun utcToday(): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

/** Visa research sources are generated from the country registry, not duplicated here. */
object OfficialVisaSources {
    val sources: List<OfficialVisaSource> = CountryRegistry.all().flatMap { country ->
        country.officialSources.map { source ->
            OfficialVisaSource(country.displayName, source.title, source.url, source.purpose)
        }
    }
}
