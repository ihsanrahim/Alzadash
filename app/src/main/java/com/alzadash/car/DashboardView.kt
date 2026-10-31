package com.alzadash.car

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import com.alzadash.core.NowPlaying
import com.alzadash.core.Radio
import com.alzadash.core.TripTracker
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** The dashboard face: clock, speed, trip and what's playing. Redrawn twice a second. */
class DashboardView(context: Context) : View(context) {
    var visibleArea = Rect()
        set(value) {
            field = value
            invalidate()
        }

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        val a = if (visibleArea.isEmpty) Rect(0, 0, width, height) else visibleArea
        val pad = a.height() * 0.05f
        val left = a.left + pad
        val top = a.top + pad
        val right = a.right - pad
        val bottom = a.bottom - pad
        val u = min(a.height() / 10f, a.width() / 16f) // one layout unit

        // Clock and date, top left.
        val now = Date()
        val clock = DateFormat.getTimeFormat(context).format(now)
        text.typeface = light
        text.textSize = u * 1.9f
        canvas.drawText(clock, left, top + u * 1.7f, text)
        dim.textSize = u * 0.55f
        val date = DateFormat.format("EEEE, d MMMM", now).toString()
        canvas.drawText(date, left, top + u * 2.5f, dim)

        // Speed, centre left.
        val speed = TripTracker.speedKmh()
        text.typeface = bold
        text.textSize = u * 3.6f
        val speedText = speed?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
        val speedY = top + u * 6.4f
        canvas.drawText(speedText, left, speedY, text)
        accent.textSize = u * 0.6f
        canvas.drawText("km/h", left + text.measureText(speedText) + u * 0.3f, speedY, accent)

        // Trip card, right.
        val cardLeft = left + (right - left) * 0.55f
        val tripRect = RectF(cardLeft, top, right, top + u * 6.0f)
        canvas.drawRoundRect(tripRect, u * 0.4f, u * 0.4f, card)
        val cx = cardLeft + u * 0.5f
        drawStat(canvas, "TRIP", String.format(Locale.US, "%.1f km", TripTracker.tripMeters / 1000), cx, top + u * 1.4f, u)
        drawStat(canvas, "TIME", duration(System.currentTimeMillis() - TripTracker.tripStartMs), cx, top + u * 3.2f, u)
        drawStat(canvas, "AVG", String.format(Locale.US, "%.0f km/h", TripTracker.avgKmh()), cx, top + u * 5.0f, u)

        // Now playing, bottom.
        val npTop = bottom - u * 2.0f
        canvas.drawRoundRect(RectF(left, npTop, right, bottom), u * 0.4f, u * 0.4f, card)
        val station = Radio.current
        val track = NowPlaying.read(context)
        val (label, line) = when {
            track != null -> "NOW PLAYING" to listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · ")
            station != null -> "RADIO" to station.name
            else -> "INSTANT RADIO" to "Tap Radio to play a station"
        }
        accent.textSize = u * 0.42f
        canvas.drawText(label, left + u * 0.5f, npTop + u * 0.8f, accent)
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.65f
        canvas.drawText(ellipsize(line, text, right - left - u), left + u * 0.5f, npTop + u * 1.6f, text)
    }

    private fun drawStat(canvas: Canvas, label: String, value: String, x: Float, y: Float, u: Float) {
        dim.textSize = u * 0.42f
        canvas.drawText(label, x, y - u * 0.75f, dim)
        text.typeface = bold
        text.textSize = u * 0.85f
        canvas.drawText(value, x, y + u * 0.2f, text)
    }

    private fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    private fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var end = s.length
        while (end > 0 && p.measureText(s, 0, end) + p.measureText("…") > max) end--
        return s.substring(0, end) + "…"
    }

    companion object {
        val BG = Color.parseColor("#0E1116")
        val CARD = Color.parseColor("#1A1F27")
        val ACCENT = Color.parseColor("#4FD1C5")
        val DIM = Color.parseColor("#8A94A6")
    }
}
