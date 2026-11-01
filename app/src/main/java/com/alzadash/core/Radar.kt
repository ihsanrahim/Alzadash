package com.alzadash.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * A small rain radar around the car: OpenStreetMap tiles with RainViewer's latest radar on
 * top, built into one picture and refreshed every 10 minutes.
 */
object Radar {
    private const val ZOOM = 7
    private const val TILE = 256

    @Volatile var image: Bitmap? = null
        private set
    /** Where the car is on [image], in its pixels. */
    @Volatile var dotX = 0f
        private set
    @Volatile var dotY = 0f
        private set
    /** When the radar picture was taken (ms), for the "Rain radar · 17:10" label. */
    @Volatile var takenAt = 0L
        private set
    @Volatile private var at = 0L
    @Volatile private var busy = false

    fun refresh(lat: Double, lng: Double) {
        if (busy || System.currentTimeMillis() - at < 10 * 60_000) return
        busy = true
        at = System.currentTimeMillis()
        thread(name = "radar") {
            try {
                // Tile maths (Web Mercator): which tile the car is in, and where in it.
                val n = 1 shl ZOOM
                val fx = (lng + 180) / 360 * n
                val latR = lat * PI / 180
                val fy = (1 - ln(tan(latR) + 1 / cos(latR)) / PI) / 2 * n
                // A 2×2 block of tiles with the car nearest the middle.
                val x0 = floor(fx - 0.5).toInt()
                val y0 = floor(fy - 0.5).toInt()
                val maps = JSONObject(text("https://api.rainviewer.com/public/weather-maps.json"))
                val host = maps.getString("host")
                val past = maps.getJSONObject("radar").getJSONArray("past")
                val last = past.getJSONObject(past.length() - 1)
                val path = last.getString("path")
                val out = Bitmap.createBitmap(TILE * 2, TILE * 2, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                val p = Paint(Paint.FILTER_BITMAP_FLAG)
                for (dx in 0..1) for (dy in 0..1) {
                    val x = ((x0 + dx) % n + n) % n
                    val y = (y0 + dy).coerceIn(0, n - 1)
                    image("https://tile.openstreetmap.org/$ZOOM/$x/$y.png")?.let {
                        c.drawBitmap(it, (dx * TILE).toFloat(), (dy * TILE).toFloat(), p)
                    }
                    image("$host$path/$TILE/$ZOOM/$x/$y/2/1_1.png")?.let {
                        c.drawBitmap(it, (dx * TILE).toFloat(), (dy * TILE).toFloat(), p)
                    }
                }
                dotX = ((fx - x0) * TILE).toFloat()
                dotY = ((fy - y0) * TILE).toFloat()
                takenAt = last.optLong("time") * 1000
                image = out
            } catch (_: Exception) {
                at = System.currentTimeMillis() - 9 * 60_000 // try again in a minute
            } finally {
                busy = false
            }
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 10000
        // OpenStreetMap asks every app to name itself.
        setRequestProperty("User-Agent", "AlzaDash/4.9 (personal car dashboard)")
    }

    private fun text(url: String) = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun image(url: String): Bitmap? = try {
        open(url).inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }
}
