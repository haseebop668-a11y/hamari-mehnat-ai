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

    fun greenhouseBoards(): String = prefs.getString(KEY_GREENHOUSE_BOARDS, "").orEmpty()
    fun leverSites(): String = prefs.getString(KEY_LEVER_SITES, "").orEmpty()

    fun saveGreenhouseBoards(value: String) {
        require(value.length <= 4000) { "Greenhouse board list is too long." }
        check(prefs.edit().putString(KEY_GREENHOUSE_BOARDS, value.trim()).commit()) {
            "Could not save Greenhouse board settings."
        }
    }

    fun saveLeverSites(value: String) {
        require(value.length <= 4000) { "Lever site list is too long." }
        check(prefs.edit().putString(KEY_LEVER_SITES, value.trim()).commit()) {
            "Could not save Lever board settings."
        }
    }

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
        private const val KEY_GREENHOUSE_BOARDS = "greenhouse_board_ids"
        private const val KEY_LEVER_SITES = "lever_site_names"
        val DEFAULT_ENABLED_IDS = setOf("arbeitnow", "remotive", "himalayas", "jobicy")
    }
}
