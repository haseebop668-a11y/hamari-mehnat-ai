package com.hamari.mehenat.jobhunter.data

import android.content.Context

/**
 * Stores only connector enablement preferences. Provider credentials remain in
 * provider-specific encrypted stores (for example AdzunaCredentialsStore).
 */
class ConnectorPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("connector_preferences_v1", Context.MODE_PRIVATE)

    fun enabledIds(): Set<String> =
        prefs.getStringSet(KEY_ENABLED_IDS, null)?.toSet() ?: DEFAULT_ENABLED_IDS

    fun isEnabled(id: String): Boolean = id in enabledIds()

    fun setEnabled(id: String, enabled: Boolean) {
        require(id.matches(Regex("[a-z0-9_-]{1,40}"))) { "Invalid connector ID." }
        val updated = enabledIds().toMutableSet()
        if (enabled) updated.add(id) else updated.remove(id)
        check(prefs.edit().putStringSet(KEY_ENABLED_IDS, updated).commit()) {
            "Could not save connector preferences."
        }
    }

    companion object {
        private const val KEY_ENABLED_IDS = "enabled_provider_ids"
        val DEFAULT_ENABLED_IDS = setOf("arbeitnow", "remotive", "himalayas", "jobicy")
    }
}
