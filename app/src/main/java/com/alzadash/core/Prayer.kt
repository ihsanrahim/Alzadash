package com.alzadash.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class PrayerTime(val name: String, val atMillis: Long)

/**
 * Prayer times calculated on the phone from the car's position, using JAKIM's settings
 * (Subuh 20°, Isyak 18°, Shafi'i Asar) plus JAKIM's 2-minute safety margin.
 * Can differ from the official e-Solat timetable by a minute or two.
 */
object Prayer {
    private const val FAJR_ANGLE = 20.0
    private const val ISHA_ANGLE = 18.0
    private const val IHTIYATI_MIN = 2

    private var cacheKey = ""
    private var cache: List<PrayerTime> = emptyList()

    fun forDay(lat: Double, lng: Double, day: Calendar): List<PrayerTime> {
        val key = "${day.get(Calendar.YEAR)}-${day.get(Calendar.DAY_OF_YEAR)}-${"%.2f".format(lat)}-${"%.2f".format(lng)}"
        if (key == cacheKey) return cache
        val tz = TimeZone.getDefault().getOffset(day.timeInMillis) / 3_600_000.0
        val jd = julian(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24)

        fun sunTime(angle: Double, guessHour: Double, before: Boolean): Double {
            val t = jd + guessHour / 24
            val decl = declination(t)
            val noon = midDay(t)
            val x = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val h = deg(acos(x.coerceIn(-1.0, 1.0))) / 15
            return noon + if (before) -h else h
        }

        val fajr = sunTime(FAJR_ANGLE, 5.0, true)
        val sunrise = sunTime(0.833, 6.0, true)
        val dhuhr = midDay(jd + 12.0 / 24)
        val asr = run {
            val t = jd + 13.0 / 24
            val decl = declination(t)
            val angle = -deg(atan(1 / (1 + tan(rad(abs(lat - decl))))))
            sunTime(angle, 13.0, false)
        }
        val maghrib = sunTime(0.833, 18.0, false)
        val isha = sunTime(ISHA_ANGLE, 18.0, false)

        val midnight = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun at(hours: Double, extraMin: Int): Long {
            val local = hours + tz - lng / 15
            // Round up to the next minute, as timetables do.
            val minutes = kotlin.math.ceil(local * 60).toLong() + extraMin
            return midnight + minutes * 60_000
        }
        cache = listOf(
            PrayerTime("Subuh", at(fajr, IHTIYATI_MIN)),
            PrayerTime("Syuruk", at(sunrise, -IHTIYATI_MIN)),
            PrayerTime("Zohor", at(dhuhr, IHTIYATI_MIN)),
            PrayerTime("Asar", at(asr, IHTIYATI_MIN)),
            PrayerTime("Maghrib", at(maghrib, IHTIYATI_MIN)),
            PrayerTime("Isyak", at(isha, IHTIYATI_MIN)),
        )
        cacheKey = key
        return cache
    }

    /** The next prayer from now (skipping Syuruk), rolling over to tomorrow's Subuh after Isyak. */
    fun next(lat: Double, lng: Double, now: Long = System.currentTimeMillis()): PrayerTime {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        forDay(lat, lng, today).firstOrNull { it.name != "Syuruk" && it.atMillis > now }?.let { return it }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val subuh = forDay(lat, lng, tomorrow).first()
        forDay(lat, lng, today) // keep today's list cached for the timetable row
        return subuh
    }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15
        val eqt = q / 15 - fixHour(ra)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        return decl to eqt
    }

    private fun declination(jd: Double) = sun(jd).first
    private fun midDay(jd: Double) = fixHour(12 - sun(jd).second)
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double) = a - 360 * floor(a / 360)
    private fun fixHour(h: Double) = h - 24 * floor(h / 24)
}
