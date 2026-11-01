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
import com.alzadash.core.DashLayout
import com.alzadash.core.Feature
import com.alzadash.core.Features
import com.alzadash.core.Hijri
import com.alzadash.core.LyricLine
import com.alzadash.core.News
import com.alzadash.core.Lyrics
import com.alzadash.core.NowPlaying
import com.alzadash.core.Prayer
import com.alzadash.core.Prefs
import com.alzadash.core.Radio
import com.alzadash.core.Station
import com.alzadash.core.Track
import com.alzadash.core.TripTracker
import com.alzadash.core.Weather
import com.alzadash.core.Widget
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
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
    /** Called when a station in the radio row is tapped. */
    var onStationTap: (Station) -> Unit = {}

    /** Called when the ⚙ settings button is tapped. */
    var onSettingsTap: () -> Unit = {}

    /** Called when one of the small buttons above ⚙ is tapped: "mic", "radio", "nav" or "web". */
    var onQuick: (String) -> Unit = {}

    /**
     * Top-right corner still covered by Android Auto's one required button; widgets that
     * would sit under it start just below it.
     */
    private val reserved = RectF()

    /** Called from the lyrics page to open the music app itself on the car screen. */
    var onOpenApp: (String) -> Unit = {}

    /** Called when a news story is tapped, to open it in the car browser. */
    var onOpenLink: (String) -> Unit = {}

    private val newsPage by lazy { NewsPage(context) }
    private val newsRect = RectF()
    private var tickerX = 0f
    private var tickerFrame = 0L

    private var hijri = ""
    private var hijriDay = -1
    private val rowRect = RectF()
    private val chipRects = mutableListOf<Pair<RectF, Station>>()
    private val settingsRect = RectF()
    private val quickRects = mutableListOf<Pair<RectF, String>>()
    private val quickIcons = mutableMapOf<String, android.graphics.drawable.Drawable?>()
    private val musicRect = RectF()
    private val closeRect = RectF()
    private val openAppRect = RectF()
    private var track: Track? = null
    private var rowMax = 0f
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private var dragInRow = false
    private var downY = 0f
    private var lastY = 0f

    init {
        live[this] = Unit
    }

    private fun on(f: Feature) = Features.on(context, f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                lastX = event.x
                downY = event.y
                lastY = event.y
                dragging = false
                dragInRow = rowRect.contains(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (lyricsOpen) {
                    if (abs(event.y - downY) > height / 60f) dragging = true
                    if (dragging) scrollLyrics(lastY - event.y)
                    lastY = event.y
                    return true
                }
                if (dragInRow && abs(event.x - downX) > width / 80f) dragging = true
                if (dragging) {
                    scrollRow(lastX - event.x)
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> if (!dragging) tap(event.x, event.y)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (newsOpen) {
            when (val a = newsPage.tap(x, y)) {
                is NewsPage.Action.Close -> showNews(false)
                is NewsPage.Action.Open -> onOpenLink(a.url)
                null -> invalidate()
            }
            return
        }
        if (lyricsOpen) {
            when {
                closeRect.contains(x, y) -> showLyrics(false)
                openAppRect.contains(x, y) -> track?.pkg?.takeIf { it.isNotEmpty() }?.let { onOpenApp(it) }
            }
            return
        }
        when {
            settingsRect.contains(x, y) -> onSettingsTap()
            quickRects.any { it.first.contains(x, y) } -> onQuick(quickRects.first { it.first.contains(x, y) }.second)
            prevRect.contains(x, y) -> onRadioStep(-1)
            nextRect.contains(x, y) -> onRadioStep(1)
            musicRect.contains(x, y) && track != null -> showLyrics(true)
            newsRect.contains(x, y) -> showNews(true)
            rowRect.contains(x, y) -> chipRects.firstOrNull { it.first.contains(x, y) }?.let { onStationTap(it.second) }
            else -> dockRects.firstOrNull { it.first.contains(x, y) }?.let { onDockTap(it.second) }
        }
    }

    /**
     * When set, the dashboard is drawn as a narrow column inside this area (the map sits
     * beside it) and the shortcut bar is left out.
     */
    var panel: RectF? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(BG)
        if (on(Feature.BACKGROUND)) Background.get(context)?.let { drawCover(canvas, it) }
        rowRect.setEmpty()
        chipRects.clear()
        settingsRect.setEmpty()
        quickRects.clear()
        prevRect.setEmpty()
        nextRect.setEmpty()
        musicRect.setEmpty()
        newsRect.setEmpty()
        dockRects.clear()
        if (newsOpen) {
            newsPage.draw(canvas, panel ?: RectF(if (visibleArea.isEmpty) Rect(0, 0, width, height) else visibleArea))
            postInvalidateDelayed(50) // keeps the LATEST strip moving
            return
        }
        track = if (on(Feature.LYRICS) || on(Feature.NOW_PLAYING)) NowPlaying.read(context) else null
        if (lyricsOpen) {
            if (track == null) lyricsOpen = false else {
                drawLyricsPage(canvas, panel ?: RectF(if (visibleArea.isEmpty) Rect(0, 0, width, height) else visibleArea))
                return
            }
        }
        val (lat, lng) = TripTracker.position(context)
        Weather.refreshIfStale(lat, lng)
        panel?.let {
            dockRects.clear()
            drawColumn(canvas, it, lat, lng)
            return
        }
        // The dashboard fills the whole car screen, including the band Android Auto keeps
        // for its buttons; only the corner under its one remaining button is avoided.
        val a = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val v = visibleArea
        reserved.setEmpty()
        if (!v.isEmpty && v.top > 0) reserved.set(width - v.top * 1.7f, 0f, width.toFloat(), v.top.toFloat())
        else if (!v.isEmpty && v.right < width) reserved.set(v.right.toFloat(), 0f, width.toFloat(), (width - v.right) * 1.2f)
        val layout = DashLayout.get(context)
        var dockShown = false
        // Each widget sits where the phone's layout editor put it, its text sized to its box.
        for (w in Widget.values()) {
            if (w.feature != null && !on(w.feature)) continue
            val r = DashLayout.place(layout.getValue(w), a)
            if (!reserved.isEmpty && RectF.intersects(r, reserved) && r.bottom > reserved.bottom + 8) r.top = reserved.bottom + 4
            if (r.width() < 4 || r.height() < 4) continue
            when (w) {
                Widget.CLOCK -> {
                    val u = r.height() / 3.9f
                    drawClock(canvas, r.left, r.top - u * 0.2f, r.width(), u * 2.3f, u)
                }
                Widget.SPEED -> {
                    val u = min(r.height() / 1.8f, r.width() / 6f)
                    val speedY = r.top + u * 1.15f
                    drawSpeed(canvas, r.left, speedY, u * 1.35f, u)
                    dim.textSize = u * 0.4f
                    val trip = String.format(Locale.US, "Trip %.1f km  ·  %s  ·  avg %.0f km/h",
                        TripTracker.tripMeters / 1000, duration(System.currentTimeMillis() - TripTracker.tripStartMs), TripTracker.avgKmh())
                    canvas.drawText(ellipsize(trip, dim, r.width()), r.left, speedY + u * 0.62f, dim)
                }
                Widget.PRAYER -> {
                    val cols = if (r.width() / r.height() >= 2.8f) 3 else 2
                    val u = min(r.height() / (if (cols == 3) 2.0f else 2.5f), r.width() / (cols * 2.3f + 0.8f))
                    canvas.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
                    drawPrayer(canvas, r, lat, lng, u, cols)
                }
                Widget.WEATHER -> {
                    val u = min(r.height() / 1.75f, r.width() / 6.5f)
                    canvas.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
                    drawWeather(canvas, r, u)
                }
                Widget.NEWS -> drawNewsTicker(canvas, r, min(r.height() / 1.3f, r.width() / 8f))
                Widget.RADIO_ROW -> drawRadioRow(canvas, r, r.height() / 0.8f)
                Widget.NOW_PLAYING -> drawNowPlaying(canvas, r, min(r.height() / 2.0f, r.width() / 10f),
                    withButtons = !on(Feature.RADIO_ROW) || Prefs(context).orderedStations.isEmpty())
                Widget.DOCK -> {
                    drawDock(canvas, r.left, r.top, r.right, r.bottom, r.width() / 1.9f)
                    dockShown = true
                }
            }
        }
        // Without the shortcut bar the ⚙ button still sits in the bottom corner.
        if (!dockShown) {
            val s = min(a.width(), a.height()) * 0.1f
            settingsRect.set(a.right - s * 1.2f, a.bottom - s * 1.2f, a.right - s * 0.2f, a.bottom - s * 0.2f)
            canvas.drawRoundRect(settingsRect, s * 0.25f, s * 0.25f, card)
            drawGear(canvas, settingsRect)
            val grid = RectF(settingsRect.left, settingsRect.top - settingsRect.height() - s * 0.2f, settingsRect.right, settingsRect.top - s * 0.2f)
            canvas.drawRoundRect(grid, s * 0.25f, s * 0.25f, card)
            drawQuick(canvas, grid)
        }
    }

    /** Mic, radio, maps and web as four small buttons in a 2×2 square. */
    private fun drawQuick(canvas: Canvas, r: RectF) {
        val kinds = listOf("mic" to com.alzadash.R.drawable.ic_mic, "radio" to com.alzadash.R.drawable.ic_radio,
            "nav" to com.alzadash.R.drawable.ic_nav, "web" to com.alzadash.R.drawable.ic_globe)
        val cw = r.width() / 2
        val ch = r.height() / 2
        kinds.forEachIndexed { i, (kind, res) ->
            val cell = RectF(r.left + (i % 2) * cw, r.top + (i / 2) * ch, r.left + (i % 2 + 1) * cw, r.top + (i / 2 + 1) * ch)
            quickRects += cell to kind
            val size = min(cw, ch) * 0.5f
            val icon = quickIcons.getOrPut(kind) {
                try { context.getDrawable(res)?.mutate()?.apply { setTint(Color.WHITE) } } catch (_: Exception) { null }
            } ?: return@forEachIndexed
            icon.setBounds((cell.centerX() - size / 2).toInt(), (cell.centerY() - size / 2).toInt(),
                (cell.centerX() + size / 2).toInt(), (cell.centerY() + size / 2).toInt())
            icon.draw(canvas)
        }
    }

    /**
     * Headlines in a small card. While parked they scroll along; while driving one headline
     * shows at a time, changing every 10 seconds. Tap for the full news page.
     */
    private fun drawNewsTicker(canvas: Canvas, r: RectF, u: Float) {
        newsRect.set(r)
        canvas.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val items = News.ticker()
        val x = r.left + u * 0.4f
        accent.textSize = u * 0.3f
        canvas.drawText("NEWS", x, r.top + u * 0.45f, accent)
        dim.textSize = u * 0.26f
        val more = "Tap for all news ›"
        canvas.drawText(more, r.right - u * 0.4f - dim.measureText(more), r.top + u * 0.45f, dim)
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.42f
        val baseline = r.top + u * 1.0f
        if (items.isEmpty()) {
            dim.textSize = u * 0.38f
            canvas.drawText("Loading headlines…", x, baseline, dim)
            return
        }
        canvas.save()
        canvas.clipRect(x, r.top, r.right - u * 0.3f, r.bottom)
        if (TripTracker.isParked()) {
            val line = items.joinToString("   •   ") { it.title }
            val w = text.measureText(line + "   •   ")
            val now = System.currentTimeMillis()
            if (tickerFrame != 0L) tickerX += (now - tickerFrame) * u * 0.0012f
            tickerFrame = now
            if (tickerX > w) tickerX -= w
            canvas.drawText(line, x - tickerX, baseline, text)
            canvas.drawText(line, x - tickerX + w, baseline, text)
            postInvalidateDelayed(50)
        } else {
            tickerFrame = 0L
            val item = items[((System.currentTimeMillis() / 10_000) % items.size).toInt()]
            canvas.drawText(ellipsize(item.title, text, r.right - u * 0.3f - x), x, baseline, text)
        }
        canvas.restore()
    }

    /** Radio stations side by side, seven at a time; drag sideways to see the rest. */
    private fun drawRadioRow(canvas: Canvas, row: RectF, u: Float) {
        val stations = Prefs(context).orderedStations
        if (stations.isEmpty()) return
        // Small ◀ ▶ at the end of the row change station.
        val btn = row.height()
        nextRect.set(row.right - btn, row.top, row.right, row.bottom)
        prevRect.set(nextRect.left - btn - u * 0.15f, row.top, nextRect.left - u * 0.15f, row.bottom)
        drawStepButton(canvas, prevRect, -1)
        drawStepButton(canvas, nextRect, 1)
        val r = RectF(row.left, row.top, prevRect.left - u * 0.2f, row.bottom)
        rowRect.set(r)
        val gap = u * 0.2f
        val chipW = (r.width() - gap * 6) / 7
        rowMax = maxOf(0f, stations.size * (chipW + gap) - gap - r.width())
        rowScroll = rowScroll.coerceIn(0f, rowMax)
        canvas.save()
        canvas.clipRect(r)
        val current = Radio.current?.name
        stations.forEachIndexed { i, st ->
            val l = r.left + i * (chipW + gap) - rowScroll
            if (l > r.right || l + chipW < r.left) return@forEachIndexed
            val chip = RectF(l, r.top, l + chipW, r.bottom)
            chipRects += chip to st
            val playing = st.name == current
            canvas.drawRoundRect(chip, r.height() / 2, r.height() / 2, if (playing) bar else card)
            text.typeface = if (playing) bold else Typeface.DEFAULT
            text.textSize = u * 0.36f
            val name = ellipsize(st.name, text, chipW - u * 0.3f)
            canvas.drawText(name, chip.centerX() - text.measureText(name) / 2, chip.centerY() + u * 0.13f, text)
        }
        canvas.restore()
        // A thin bar shows how far along the row is scrolled.
        if (rowMax > 0) {
            val track = r.width() * r.width() / (r.width() + rowMax)
            val x = r.left + (r.width() - track) * (rowScroll / rowMax)
            canvas.drawRect(x, r.bottom + u * 0.08f, x + track, r.bottom + u * 0.14f, dim)
        }
    }

    /** The narrow layout used beside the map: everything stacked in one column. */
    private fun drawColumn(canvas: Canvas, r: RectF, lat: Double, lng: Double) {
        val u = min(r.height() / 12.6f, r.width() / 7.5f)
        val pad = u * 0.4f
        val left = r.left + pad
        val right = r.right - pad
        var y = r.top + pad
        drawClock(canvas, left, y - u * 0.5f, right - left, u * 1.8f, u)
        y += u * 3.4f
        if (on(Feature.SPEED)) {
            drawSpeed(canvas, left, y + u * 0.9f, u * 1.1f, u)
            y += u * 1.4f
        }
        if (on(Feature.PRAYER)) {
            val prayerRect = RectF(left, y, right, y + u * 2.5f)
            canvas.drawRoundRect(prayerRect, u * 0.4f, u * 0.4f, card)
            drawPrayer(canvas, prayerRect, lat, lng, u, 2)
            y = prayerRect.bottom + u * 0.3f
        }
        if (on(Feature.WEATHER)) {
            val wRect = RectF(left, y, right, y + u * 2.0f)
            canvas.drawRoundRect(wRect, u * 0.4f, u * 0.4f, card)
            drawWeather(canvas, wRect, u)
        }
        if (on(Feature.NOW_PLAYING)) drawNowPlaying(canvas, RectF(left, r.bottom - pad - u * 2.0f, right, r.bottom - pad), u)
    }

    /** Time, then the date and Hijri date. The time shrinks to fit [maxW] so it never runs into the cards. */
    private fun drawClock(canvas: Canvas, left: Float, top: Float, maxW: Float, size: Float, u: Float) {
        val now = Date()
        val time = DateFormat.getTimeFormat(context).format(now)
        text.typeface = light
        text.textSize = size
        val w = text.measureText(time)
        if (w > maxW) text.textSize = size * maxW / w
        canvas.drawText(time, left, top + u * 2.0f, text)
        dim.textSize = u * 0.55f
        canvas.drawText(ellipsize(DateFormat.format("EEEE, d MMMM yyyy", now).toString(), dim, maxW), left, top + u * 2.9f, dim)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (day != hijriDay) {
            hijri = try { Hijri.today() } catch (_: Exception) { "" }
            hijriDay = day
        }
        warm.textSize = u * 0.55f
        if (on(Feature.HIJRI)) canvas.drawText(hijri, left, top + u * 3.7f, warm)
    }

    private fun drawSpeed(canvas: Canvas, left: Float, baseline: Float, size: Float, u: Float) {
        val speed = TripTracker.speedKmh()
        text.typeface = bold
        text.textSize = size
        val speedText = speed?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
        canvas.drawText(speedText, left, baseline, text)
        accent.textSize = u * 0.5f
        canvas.drawText("km/h", left + text.measureText(speedText) + u * 0.25f, baseline, accent)
    }

    /**
     * What's playing: song and artist from YouTube, Spotify or any music app, with the lyric
     * line being sung underneath; or the radio station. ◀ ▶ change radio station.
     */
    private fun drawNowPlaying(canvas: Canvas, npRect: RectF, u: Float, withButtons: Boolean = true) {
        canvas.drawRoundRect(npRect, u * 0.4f, u * 0.4f, card)
        val station = Radio.current
        val t = track
        val x = npRect.left + u * 0.5f
        // ◀ ▶ live in the station row when it's shown; otherwise they sit here.
        val textRight = if (withButtons) {
            val btn = minOf(npRect.height() * 0.6f, u * 1.1f)
            val by = npRect.centerY()
            nextRect.set(npRect.right - u * 0.4f - btn, by - btn / 2, npRect.right - u * 0.4f, by + btn / 2)
            prevRect.set(nextRect.left - u * 0.3f - btn, nextRect.top, nextRect.left - u * 0.3f, nextRect.bottom)
            drawStepButton(canvas, prevRect, -1)
            drawStepButton(canvas, nextRect, 1)
            prevRect.left
        } else npRect.right
        val maxW = textRight - x - u * 0.4f
        accent.textSize = u * 0.36f
        text.typeface = bold
        text.textSize = u * 0.52f
        when {
            t != null -> {
                musicRect.set(npRect.left, npRect.top, textRight - u * 0.2f, npRect.bottom)
                canvas.drawText(ellipsize("NOW PLAYING · ${appName(t.pkg)}", accent, maxW), x, npRect.top + u * 0.55f, accent)
                canvas.drawText(ellipsize(t.title, text, maxW), x, npRect.top + u * 1.12f, text)
                dim.textSize = u * 0.4f
                val artistY = npRect.top + u * 1.6f
                canvas.drawText(ellipsize(t.artist, dim, maxW), x, artistY, dim)
                if (on(Feature.LYRICS) && npRect.height() >= u * 1.9f) {
                    val lines = Lyrics.get(t)
                    val lyric = when {
                        lines == null -> "Finding lyrics…"
                        lines.isEmpty() -> ""
                        else -> Lyrics.currentIndex(lines, t.positionMs).let { i ->
                            if (i >= 0) lines[i].text.ifBlank { "♪" } else if (lines[0].atMs < 0) "Tap for lyrics" else "♪"
                        }
                    }
                    // Lyrics go on the artist's line, after the name, so the card stays two lines tall.
                    val aw = dim.measureText(t.artist) + u * 0.4f
                    warm.textSize = u * 0.4f
                    if (lyric.isNotEmpty() && aw < maxW * 0.6f) {
                        canvas.drawText(ellipsize(lyric, warm, maxW - aw), x + aw, artistY, warm)
                    }
                }
            }
            station != null -> {
                canvas.drawText("RADIO · LIVE", x, npRect.top + u * 0.55f, accent)
                canvas.drawText(ellipsize(station.name, text, maxW), x, npRect.top + u * 1.12f, text)
                // The song or show the station says is on air.
                val onAir = Radio.streamTitle
                if (onAir != null && npRect.height() >= u * 1.75f) {
                    dim.textSize = u * 0.4f
                    canvas.drawText(ellipsize("♪ $onAir", dim, maxW), x, npRect.top + u * 1.6f, dim)
                }
            }
            else -> {
                canvas.drawText("INSTANT RADIO", x, npRect.top + u * 0.55f, accent)
                text.typeface = Typeface.DEFAULT
                canvas.drawText(ellipsize("Pick a station above", text, maxW), x, npRect.top + u * 1.12f, text)
            }
        }
    }

    private fun appName(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        "Music"
    }

    /**
     * The full lyrics of the song playing, following along as it's sung. While the car is
     * moving only the current and next line are shown, large, so it can be read at a glance.
     */
    private fun drawLyricsPage(canvas: Canvas, r: RectF) {
        val t = track ?: return
        val u = min(r.height() / 10f, r.width() / 12f)
        val pad = u * 0.5f
        val left = r.left + pad
        val right = r.right - pad
        // Header: song, artist, open-in-app and close buttons.
        val bh = u * 1.1f
        closeRect.set(right - bh, r.top + pad, right, r.top + pad + bh)
        canvas.drawOval(closeRect, card)
        text.typeface = bold
        text.textSize = u * 0.55f
        canvas.drawText("✕", closeRect.centerX() - text.measureText("✕") / 2, closeRect.centerY() + u * 0.2f, text)
        val appLabel = "Open ${appName(t.pkg)}"
        accent.textSize = u * 0.42f
        val ow = accent.measureText(appLabel) + u * 0.8f
        openAppRect.set(closeRect.left - u * 0.3f - ow, closeRect.top, closeRect.left - u * 0.3f, closeRect.bottom)
        canvas.drawRoundRect(openAppRect, bh / 2, bh / 2, card)
        canvas.drawText(appLabel, openAppRect.left + u * 0.4f, openAppRect.centerY() + u * 0.15f, accent)
        val headW = openAppRect.left - left - u * 0.3f
        canvas.drawText(ellipsize(t.title, text, headW), left, r.top + pad + u * 0.6f, text)
        dim.textSize = u * 0.42f
        canvas.drawText(ellipsize(t.artist, dim, headW), left, r.top + pad + u * 1.15f, dim)

        val body = RectF(left, r.top + pad + bh + u * 0.4f, right, r.bottom - pad)
        val lines = Lyrics.get(t)
        if (lines == null || lines.isEmpty()) {
            dim.textSize = u * 0.5f
            val msg = if (lines == null) "Finding lyrics…" else "No lyrics found for this song"
            canvas.drawText(msg, body.centerX() - dim.measureText(msg) / 2, body.centerY(), dim)
            return
        }
        val cur = Lyrics.currentIndex(lines, t.positionMs)
        if (!TripTracker.isParked() && cur >= 0) {
            drawGlance(canvas, body, lines, cur, u)
            return
        }
        drawAllLines(canvas, body, lines, cur, u)
    }

    private fun drawGlance(canvas: Canvas, body: RectF, lines: List<LyricLine>, cur: Int, u: Float) {
        warm.textSize = u * 0.85f
        warm.typeface = bold
        val now = ellipsize(lines[cur].text.ifBlank { "♪" }, warm, body.width())
        canvas.drawText(now, body.centerX() - warm.measureText(now) / 2, body.centerY(), warm)
        warm.typeface = Typeface.DEFAULT
        lines.getOrNull(cur + 1)?.let {
            dim.textSize = u * 0.55f
            val next = ellipsize(it.text, dim, body.width())
            canvas.drawText(next, body.centerX() - dim.measureText(next) / 2, body.centerY() + u * 1.1f, dim)
        }
    }

    private fun drawAllLines(canvas: Canvas, body: RectF, lines: List<LyricLine>, cur: Int, u: Float) {
        val lh = u * 0.8f
        val total = lines.size * lh
        // Follow the song unless the lyrics were scrolled by hand in the last few seconds.
        val follow = cur >= 0 && System.currentTimeMillis() - lyricsTouchedAt > 5000
        if (follow) lyricsScroll = cur * lh - body.height() / 3
        lyricsScroll = lyricsScroll.coerceIn(0f, maxOf(0f, total - body.height() / 2))
        canvas.save()
        canvas.clipRect(body)
        lines.forEachIndexed { i, line ->
            val y = body.top + lh + i * lh - lyricsScroll
            if (y < body.top || y > body.bottom + lh) return@forEachIndexed
            val p = if (i == cur) warm else if (cur >= 0 && i < cur) dim else text
            p.typeface = if (i == cur) bold else Typeface.DEFAULT
            p.textSize = if (i == cur) u * 0.6f else u * 0.5f
            canvas.drawText(ellipsize(line.text, p, body.width()), body.left, y, p)
        }
        canvas.restore()
        warm.typeface = Typeface.DEFAULT
    }

    /** Compact solat card: next prayer and countdown on one line, today's times underneath in [cols] columns. */
    private fun drawPrayer(canvas: Canvas, r: RectF, lat: Double, lng: Double, u: Float, cols: Int) {
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
        val times = Prayer.forDay(lat, lng, Calendar.getInstance())
        val colW = (r.width() - u * 0.8f) / cols
        times.forEachIndexed { i, p ->
            val paint = if (p.name == next.name) warm else dim
            paint.textSize = u * 0.28f
            canvas.drawText("${p.name} ${fmt.format(Date(p.atMillis))}", x + (i % cols) * colW, r.top + u * 1.25f + (i / cols) * u * 0.42f, paint)
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

    /** The shortcut bar, with the ⚙ settings button at its foot (shown even when the bar is turned off). */
    private fun drawDock(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, u: Float) {
        // Laid out down the bar, or across it when the bar was made wide in the layout editor.
        val across = r - l > b - t
        val thick = if (across) b - t else r - l
        val gear = if (across) RectF(r - thick, t, r, b) else RectF(l, b - thick, r, b)
        settingsRect.set(gear)
        canvas.drawRoundRect(RectF(l, t, r, b), thick * 0.25f, thick * 0.25f, card)
        drawGear(canvas, gear)
        // The four quick buttons sit in a small square next to ⚙ at the bottom right.
        val quick = if (across) RectF(gear.left - thick, t, gear.left, b) else RectF(l, gear.top - thick, r, gear.top)
        drawQuick(canvas, quick)
        if (System.currentTimeMillis() - dockLoadedAt > 15_000) {
            dockApps = Dock.load(context)
            dockLoadedAt = System.currentTimeMillis()
        }
        val apps = dockApps
        if (apps.isEmpty()) return
        val length = if (across) gear.left - thick - l else gear.top - thick - t
        val slot = length / maxOf(apps.size, 4)
        val size = min(thick * 0.62f, slot * 0.62f)
        val start = (length - slot * apps.size) / 2
        apps.forEachIndexed { i, app ->
            val mid = start + slot * i + slot / 2
            val cx = if (across) l + mid else (l + r) / 2
            val cy = if (across) (t + b) / 2 else t + mid
            val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            dockRects += (if (across) RectF(cx - slot / 2, t, cx + slot / 2, b) else RectF(l, cy - slot / 2, r, cy + slot / 2)) to app
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

    private fun drawGear(canvas: Canvas, r: RectF) {
        dim.textSize = r.height() * 0.5f
        val g = "\u2699\uFE0E"
        canvas.drawText(g, r.centerX() - dim.measureText(g) / 2, r.centerY() + r.height() * 0.17f, dim)
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
        /** How far the radio row is scrolled, shared by every dashboard on the car screen. */
        var rowScroll = 0f

        /** True while the full lyrics page is showing instead of the dashboard. */
        var lyricsOpen = false
            private set
        private var lyricsScroll = 0f
        private var lyricsTouchedAt = 0L

        /** True while the full news page is showing. */
        var newsOpen = false
            private set

        fun showNews(open: Boolean) {
            newsOpen = open
            if (open) lyricsOpen = false
            live.keys.toList().forEach { it.postInvalidate() }
        }

        fun showLyrics(open: Boolean) {
            if (open) newsOpen = false
            lyricsOpen = open
            lyricsTouchedAt = 0L
            live.keys.toList().forEach { it.postInvalidate() }
        }

        /** Scrolls the lyrics page by hand; it goes back to following the song after a few seconds. */
        fun scrollLyrics(dy: Float) {
            lyricsScroll += dy
            lyricsTouchedAt = System.currentTimeMillis()
            live.keys.toList().forEach { it.postInvalidate() }
        }
        private val live = WeakHashMap<DashboardView, Unit>()

        /** Scrolls the radio row, used for Android Auto's drag gesture. */
        fun scrollRow(dx: Float) {
            rowScroll = maxOf(0f, rowScroll + dx)
            live.keys.toList().forEach { it.postInvalidate() }
        }

        val BG = Color.parseColor("#0E1116")
        val CARD = Color.parseColor("#1A1F27")
        val ACCENT = Color.parseColor("#4FD1C5")
        val WARM = Color.parseColor("#F6AD55")
        val DIM = Color.parseColor("#8A94A6")
    }
}
