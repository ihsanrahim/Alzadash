package com.alzadash.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar
import kotlin.concurrent.thread

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/** The next few events from the phone's calendars (needs calendar permission). Refreshed every 5 minutes. */
object CalendarEvents {
    @Volatile var events: List<CalEvent>? = null
        private set
    @Volatile private var at = 0L
    @Volatile private var busy = false

    fun allowed(c: Context) = c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun refresh(c: Context) {
        if (busy || System.currentTimeMillis() - at < 5 * 60_000 || !allowed(c)) return
        busy = true
        at = System.currentTimeMillis()
        val app = c.applicationContext
        thread(name = "calendar") {
            try {
                val now = System.currentTimeMillis()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                    ContentUris.appendId(it, now - 12 * 3600_000L)
                    ContentUris.appendId(it, now + 7 * 24 * 3600_000L)
                }.build()
                val out = mutableListOf<CalEvent>()
                app.contentResolver.query(uri,
                    arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                    null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cur ->
                    while (cur.moveToNext()) {
                        val e = CalEvent(cur.getString(0) ?: "(No title)", cur.getLong(1), cur.getLong(2), cur.getInt(3) == 1)
                        // Skip what has already finished.
                        if (e.end > now) out += e
                    }
                }
                events = out.take(6)
            } catch (_: Exception) {
            } finally {
                busy = false
            }
        }
    }

    /** "Today", "Tomorrow" or "Wed, 4 Nov". */
    fun dayLabel(ms: Long, allDay: Boolean): String {
        // All-day events are stored in UTC midnight; shift them so the day doesn't slip.
        val t = if (allDay) ms - java.util.TimeZone.getDefault().getOffset(ms) else ms
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val today = Calendar.getInstance()
        val days = (startOfDay(c) - startOfDay(today)) / 86_400_000L
        return when (days) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> android.text.format.DateFormat.format("EEE, d MMM", c).toString()
        }
    }

    private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
