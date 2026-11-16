package com.hamari.mehenat.jobhunter

import org.json.JSONArray

internal object CvQuestionParser {
    fun parse(modelOutput: String): List<String> {
        val start = modelOutput.indexOf('[')
        val end = modelOutput.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            val array = JSONArray(modelOutput.substring(start, end + 1))
            (0 until array.length())
                .map { array.optString(it).trim() }
                .filter { it.length in 8..240 }
                .distinct()
                .take(5)
        }.getOrDefault(emptyList())
    }
}
