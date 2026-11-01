package com.alzadash.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** US air-quality index and PM2.5 from Open-Meteo, fetched along with the weather. */
object AirQuality {
    @Volatile var aqi: Int? = null
        private set
    @Volatile var pm25: Double? = null
        private set

    /** Called on the weather thread. */
    fun fetch(lat: Double, lng: Double) {
        try {
            val c = URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lng&current=us_aqi,pm2_5")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val cur = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            aqi = cur.optDouble("us_aqi").takeIf { !it.isNaN() }?.toInt()
            pm25 = cur.optDouble("pm2_5").takeIf { !it.isNaN() }
        } catch (_: Exception) {
        }
    }

    /** The AQI band's name and colour. */
    fun band(aqi: Int): Pair<String, Int> = when {
        aqi <= 50 -> "Good" to 0xFF48BB78.toInt()
        aqi <= 100 -> "Moderate" to 0xFFECC94B.toInt()
        aqi <= 150 -> "Unhealthy for some" to 0xFFED8936.toInt()
        aqi <= 200 -> "Unhealthy" to 0xFFF56565.toInt()
        aqi <= 300 -> "Very unhealthy" to 0xFF9F7AEA.toInt()
        else -> "Hazardous" to 0xFF9B2C2C.toInt()
    }
}
