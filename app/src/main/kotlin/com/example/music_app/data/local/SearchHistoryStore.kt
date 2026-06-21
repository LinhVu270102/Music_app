package com.example.music_app.data.local

import android.content.Context
import org.json.JSONArray

class SearchHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )

    fun saveQuery(query: String) {
        val normalizedQuery = query.trim()

        if (normalizedQuery.isBlank()) return

        val oldQueries = getRecentQueries()
            .filterNot { oldQuery ->
                oldQuery.equals(normalizedQuery, ignoreCase = true)
            }

        val newQueries = listOf(normalizedQuery) + oldQueries

        prefs.edit()
            .putString(
                KEY_RECENT_QUERIES,
                encodeQueries(newQueries.take(MAX_RECENT_QUERIES))
            )
            .putString(KEY_LATEST_QUERY, normalizedQuery)
            .apply()
    }

    fun saveLatestQuery(query: String) {
        saveQuery(query)
    }

    fun getLatestQuery(): String {
        return prefs.getString(KEY_LATEST_QUERY, "").orEmpty()
    }

    fun getRecentQueries(): List<String> {
        val rawJson = prefs.getString(KEY_RECENT_QUERIES, "[]").orEmpty()

        return runCatching {
            val jsonArray = JSONArray(rawJson)

            List(jsonArray.length()) { index ->
                jsonArray.optString(index)
            }.filter { query ->
                query.isNotBlank()
            }
        }.getOrDefault(emptyList())
    }

    fun removeQuery(query: String) {
        val newQueries = getRecentQueries()
            .filterNot { oldQuery ->
                oldQuery.equals(query, ignoreCase = true)
            }

        prefs.edit()
            .putString(KEY_RECENT_QUERIES, encodeQueries(newQueries))
            .apply()
    }

    fun clearLatestQuery() {
        prefs.edit()
            .remove(KEY_LATEST_QUERY)
            .apply()
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_LATEST_QUERY)
            .remove(KEY_RECENT_QUERIES)
            .apply()
    }

    private fun encodeQueries(queries: List<String>): String {
        val jsonArray = JSONArray()

        queries.forEach { query ->
            jsonArray.put(query)
        }

        return jsonArray.toString()
    }

    companion object {
        private const val PREF_NAME = "search_history"
        private const val KEY_LATEST_QUERY = "latest_query"
        private const val KEY_RECENT_QUERIES = "recent_queries"
        private const val MAX_RECENT_QUERIES = 10
    }
}