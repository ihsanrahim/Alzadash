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
import com.alzadash.core.Look
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

    /** The YouTube card: "search", "home", "subs" or "history". */
    var onYouTube: (String) -> Unit = {}

    /** Drawn as a preview in the phone's editor: no taps, nothing that changes car state. */
    var preview = false

    private val ytRects = mutableListOf<Pair<RectF, String>>()
    private val playerRects = mutableListOf<Pair<RectF, Int>>()

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
    private val mirrorRect = RectF()
    private val appsRect = RectF()
    private val panelRect = RectF()
    /** Whether the apps row is folded out. */
    private var dockOpen = false
    private val solidCard = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F0202A3A") }
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
        // Redraw the moment a song changes or its lyrics arrive, not on the next tick.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val redraw: () -> Unit = { main.post { live.keys.toList().forEach { it.invalidate() } } }
        NowPlaying.onChange = redraw
        Lyrics.onFound = redraw
    }

    private fun on(f: Feature) = Features.on(context, f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (preview) return false
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
        // The folded-out apps row takes taps first; a tap anywhere else folds it away.
        if (dockOpen) {
            val hit = dockRects.firstOrNull { it.first.contains(x, y) }
            dockOpen = false
            invalidate()
            if (hit != null) { onDockTap(hit.second); return }
            if (appsRect.contains(x, y) || panelRect.contains(x, y)) return
        }
        ytRects.firstOrNull { it.first.contains(x, y) }?.let { onYouTube(it.second); return }
        playerRects.firstOrNull { it.first.contains(x, y) }?.let { NowPlaying.control(context, it.second); postInvalidateDelayed(300); return }
        when {
            appsRect.contains(x, y) -> { dockOpen = true; invalidate() }
            mirrorRect.contains(x, y) -> onQuick("mirror")
            settingsRect.contains(x, y) -> onSettingsTap()
            quickRects.any { it.first.contains(x, y) } -> onQuick(quickRects.first { it.first.contains(x, y) }.second)
            prevRect.contains(x, y) -> onRadioStep(-1)
            nextRect.contains(x, y) -> onRadioStep(1)
            musicRect.contains(x, y) && track != null -> showLyrics(true)
            newsRect.contains(x, y) -> showNews(true)
            rowRect.contains(x, y) -> chipRects.firstOrNull { it.first.contains(x, y) }?.let { onStationTap(it.second) }
            else -> {}
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
        ytRects.clear()
        playerRects.clear()
        mirrorRect.setEmpty()
        appsRect.setEmpty()
        panelRect.setEmpty()
        prevRect.setEmpty()
        nextRect.setEmpty()
        musicRect.setEmpty()
        newsRect.setEmpty()
        dockRects.clear()
        if (newsOpen && !preview) {
            newsPage.draw(canvas, panel ?: RectF(if (visibleArea.isEmpty) Rect(0, 0, width, height) else visibleArea))
            postInvalidateDelayed(50) // keeps the LATEST strip moving
            return
        }
        // Follows whatever is sounding: a music app that's playing wins; with the radio off,
        // a paused song stays on the card too.
        track = if (on(Feature.LYRICS) || on(Feature.NOW_PLAYING)) {
            NowPlaying.read(context) ?: if (!Radio.playing) NowPlaying.readAny(context) else null
        } else null
        if (lyricsOpen && !preview) {
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
            if (!DashLayout.shown(context, w)) continue
            val r = DashLayout.place(layout.getValue(w), a)
            val st = DashLayout.style(context, w)
            val k = st.scale
            if (!reserved.isEmpty && RectF.intersects(r, reserved) && r.bottom > reserved.bottom + 8) r.top = reserved.bottom + 4
            if (r.width() < 4 || r.height() < 4) continue
            when (w) {
                Widget.CLOCK -> {
                    val u = r.height() / 3.9f * k
                    drawClock(canvas, r.left, r.top - u * 0.2f, r.width(), u * 2.3f, u, st.align)
                }
                Widget.SPEED -> {
                    val u = min(r.height() / 1.8f, r.width() / 6f) * k
                    val speedY = r.top + u * 1.15f
                    drawSpeed(canvas, r.left, speedY, u * 1.35f, u)
                    dim.textSize = u * 0.4f
                    val trip = String.format(Locale.US, "Trip %.1f km  ·  %s  ·  avg %.0f km/h",
                        TripTracker.tripMeters / 1000, duration(System.currentTimeMillis() - TripTracker.tripStartMs), TripTracker.avgKmh())
                    canvas.drawText(ellipsize(trip, dim, r.width()), r.left, speedY + u * 0.62f, dim)
                }
                Widget.PRAYER -> {
                    val cols = if (r.width() / r.height() >= 2.8f) 3 else 2
                    val u = min(r.height() / (if (cols == 3) 2.0f else 2.5f), r.width() / (cols * 2.3f + 0.8f)) * k
                    canvas.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
                    drawPrayer(canvas, r, lat, lng, u, cols)
                }
                Widget.WEATHER -> {
                    val u = min(r.height() / 1.75f, r.width() / 6.5f) * k
                    canvas.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
                    drawWeather(canvas, r, u)
                }
                Widget.NEWS -> drawNewsTicker(canvas, r, min(r.height() / 1.3f, r.width() / 8f) * k)
                Widget.RADIO_ROW -> drawRadioRow(canvas, r, r.height() / 0.8f * k)
                Widget.NOW_PLAYING -> drawNowPlaying(canvas, r, min(r.height() / 2.0f, r.width() / 10f) * k,
                    withButtons = !on(Feature.RADIO_ROW) || Prefs(context).orderedStations.isEmpty())
                Widget.DOCK -> {
                    drawDock(canvas, r.left, r.top, r.right, r.bottom, r.width() / 1.9f)
                    dockShown = true
                }
                Widget.CALENDAR -> drawCalendar(canvas, r, min(r.height() / 3.2f, r.width() / 7f) * k)
                Widget.AIR -> drawAir(canvas, r, min(r.height() / 5.2f, r.width() / 4.2f) * k)
                Widget.RADAR -> drawRadar(canvas, r, min(r.height(), r.width()) / 4.5f * k, lat, lng)
                Widget.PLAYER -> drawPlayer(canvas, r, min(r.height() / 3.2f, r.width() / 9f) * k)
                Widget.YOUTUBE -> drawYouTube(canvas, r, min(r.height() / 2.4f, r.width() / 7.5f) * k)
                Widget.WEATHER2 -> drawWeather2(canvas, r, min(r.height() / 2.6f, r.width() / 4.6f) * k)
            }
        }
        // Without the shortcut bar the ⚙ button still sits in the bottom corner.
        if (!dockShown) {
            val s = min(a.width(), a.height()) * 0.1f
            settingsRect.set(a.right - s * 1.2f, a.bottom - s * 1.2f, a.right - s * 0.2f, a.bottom - s * 0.2f)
            canvas.drawRoundRect(settingsRect, s * 0.25f, s * 0.25f, card)
            drawGear(canvas, settingsRect)
            mirrorRect.set(settingsRect.left, settingsRect.top - settingsRect.height() - s * 0.2f, settingsRect.right, settingsRect.top - s * 0.2f)
            canvas.drawRoundRect(mirrorRect, s * 0.25f, s * 0.25f, card)
            drawIcon(canvas, com.alzadash.R.drawable.ic_mirror, mirrorRect, 0.5f)
            val grid = RectF(mirrorRect.left, mirrorRect.top - mirrorRect.height() - s * 0.2f, mirrorRect.right, mirrorRect.top - s * 0.2f)
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
            val size = min(cw, ch) * 0.5f * Look.buttonScale(context)
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
        // The bottom card is picked in the phone app (Car card next to the map).
        val bottom = RectF(left, r.bottom - pad - u * 2.6f, right, r.bottom - pad)
        when (Look.splitCard(context)) {
            "Nothing" -> {}
            "Lyrics" -> drawLyricCard(canvas, bottom, u)
            "Music player" -> drawPlayer(canvas, bottom, u * 0.8f)
            "Calendar" -> drawCalendar(canvas, bottom, u * 0.75f)
            "Air quality" -> drawAir(canvas, RectF(bottom.left, bottom.top - u * 1.4f, bottom.right, bottom.bottom), u * 0.7f)
            else -> if (on(Feature.NOW_PLAYING)) drawNowPlaying(canvas, RectF(left, r.bottom - pad - u * 2.0f, right, r.bottom - pad), u)
        }
    }

    /** The line being sung, large, with the next one under it. */
    private fun drawLyricCard(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val t = track
        val x = r.left + u * 0.4f
        val w = r.width() - u * 0.8f
        if (t == null) {
            dim.textSize = u * 0.45f
            c.drawText("No song playing", x, r.centerY() + u * 0.15f, dim)
            return
        }
        accent.textSize = u * 0.34f
        c.drawText(ellipsize("${t.title} · ${t.artist}", accent, w), x, r.top + u * 0.55f, accent)
        val lines = Lyrics.get(t)
        val i = if (lines.isNullOrEmpty()) -1 else Lyrics.currentIndex(lines, t.positionMs)
        val now = when {
            lines == null -> "Finding lyrics…"
            lines.isEmpty() -> "No lyrics found"
            i < 0 -> "♪"
            else -> lines[i].text.ifBlank { "♪" }
        }
        text.typeface = bold
        text.textSize = u * 0.55f
        c.drawText(ellipsize(now, text, w), x, r.top + u * 1.35f, text)
        val next = if (lines != null && i + 1 in lines.indices) lines[i + 1].text else ""
        dim.textSize = u * 0.42f
        c.drawText(ellipsize(next, dim, w), x, r.top + u * 2.0f, dim)
    }

    /** Time, then the date and Hijri date. The time shrinks to fit [maxW] so it never runs into the cards. */
    private fun drawClock(canvas: Canvas, left: Float, top: Float, maxW: Float, size: Float, u: Float, align: Int = 0) {
        val now = Date()
        val time = when (com.alzadash.core.Look.clock24(context)) {
            1 -> DateFormat.format("HH:mm", now).toString()
            0 -> DateFormat.format("h:mm a", now).toString()
            else -> DateFormat.getTimeFormat(context).format(now)
        }
        val font = com.alzadash.core.Look.clockFont(context)
        text.typeface = com.alzadash.core.Look.typeface(font)
        text.letterSpacing = com.alzadash.core.Look.letterSpacing(font)
        text.textSize = size
        val w = text.measureText(time)
        if (w > maxW) text.textSize = size * maxW / w
        // Left, centre or right inside the widget.
        fun x(p: Paint, s: String) = when (align) {
            1 -> left + (maxW - p.measureText(s)) / 2
            2 -> left + maxW - p.measureText(s)
            else -> left
        }
        canvas.drawText(time, x(text, time), top + u * 2.0f, text)
        text.letterSpacing = 0f
        dim.textSize = u * 0.55f
        val date = ellipsize(DateFormat.format("EEEE, d MMMM yyyy", now).toString(), dim, maxW)
        canvas.drawText(date, x(dim, date), top + u * 2.9f, dim)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (day != hijriDay) {
            hijri = try { Hijri.today() } catch (_: Exception) { "" }
            hijriDay = day
        }
        warm.textSize = u * 0.55f
        if (on(Feature.HIJRI)) canvas.drawText(hijri, when (align) {
            1 -> left + (maxW - warm.measureText(hijri)) / 2
            2 -> left + maxW - warm.measureText(hijri)
            else -> left
        }, top + u * 3.7f, warm)
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
                canvas.drawText(ellipsize((if (t.playing) "NOW PLAYING · " else "PAUSED · ") + appName(t.pkg), accent, maxW), x, npRect.top + u * 0.55f, accent)
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
                canvas.drawText(if (Radio.playing) "RADIO · LIVE" else "RADIO · PAUSED", x, npRect.top + u * 0.55f, accent)
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

    /**
     * The shortcut bar. From the top: the apps button (its apps fold out sideways when tapped),
     * then at the foot the four quick buttons, phone mirroring and ⚙ settings.
     */
    private fun drawDock(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, u: Float) {
        // Laid out down the bar, or across it when the bar was made wide in the layout editor.
        val across = r - l > b - t
        val thick = if (across) b - t else r - l
        fun slotFromEnd(n: Float, size: Float) =
            if (across) RectF(r - n - size, t, r - n, b) else RectF(l, b - n - size, r, b - n)
        val gear = slotFromEnd(0f, thick)
        settingsRect.set(gear)
        canvas.drawRoundRect(RectF(l, t, r, b), thick * 0.25f, thick * 0.25f, card)
        drawGear(canvas, gear)
        mirrorRect.set(slotFromEnd(thick, thick * 0.85f))
        drawIcon(canvas, com.alzadash.R.drawable.ic_mirror, mirrorRect, 0.5f)
        val quick = slotFromEnd(thick * 1.85f, thick)
        drawQuick(canvas, quick)

        // Apps that open on the car screen (they need Shizuku) sit behind one button.
        val apps = if (across) RectF(l, t, l + thick, b) else RectF(l, t, r, t + thick)
        appsRect.set(apps)
        if (dockOpen) canvas.drawRoundRect(apps, thick * 0.25f, thick * 0.25f, bar)
        drawIcon(canvas, com.alzadash.R.drawable.ic_apps, apps, 0.45f)
        if (!dockOpen) return
        if (System.currentTimeMillis() - dockLoadedAt > 15_000) {
            dockApps = Dock.load(context)
            dockLoadedAt = System.currentTimeMillis()
        }
        val list = dockApps
        val gap = thick * 0.15f
        val n = maxOf(list.size, 1)
        // Folds out to the left of the bar (or upward from a bar laid across the bottom), in one row.
        val panel = if (across) RectF(apps.left, apps.top - gap - thick, apps.left + n * thick, apps.top - gap)
        else RectF(apps.left - gap - n * thick, apps.top, apps.left - gap, apps.bottom)
        if (panel.left < 0) panel.offset(-panel.left, 0f)
        panelRect.set(panel)
        canvas.drawRoundRect(panel, thick * 0.25f, thick * 0.25f, solidCard)
        if (list.isEmpty()) {
            dim.textSize = thick * 0.2f
            val msg = "Pick apps in the phone app"
            canvas.drawText(ellipsize(msg, dim, panel.width() - thick * 0.2f), panel.left + thick * 0.1f, panel.centerY() + thick * 0.07f, dim)
            return
        }
        val size = thick * 0.62f
        list.forEachIndexed { i, app ->
            val cx = panel.left + thick * i + thick / 2
            val cy = panel.centerY()
            val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            dockRects += RectF(cx - thick / 2, panel.top, cx + thick / 2, panel.bottom) to app
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

    private fun drawIcon(canvas: Canvas, res: Int, r: RectF, frac: Float) {
        val icon = quickIcons.getOrPut("res$res") {
            try { context.getDrawable(res)?.mutate()?.apply { setTint(Color.WHITE) } } catch (_: Exception) { null }
        } ?: return
        val size = min(r.width(), r.height()) * frac * Look.buttonScale(context)
        icon.setBounds((r.centerX() - size / 2).toInt(), (r.centerY() - size / 2).toInt(),
            (r.centerX() + size / 2).toInt(), (r.centerY() + size / 2).toInt())
        icon.draw(canvas)
    }


    // ---------- Newer widgets ----------


    /** Next events from the phone's calendar: day, time and title. */
    private fun drawCalendar(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val x = r.left + u * 0.4f
        val w = r.width() - u * 0.8f
        if (!com.alzadash.core.CalendarEvents.allowed(context)) {
            dim.textSize = u * 0.38f
            c.drawText(ellipsize("Allow Calendar in the phone app", dim, w), x, r.centerY(), dim)
            return
        }
        com.alzadash.core.CalendarEvents.refresh(context)
        val events = com.alzadash.core.CalendarEvents.events
        if (events.isNullOrEmpty()) {
            dim.textSize = u * 0.4f
            c.drawText(if (events == null) "Calendar loading…" else "Nothing coming up", x, r.centerY() + u * 0.15f, dim)
            return
        }
        var y = r.top + u * 0.3f
        var lastDay = ""
        for (e in events) {
            val day = com.alzadash.core.CalendarEvents.dayLabel(e.begin, e.allDay)
            if (day != lastDay) {
                if (y + u * 0.5f > r.bottom) break
                dim.textSize = u * 0.32f
                y += u * 0.42f
                c.drawText(day, x, y, dim)
                lastDay = day
            }
            if (y + u * 0.55f > r.bottom - u * 0.1f) break
            y += u * 0.5f
            accent.textSize = u * 0.34f
            val time = if (e.allDay) "All day" else DateFormat.getTimeFormat(context).format(Date(e.begin))
            c.drawRect(x, y - u * 0.34f, x + u * 0.06f, y + u * 0.06f, accent)
            c.drawText(time, x + u * 0.2f, y, accent)
            text.typeface = bold
            text.textSize = u * 0.36f
            val tx = x + u * 0.2f + accent.measureText("00:00 pm") + u * 0.2f
            c.drawText(ellipsize(e.title, text, r.right - u * 0.3f - tx), tx, y, text)
        }
    }

    /** US AQI with its colour band, PM2.5, UV, humidity and wind. */
    private fun drawAir(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val cx = r.centerX()
        dim.textSize = u * 0.4f
        var y = r.top + u * 0.7f
        center(c, "Air quality", cx, y, dim)
        val aqi = com.alzadash.core.AirQuality.aqi
        if (aqi == null) {
            center(c, "Loading…", cx, r.centerY(), dim)
            return
        }
        val (label, colour) = com.alzadash.core.AirQuality.band(aqi)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; typeface = bold; textSize = u * 1.3f }
        y += u * 1.35f
        center(c, aqi.toString(), cx, y, big)
        text.typeface = bold
        text.textSize = u * 0.36f
        val pw = min(text.measureText(label) + u * 0.5f, r.width() - u * 0.4f)
        val pill = RectF(cx - pw / 2, y + u * 0.2f, cx + pw / 2, y + u * 0.75f)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.STROKE; strokeWidth = u * 0.05f }
        c.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, stroke)
        center(c, ellipsize(label, text, pw - u * 0.2f), cx, pill.bottom - u * 0.15f, text)
        y = pill.bottom + u * 0.15f
        val w = Weather.now
        val lines = listOfNotNull(
            com.alzadash.core.AirQuality.pm25?.let { String.format(Locale.US, "PM2.5 %.0f", it) },
            w?.uv?.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "UV %.0f %s", it, uvWord(it)) },
            w?.humidity?.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "Humidity %.0f%%", it) },
            w?.windKmh?.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "Wind %.0f km/h", it) },
        )
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.36f
        for (line in lines) {
            y += u * 0.5f
            if (y > r.bottom - u * 0.1f) break
            center(c, ellipsize(line, text, r.width() - u * 0.3f), cx, y, text)
        }
    }

    private fun uvWord(uv: Double) = when {
        uv < 3 -> "Low"
        uv < 6 -> "Moderate"
        uv < 8 -> "High"
        else -> "Very high"
    }

    private fun center(c: Canvas, s: String, cx: Float, y: Float, p: Paint) = c.drawText(s, cx - p.measureText(s) / 2, y, p)

    /** Live rain radar around the car on a small map. */
    private fun drawRadar(c: Canvas, r: RectF, u: Float, lat: Double, lng: Double) {
        c.drawRoundRect(r, u * 0.3f, u * 0.3f, card)
        com.alzadash.core.Radar.refresh(lat, lng)
        val img = com.alzadash.core.Radar.image
        if (img == null) {
            dim.textSize = u * 0.4f
            center(c, "Rain radar loading…", r.centerX(), r.centerY(), dim)
            return
        }
        // Crop the picture around the car to fill the widget.
        val scale = maxOf(r.width() / img.width, r.height() / img.height) * 1.4f
        val dx = r.centerX() - com.alzadash.core.Radar.dotX * scale
        val dy = r.centerY() - com.alzadash.core.Radar.dotY * scale
        c.save()
        val clip = android.graphics.Path().apply { addRoundRect(r, u * 0.3f, u * 0.3f, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(img, null, RectF(dx, dy, dx + img.width * scale, dy + img.height * scale), bgPaint)
        c.restore()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        c.drawCircle(r.centerX(), r.centerY(), u * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawCircle(r.centerX(), r.centerY(), u * 0.12f, dot)
        val label = "Rain radar · " + DateFormat.getTimeFormat(context).format(Date(com.alzadash.core.Radar.takenAt))
        text.typeface = bold
        text.textSize = u * 0.3f
        val bg = RectF(r.left + u * 0.15f, r.top + u * 0.15f, r.left + u * 0.45f + text.measureText(label), r.top + u * 0.6f)
        c.drawRoundRect(bg, u * 0.1f, u * 0.1f, shade)
        c.drawText(label, bg.left + u * 0.15f, bg.bottom - u * 0.13f, text)
        val credit = "© OpenStreetMap · RainViewer"
        dim.textSize = u * 0.2f
        c.drawText(credit, r.right - u * 0.15f - dim.measureText(credit), r.bottom - u * 0.12f, dim)
    }

    /** Album art, song, progress and ◀ ⏯ ▶ for whichever music app is playing (or paused). */
    private fun drawPlayer(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val t = NowPlaying.readAny(context)
        val pad = u * 0.35f
        val artSize = min(r.height() - pad * 2, r.width() * 0.35f)
        val art = RectF(r.left + pad, r.centerY() - artSize / 2, r.left + pad + artSize, r.centerY() + artSize / 2)
        val bmp = if (t != null) NowPlaying.art(context) else null
        if (bmp != null) {
            c.save()
            c.clipPath(android.graphics.Path().apply { addRoundRect(art, u * 0.25f, u * 0.25f, android.graphics.Path.Direction.CW) })
            val sc = maxOf(art.width() / bmp.width, art.height() / bmp.height)
            val w = bmp.width * sc
            val h = bmp.height * sc
            c.drawBitmap(bmp, null, RectF(art.centerX() - w / 2, art.centerY() - h / 2, art.centerX() + w / 2, art.centerY() + h / 2), bgPaint)
            c.restore()
        } else {
            c.drawRoundRect(art, u * 0.25f, u * 0.25f, shade)
            text.typeface = Typeface.DEFAULT
            text.textSize = artSize * 0.3f
            center(c, "♪", art.centerX(), art.centerY() + artSize * 0.1f, text)
        }
        val x = art.right + pad
        val w = r.right - pad - x
        if (w < u) return
        dim.textSize = u * 0.32f
        c.drawText(ellipsize(t?.let { appName(it.pkg) } ?: "Music", dim, w), x, r.top + u * 0.75f, dim)
        text.typeface = bold
        text.textSize = u * 0.5f
        c.drawText(ellipsize(t?.title ?: "Nothing playing", text, w), x, r.top + u * 1.35f, text)
        if (t != null && t.artist.isNotEmpty()) {
            dim.textSize = u * 0.36f
            c.drawText(ellipsize(t.artist, dim, w), x, r.top + u * 1.8f, dim)
        }
        // Progress bar.
        val by = r.top + u * 2.15f
        c.drawRoundRect(RectF(x, by, x + w, by + u * 0.08f), u * 0.04f, u * 0.04f, shade)
        if (t != null && t.durationMs > 0) {
            val f = (t.positionMs.toFloat() / t.durationMs).coerceIn(0f, 1f)
            c.drawRoundRect(RectF(x, by, x + w * f, by + u * 0.08f), u * 0.04f, u * 0.04f, text)
        }
        // ◀ ⏯ ▶
        val b = min(u * 0.75f, (r.bottom - by - u * 0.3f)).coerceAtLeast(u * 0.4f) * Look.buttonScale(context)
        val cy = (by + r.bottom) / 2 + u * 0.05f
        val prev = RectF(x, cy - b / 2, x + b, cy + b / 2)
        val play = RectF(prev.right + u * 0.3f, cy - b * 0.6f, prev.right + u * 0.3f + b * 1.2f, cy + b * 0.6f)
        val next = RectF(play.right + u * 0.3f, cy - b / 2, play.right + u * 0.3f + b, cy + b / 2)
        drawStepButton(c, prev, -1)
        drawStepButton(c, next, 1)
        c.drawOval(play, text)
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        if (t?.playing == true) {
            val bw = play.width() * 0.1f
            c.drawRect(play.centerX() - bw * 1.6f, play.centerY() - bw * 2, play.centerX() - bw * 0.6f, play.centerY() + bw * 2, dark)
            c.drawRect(play.centerX() + bw * 0.6f, play.centerY() - bw * 2, play.centerX() + bw * 1.6f, play.centerY() + bw * 2, dark)
        } else {
            val tri = android.graphics.Path()
            val s = play.width() * 0.18f
            tri.moveTo(play.centerX() - s * 0.7f, play.centerY() - s)
            tri.lineTo(play.centerX() + s, play.centerY())
            tri.lineTo(play.centerX() - s * 0.7f, play.centerY() + s)
            tri.close()
            c.drawPath(tri, dark)
        }
        playerRects += prev to -1
        playerRects += play to 0
        playerRects += next to 1
    }

    /** "Search YouTube" by voice, plus Home, Subscriptions and History. */
    private fun drawYouTube(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val pad = u * 0.25f
        val half = (r.height() - pad * 3) / 2
        val search = RectF(r.left + pad, r.top + pad, r.right - pad, r.top + pad + half)
        c.drawRoundRect(search, search.height() / 2, search.height() / 2, shade)
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF0033") }
        val logo = RectF(search.left + u * 0.3f, search.centerY() - u * 0.22f, search.left + u * 0.9f, search.centerY() + u * 0.22f)
        c.drawRoundRect(logo, u * 0.1f, u * 0.1f, red)
        val tri = android.graphics.Path().apply {
            moveTo(logo.centerX() - u * 0.08f, logo.centerY() - u * 0.12f)
            lineTo(logo.centerX() + u * 0.12f, logo.centerY())
            lineTo(logo.centerX() - u * 0.08f, logo.centerY() + u * 0.12f)
            close()
        }
        c.drawPath(tri, text)
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 0.36f
        c.drawText(ellipsize("Search YouTube", text, search.right - logo.right - u * 0.4f), logo.right + u * 0.25f, search.centerY() + u * 0.13f, text)
        ytRects += search to "search"
        val labels = listOf("Home" to "home", "Subscriptions" to "subs", "History" to "history")
        val bw = (r.width() - pad * 4) / 3
        labels.forEachIndexed { i, (label, kind) ->
            val b = RectF(r.left + pad + i * (bw + pad), search.bottom + pad, r.left + pad + i * (bw + pad) + bw, r.bottom - pad)
            c.drawRoundRect(b, b.height() / 2, b.height() / 2, shade)
            text.textSize = u * 0.3f
            val l = ellipsize(label, text, b.width() - u * 0.2f)
            center(c, l, b.centerX(), b.centerY() + u * 0.1f, text)
            ytRects += b to kind
        }
    }

    /** Weather with "feels like" and UV. */
    private fun drawWeather2(c: Canvas, r: RectF, u: Float) {
        c.drawRoundRect(r, u * 0.4f, u * 0.4f, card)
        val w = Weather.now
        val x = r.left + u * 0.4f
        if (w == null) {
            dim.textSize = u * 0.4f
            c.drawText("Weather loading…", x, r.centerY(), dim)
            return
        }
        text.typeface = Typeface.DEFAULT
        text.textSize = u * 1.1f
        val temp = String.format(Locale.US, "%.0f°", w.tempC)
        c.drawText(temp, x, r.top + u * 1.45f, text)
        text.textSize = u * 0.8f
        c.drawText(w.icon, x + text.measureText(temp) * 1.4f + u * 0.2f, r.top + u * 1.35f, text)
        val rx = r.right - u * 0.35f
        text.typeface = bold
        text.textSize = u * 0.38f
        c.drawText(ellipsize(w.label, text, r.width() * 0.45f), rx - min(text.measureText(w.label), r.width() * 0.45f), r.top + u * 0.6f, text)
        dim.textSize = u * 0.34f
        val details = listOfNotNull(
            w.feelsC.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "Feels like %.0f°", it) },
            w.uv.takeIf { !it.isNaN() }?.let { String.format(Locale.US, "UV %.0f", it) },
        ).joinToString(" · ")
        c.drawText(ellipsize(details, dim, r.width() - u * 0.7f), x, r.bottom - u * 0.35f, dim)
    }

    private fun drawGear(canvas: Canvas, r: RectF) {
        dim.textSize = r.height() * 0.5f * Look.buttonScale(context)
        val g = "\u2699\uFE0E"
        canvas.drawText(g, r.centerX() - dim.measureText(g) / 2, r.centerY() + r.height() * 0.17f, dim)
    }

    private fun drawStepButton(canvas: Canvas, rect: RectF, dir: Int) {
        // Sized by the "Car button size" setting, around the same centre.
        val k = Look.buttonScale(context)
        val r = RectF(rect.centerX() - rect.width() * k / 2, rect.centerY() - rect.height() * k / 2,
            rect.centerX() + rect.width() * k / 2, rect.centerY() + rect.height() * k / 2)
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
