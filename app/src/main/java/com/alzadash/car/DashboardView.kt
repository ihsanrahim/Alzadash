package com.alzadash.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.View
import com.alzadash.core.Background
import com.alzadash.core.Hijri
import com.alzadash.core.NowPlaying
import com.alzadash.core.Prayer
import com.alzadash.core.Radio
import com.alzadash.core.TripTracker
import com.alzadash.core.Weather
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * The dashboard face: time, dates, next prayer with countdown, weather, speed and trip,
 * what's playing, and a vertical shortcut bar on the right. Redrawn twice a second.
 */
class DashboardView(context: Context) : View(context) {
    var visibleArea = Rect()
        set(value) {
            field = value
            invalidate()
        }

    /** Called when a shortcut in the right-hand bar is tapped. */
    var onDockTap: (DockApp) -> Unit = {}

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val warm = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WARM }
    private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD; alpha = 215 }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)

    private val dockRects = mutableListOf<Pair<RectF, DockApp>>()
    private val icons = HashMap<String, Drawable?>()
    private var dockApps: List<DockApp> = emptyList()
    private var dockLoadedAt = 0L
    private val prevRect = RectF()
    private val nextRect = RectF()
    private val shade = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Called when the ◀ or ▶ radio button is tapped, with -1 or +1. */
    var onRadioStep: (Int) -> Unit = {}
    private var hijri = ""
    private var hijriDay = -1

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            when {
                prevRect.contains(event.x, event.y) -> onRadioStep(-1)
                nextRect.contains(event.x, event.y) -> onRadioStep(1)
                else -> dockRects.firstOrNull { it.first.contains(event.x, event.y) }?.let { onDockTap(it.second) }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        Background.get(context)?.let { drawCover(canvas, it) }
        val a = if (visibleArea.isEmpty) Rect(0, 0, width, height) else visibleArea
        val u = min(a.height() / 10f, a.width() / 17f) // one layout unit
        val pad = u * 0.5f
        val top = a.top + pad
        val bottom = a.bottom - pad
        val dockW = u * 1.9f
        val left = a.left + pad
        val right = a.right - pad - dockW - pad
        val (lat, lng) = TripTracker.position(context)
        Weather.refreshIfStale(lat, lng)

        drawDock(canvas, a.right - pad - dockW, top, a.right - pad, bottom, u)

        // Left column: time, dates, speed and trip.
        val now = Date()
        text.typeface = light
        text.textSize = u * 2.3f
        canvas.drawText(DateFormat.getTimeFormat(context).format(now), left, top + u * 2.0f, text)
        dim.textSize = u * 0.55f
        canvas.drawText(DateFormat.format("EEEE, d MMMM yyyy", now).toString(), left, top + u * 2.9f, dim)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (day != hijriDay) {
            hijri = try { Hijri.today() } catch (_: Exception) { "" }
            hijriDay = day
        }
        warm.textSize = u * 0.55f
        canvas.drawText(hijri, left, top + u * 3.7f, warm)

        val speed = TripTracker.speedKmh()
        text.typeface = bold
        text.textSize = u * 1.6f
        val speedText = speed?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
        val speedY = top + u * 5.9f
        canvas.drawText(speedText, left, speedY, text)
        accent.textSize = u * 0.5f
        val sx = left + text.measureText(speedText) + u * 0.25f
        canvas.drawText("km/h", sx, speedY, accent)
        dim.textSize = u * 0.45f
        val trip = String.format(Locale.US, "Trip %.1f km  ·  %s  ·  avg %.0f km/h",
            TripTracker.tripMeters / 1000, duration(System.currentTimeMillis() - TripTracker.tripStartMs), TripTracker.avgKmh())
        canvas.drawText(trip, left, speedY + u * 0.8f, dim)

        // Right column: prayer card and weather card.
        val colL = left + (right - left) * 0.5f
        val prayerRect = RectF(colL, top, right, top + u * 2.0f)
        canvas.drawRoundRect(prayerRect, u * 0.4f, u * 0.4f, card)
        drawPrayer(canvas, prayerRect, lat, lng, u)

        val wRect = RectF(colL, prayerRect.bottom + u * 0.3f, right, prayerRect.bottom + u * 2.3f)
        canvas.drawRoundRect(wRect, u * 0.4f, u * 0.4f, card)
        drawWeather(canvas, wRect, u)

        // Bottom: what's playing.
        val npRect = RectF(left, bottom - u * 1.6f, right, bottom)
        canvas.drawRoundRect(npRect, u * 0.4f, u * 0.4f, card)
        val station = Radio.current
        val track = NowPlaying.read(context)
        val (label, line) = when {
            track != null -> "NOW PLAYING" to listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" · ")
            station != null -> "RADIO" to station.name
            else -> "INSTANT RADIO" to "Tap the radio button to pick a station"
        }
        // ◀ ▶ change radio station.
        val btn = npRect.height() * 0.7f
        val by = npRect.centerY()
        nextRect.set(npRect.right - u * 0.4f - btn, by - btn / 2, npRect.right - u * 0.4f, by + btn / 2)
        prevRect.set(nextRect.left - u * 0.3f - btn, nextRect.top, nextRect.left - u * 0.3f, nextRect.bottom)
        drawStepButton(canvas, prevRect, -1)
        drawStepButton(canvas, nextRect, 1)
        accent.textSize = u * 0.38f
        canvas.drawText(label, npRect.left + u * 0.5f, npRect.top + u * 0.6f, accent)
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.6f
        val maxW = prevRect.left - npRect.left - u
        canvas.drawText(ellipsize(line, text, maxW), npRect.left + u * 0.5f, npRect.top + u * 1.3f, text)
    }

    /** Compact solat card: next prayer and countdown on one line, today's times underneath. */
    private fun drawPrayer(canvas: Canvas, r: RectF, lat: Double, lng: Double, u: Float) {
        val now = System.currentTimeMillis()
        val next = Prayer.next(lat, lng, now)
        val fmt = DateFormat.getTimeFormat(context)
        val x = r.left + u * 0.4f
        val left = (next.atMillis - now).coerceAtLeast(0) / 1000
        text.typeface = bold
        text.textSize = u * 0.5f
        val head = "${next.name} ${fmt.format(Date(next.atMillis))}"
        canvas.drawText(head, x, r.top + u * 0.75f, text)
        warm.textSize = u * 0.5f
        canvas.drawText(
            String.format(Locale.US, "−%d:%02d:%02d", left / 3600, left / 60 % 60, left % 60),
            x + text.measureText(head) + u * 0.35f, r.top + u * 0.75f, warm,
        )
        // Today's timetable in two rows of three.
        val times = Prayer.forDay(lat, lng, Calendar.getInstance())
        val colW = (r.width() - u * 0.8f) / 3
        times.forEachIndexed { i, p ->
            val paint = if (p.name == next.name) warm else dim
            paint.textSize = u * 0.28f
            canvas.drawText("${p.name} ${fmt.format(Date(p.atMillis))}", x + (i % 3) * colW, r.top + u * 1.25f + (i / 3) * u * 0.42f, paint)
        }
    }

    private fun drawWeather(canvas: Canvas, r: RectF, u: Float) {
        val w = Weather.now
        val x = r.left + u * 0.5f
        if (w == null) {
            dim.textSize = u * 0.45f
            canvas.drawText("Weather loading…", x, r.centerY() + u * 0.15f, dim)
            return
        }
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 1.0f
        canvas.drawText(w.icon, x, r.top + u * 1.35f, text)
        text.typeface = bold
        text.textSize = u * 1.0f
        val tx = x + u * 1.5f
        val temp = String.format(Locale.US, "%.0f°", w.tempC)
        canvas.drawText(temp, tx, r.top + u * 1.35f, text)
        val lx = tx + text.measureText(temp) + u * 0.4f
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.45f
        canvas.drawText(w.label, lx, r.top + u * 0.95f, text)
        dim.textSize = u * 0.38f
        canvas.drawText(String.format(Locale.US, "H %.0f°  L %.0f°", w.highC, w.lowC), lx, r.top + u * 1.5f, dim)
    }

    private fun drawDock(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, u: Float) {
        canvas.drawRoundRect(RectF(l, t, r, b), u * 0.5f, u * 0.5f, card)
        dockRects.clear()
        if (System.currentTimeMillis() - dockLoadedAt > 15_000) {
            dockApps = Dock.load(context)
            dockLoadedAt = System.currentTimeMillis()
        }
        val apps = dockApps
        if (apps.isEmpty()) return
        val slot = (b - t) / maxOf(apps.size, 4)
        val size = min((r - l) * 0.62f, slot * 0.62f)
        apps.forEachIndexed { i, app ->
            val cx = (l + r) / 2
            val cy = t + (b - t - slot * apps.size) / 2 + slot * i + slot / 2
            val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            dockRects += RectF(l, cy - slot / 2, r, cy + slot / 2) to app
            val icon = icons.getOrPut(app.pkg) { Dock.icon(context, app) }
            if (icon != null) {
                icon.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                icon.draw(canvas)
            } else {
                canvas.drawOval(rect, bar)
                text.typeface = bold
                text.textSize = size * 0.45f
                val letter = app.label.take(1)
                canvas.drawText(letter, cx - text.measureText(letter) / 2, cy + size * 0.16f, text)
            }
        }
    }

    private fun drawStepButton(canvas: Canvas, r: RectF, dir: Int) {
        canvas.drawOval(r, shade)
        val path = android.graphics.Path()
        val w = r.width() * 0.22f
        if (dir < 0) {
            path.moveTo(r.centerX() + w * 0.6f, r.centerY() - w); path.lineTo(r.centerX() - w * 0.8f, r.centerY()); path.lineTo(r.centerX() + w * 0.6f, r.centerY() + w)
        } else {
            path.moveTo(r.centerX() - w * 0.6f, r.centerY() - w); path.lineTo(r.centerX() + w * 0.8f, r.centerY()); path.lineTo(r.centerX() - w * 0.6f, r.centerY() + w)
        }
        path.close()
        canvas.drawPath(path, text)
    }

    /** Draws the chosen picture filling the screen (cropped, not stretched), darkened so text stays readable. */
    private fun drawCover(canvas: Canvas, bmp: Bitmap) {
        val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val dst = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(bmp, null, dst, bgPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shade)
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
        val WARM = Color.parseColor("#F6AD55")
        val DIM = Color.parseColor("#8A94A6")
    }
}
