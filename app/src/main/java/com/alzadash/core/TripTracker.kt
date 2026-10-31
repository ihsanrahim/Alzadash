package com.alzadash.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Speed and trip info from GPS (and the car's own speed sensor when Android Auto shares it).
 * Also decides whether the car is parked, which is what allows video to play.
 */
object TripTracker {
    /** Below this speed the car counts as stopped. */
    private const val STILL_KMH = 3f
    /** The car must stay stopped this long before video is allowed. */
    private const val PARKED_AFTER_MS = 3_000L
    private const val GPS_FRESH_MS = 5_000L
    private const val CAR_FRESH_MS = 3_000L

    private var gpsKmh = 0f
    private var gpsAt = 0L
    private var carKmh = 0f
    private var carAt = 0L
    private var stillSince = 0L
    private var last: Location? = null

    var tripMeters = 0.0
        private set
    var tripStartMs = System.currentTimeMillis()
        private set
    var movingMs = 0L
        private set

    private var manager: LocationManager? = null
    private var prefs: Prefs? = null
    private var savedAt = 0L

    /** Where the car is, or the last place it was seen (Kuala Lumpur until the first fix). */
    fun position(context: Context): Pair<Double, Double> {
        last?.let { return it.latitude to it.longitude }
        val p = prefs ?: Prefs(context)
        return p.lastLat to p.lastLng
    }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun start(context: Context) {
        if (manager != null) return
        prefs = Prefs(context)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val lm = context.applicationContext.getSystemService(LocationManager::class.java)
        // GPS first; the fused provider (GPS + Wi-Fi + phone sensors) helps under roofs and in car parks.
        val providers = mutableListOf(LocationManager.GPS_PROVIDER)
        if (android.os.Build.VERSION.SDK_INT >= 31) providers += LocationManager.FUSED_PROVIDER
        for (p in providers) {
            try {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
                manager = lm
            } catch (_: Exception) {
            }
        }
    }

    /** Why there is no speed reading, in words for the car screen. */
    fun problem(context: Context): String {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return "AlzaDash isn't allowed to use location. Open AlzaDash on your phone and tap Location."
        val lm = context.getSystemService(LocationManager::class.java)
        if (lm != null && android.os.Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled) return "Location is turned off on your phone."
        if (android.os.Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return "Location stops when the phone screen is off. In the AlzaDash phone app, tap Location and pick Allow all the time."
        return "Waiting for a GPS or car speed reading. A roof or car park can block GPS."
    }

    fun stop() {
        manager?.removeUpdates(listener)
        manager = null
        last = null
    }

    private fun onLocation(loc: Location) {
        val prev = last
        // A fused fix without its own speed is too rough to prove the car is stopped; skip it.
        if (!loc.hasSpeed() && loc.provider != LocationManager.GPS_PROVIDER) {
            last = loc
            return
        }
        val mps = when {
            loc.hasSpeed() -> loc.speed
            prev != null && loc.time > prev.time -> prev.distanceTo(loc) / ((loc.time - prev.time) / 1000f)
            else -> 0f
        }
        gpsKmh = mps * 3.6f
        gpsAt = SystemClock.elapsedRealtime()
        if (prev != null && gpsKmh >= STILL_KMH && loc.accuracy < 30f) {
            tripMeters += prev.distanceTo(loc)
            movingMs += (loc.time - prev.time).coerceIn(0, 5_000)
        }
        last = loc
        updateStill()
        val now = SystemClock.elapsedRealtime()
        if (now - savedAt > 5 * 60_000) {
            savedAt = now
            prefs?.lastLat = loc.latitude
            prefs?.lastLng = loc.longitude
        }
    }

    fun onCarSpeed(metersPerSecond: Float) {
        carKmh = metersPerSecond * 3.6f
        carAt = SystemClock.elapsedRealtime()
        updateStill()
    }

    /** Current speed in km/h, or null when there is no recent reading. */
    fun speedKmh(): Float? {
        val now = SystemClock.elapsedRealtime()
        return when {
            now - carAt < CAR_FRESH_MS -> carKmh
            now - gpsAt < GPS_FRESH_MS -> gpsKmh
            else -> null
        }
    }

    private fun updateStill() {
        val v = speedKmh()
        if (v == null || v >= STILL_KMH) stillSince = 0L
        else if (stillSince == 0L) stillSince = SystemClock.elapsedRealtime()
    }

    /** True only with a fresh reading showing the car has been stopped for a few seconds. No reading means not parked. */
    fun isParked(): Boolean {
        updateStill()
        return stillSince != 0L && SystemClock.elapsedRealtime() - stillSince >= PARKED_AFTER_MS
    }

    fun avgKmh(): Float = if (movingMs < 1000) 0f else (tripMeters / (movingMs / 1000.0) * 3.6).toFloat()

    fun reset() {
        tripMeters = 0.0
        movingMs = 0L
        tripStartMs = System.currentTimeMillis()
    }
}
