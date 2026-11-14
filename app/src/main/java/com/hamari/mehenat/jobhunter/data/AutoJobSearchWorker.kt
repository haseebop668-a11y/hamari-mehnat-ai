package com.hamari.mehenat.jobhunter.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hamari.mehenat.jobhunter.domain.JobMatcher
import com.hamari.mehenat.jobhunter.domain.JobProfile
import com.hamari.mehenat.jobhunter.domain.JobFreshnessService
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class AutoJobSearchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val profile = LocalProfileStore(applicationContext).load() ?: return Result.success()
            val roles = profile.optJSONArray("target_roles").toStringList()
            val countries = profile.optJSONArray("target_countries").toStringList()
            val skills = profile.optJSONArray("skills").toStringList()
            if (roles.isEmpty() || countries.isEmpty()) return Result.success()

            val prefs = applicationContext.getSharedPreferences("job_search_worker", Context.MODE_PRIVATE)
            val cursor = prefs.getInt("role_cursor", 0).coerceAtLeast(0) % roles.size
            val query = roles[cursor]
            prefs.edit().putInt("role_cursor", (cursor + 1) % roles.size).apply()
            val sources = JobDiscoveryRepository(applicationContext).search(query, countries)
            if (sources.all { it.error != null }) return Result.retry()

            val matcher = JobMatcher()
            val tracker = ApplicationTrackerStore(applicationContext)
            try {
                JobFreshnessService.removeExpired(sources.flatMap { it.jobs }).forEach { job ->
                    val match = matcher.evaluate(
                        job,
                        JobProfile(targetRoles = roles, targetCountries = countries, skills = skills)
                    )
                    if (match.score >= 35) tracker.save(job)
                }
            } finally {
                tracker.close()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull {
            optString(it).trim().takeIf(String::isNotBlank)
        }
}

object AutoJobSearchScheduler {
    private const val UNIQUE_NAME = "personal-job-search-refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoJobSearchWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
