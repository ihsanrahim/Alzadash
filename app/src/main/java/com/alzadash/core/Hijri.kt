package com.alzadash.core

import android.icu.util.IslamicCalendar
import java.util.Date

/** Today's Hijri date (Umm al-Qura). Malaysia's official date can occasionally differ by a day. */
object Hijri {
    private val months = listOf(
        "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
        "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
    )

    fun today(): String {
        val cal = IslamicCalendar()
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.time = Date()
        val month = months[cal.get(android.icu.util.Calendar.MONTH)]
        return "${cal.get(android.icu.util.Calendar.DAY_OF_MONTH)} $month ${cal.get(android.icu.util.Calendar.YEAR)}H"
    }
}
