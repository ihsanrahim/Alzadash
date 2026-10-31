package com.alzadash.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class WeatherNow(val tempC: Double, val code: Int, val highC: Double, val lowC: Double) {
    val label: String
        get() = when (code) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Cloudy"
            45, 48 -> "Foggy"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 80..82 -> "Showers"
            in 95..99 -> "Thunderstorm"
            else -> "—"
        }
    val icon: String
        get() = when (code) {
            0 -> "☀️"
            1, 2 -> "⛅"
            3 -> "☁️"
            45, 48 -> "🌫️"
            in 51..67, in 80..82 -> "🌧️"
            in 95..99 -> "⛈️"
            else -> "🌡️"
        }
}

/** Current weather from Open-Meteo (free, no account). Refreshed at most every 20 minutes. */
object Weather {
    @Volatile
    var now: WeatherNow? = null
        private set
    @Volatile
    private var fetchedAt = 0L
    @Volatile
    private var busy = false

    fun refreshIfStale(lat: Double, lng: Double) {
        val age = System.currentTimeMillis() - fetchedAt
        if (busy || (now != null && age < 20 * 60_000) || (now == null && age < 60_000)) return
        busy = true
        fetchedAt = System.currentTimeMillis()
        thread(name = "weather") {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto&forecast_days=1"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cur = o.getJSONObject("current")
                val daily = o.getJSONObject("daily")
                now = WeatherNow(
                    cur.getDouble("temperature_2m"),
                    cur.getInt("weather_code"),
                    daily.getJSONArray("temperature_2m_max").getDouble(0),
                    daily.getJSONArray("temperature_2m_min").getDouble(0),
                )
            } catch (_: Exception) {
            } finally {
                busy = false
            }
        }
    }
}
