package com.alzadash.core

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Finds a working stream link for a built-in station on radio-browser.info. */
object RadioResolver {
    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    /** Blocking; call off the main thread. */
    fun resolve(context: Context, station: Station): String? {
        if (station.url.isNotBlank()) return station.url
        val prefs = Prefs(context)
        prefs.resolvedUrl(station.name)?.let { return it }
        val q = URLEncoder.encode(station.search.ifBlank { station.name }, "UTF-8")
        for (base in mirrors) {
            try {
                val url = URL("$base/json/stations/search?name=$q&countrycode=MY&hidebroken=true&order=clickcount&reverse=true&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "AlzaDash/1.0")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val stream = arr.getJSONObject(i).optString("url_resolved").ifBlank { arr.getJSONObject(i).optString("url") }
                    if (stream.startsWith("http")) {
                        prefs.saveResolvedUrl(station.name, stream)
                        return stream
                    }
                }
                return null
            } catch (_: Exception) {
                // try the next mirror
            }
        }
        return null
    }
}
