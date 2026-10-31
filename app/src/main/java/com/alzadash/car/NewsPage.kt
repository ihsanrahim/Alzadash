package com.alzadash.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.format.DateFormat
import com.alzadash.core.News
import com.alzadash.core.NewsItem
import com.alzadash.core.NowPlaying
import com.alzadash.core.Prayer
import com.alzadash.core.Radio
import com.alzadash.core.TripTracker
import com.alzadash.core.Weather
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * The full news page: section tabs, a big top story with its picture, weather, next prayer
 * and what's playing on the side, four more stories underneath and a scrolling LATEST strip.
 * Tapping a story opens it in the car browser.
 */
class NewsPage(private val context: Context) {
    sealed class Action {
        object Close : Action()
        data class Open(val url: String) : Action()
    }

    private var section = News.sections[0]
    private var lastBm = News.sections.first { it.group == News.BM }
    private val targets = mutableListOf<Pair<RectF, () -> Action?>>()
    private var tickerX = 0f
    private var lastFrame = 0L

    private val bg = Paint().apply { color = Color.parseColor("#0B1F4A") }
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#163A7A"); alpha = 230 }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53E3E") }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C3CFEA") }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE }
    private val img = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    fun tap(x: Float, y: Float): Action? = targets.firstOrNull { it.first.contains(x, y) }?.second?.invoke()

    fun draw(canvas: Canvas, r: RectF) {
        targets.clear()
        canvas.drawRect(r, bg)
        val u = min(r.height() / 10f, r.width() / 17f)
        val pad = u * 0.4f
        val left = r.left + pad
        val right = r.right - pad
        var y = r.top + pad

        // Header: name, sections, clock and close.
        white.typeface = bold
        white.textSize = u * 0.5f
        canvas.drawText("AlzaDash News", left, y + u * 0.55f, white)
        val close = RectF(right - u * 0.8f, y, right, y + u * 0.8f)
        canvas.drawOval(close, panel)
        white.textSize = u * 0.4f
        canvas.drawText("✕", close.centerX() - white.measureText("✕") / 2, close.centerY() + u * 0.14f, white)
        targets += close to { Action.Close }
        val now = Date()
        white.textSize = u * 0.4f
        val clock = DateFormat.getTimeFormat(context).format(now)
        val cw = white.measureText(clock)
        canvas.drawText(clock, close.left - u * 0.3f - cw, y + u * 0.42f, white)
        soft.textSize = u * 0.26f
        val day = DateFormat.format("EEE, d MMM", now).toString()
        canvas.drawText(day, close.left - u * 0.3f - soft.measureText(day), y + u * 0.75f, soft)

        var tx = left + u * 4.3f
        val tabs = News.sections.filter { it.group == null }.map { it.name } + News.BM
        white.textSize = u * 0.32f
        for (name in tabs) {
            val tw = white.measureText(name)
            val cell = RectF(tx - u * 0.2f, y + u * 0.1f, tx + tw + u * 0.2f, y + u * 0.7f)
            if (cell.right > close.left - cw - u * 0.5f) break
            val selected = if (name == News.BM) section.group == News.BM else section.name == name
            if (selected) {
                outline.strokeWidth = u * 0.04f
                canvas.drawRoundRect(cell, cell.height() / 2, cell.height() / 2, outline)
            }
            white.typeface = if (selected) bold else Typeface.DEFAULT
            canvas.drawText(name, tx, y + u * 0.51f, white)
            targets += cell to {
                section = if (name == News.BM) lastBm else News.sections.first { it.name == name }
                null
            }
            tx += tw + u * 0.55f
        }
        y += u * 0.95f

        // Malay papers get a second row.
        if (section.group == News.BM) {
            var sx = left
            soft.textSize = u * 0.28f
            for (s in News.sections.filter { it.group == News.BM }) {
                val tw = soft.measureText(s.name)
                val cell = RectF(sx - u * 0.15f, y, sx + tw + u * 0.15f, y + u * 0.5f)
                if (s == section) canvas.drawRoundRect(cell, cell.height() / 2, cell.height() / 2, panel)
                canvas.drawText(s.name, sx, y + u * 0.35f, if (s == section) white.also { it.textSize = u * 0.28f; it.typeface = bold } else soft)
                targets += cell to {
                    section = s
                    lastBm = s
                    null
                }
                sx += tw + u * 0.5f
            }
            y += u * 0.65f
        }

        val tickerH = u * 0.65f
        val bottom = r.bottom - pad - tickerH - u * 0.2f
        val items = News.get(section)
        if (!TripTracker.isParked()) {
            message(canvas, RectF(left, y, right, bottom), u, "The news page opens when you're parked.")
        } else if (items == null) {
            message(canvas, RectF(left, y, right, bottom), u, "Loading ${section.name}…")
        } else if (items.isEmpty()) {
            message(canvas, RectF(left, y, right, bottom), u, "No news right now. Check the phone's internet.")
        } else {
            drawStories(canvas, items, RectF(left, y + u * 0.15f, right, bottom), u)
        }
        drawLatest(canvas, RectF(left, r.bottom - pad - tickerH, right, r.bottom - pad), u, items ?: emptyList())
    }

    private fun message(canvas: Canvas, r: RectF, u: Float, text: String) {
        soft.textSize = u * 0.45f
        canvas.drawText(text, r.centerX() - soft.measureText(text) / 2, r.centerY(), soft)
    }

    private fun drawStories(canvas: Canvas, items: List<NewsItem>, r: RectF, u: Float) {
        val gap = u * 0.3f
        val heroW = r.width() * 0.66f
        val heroH = r.height() * 0.6f
        val hero = RectF(r.left, r.top, r.left + heroW, r.top + heroH)
        drawHero(canvas, items[0], hero, u)

        // Side cards.
        val side = RectF(hero.right + gap, r.top, r.right, hero.bottom)
        val ch = (side.height() - gap * 2) / 3
        val (lat, lng) = TripTracker.position(context)
        val w = Weather.now
        sideCard(canvas, RectF(side.left, side.top, side.right, side.top + ch), u, "WEATHER",
            w?.let { String.format(Locale.US, "%.0f°C · %s", it.tempC, it.label) } ?: "Loading…",
            w?.let { String.format(Locale.US, "H %.0f°  L %.0f°", it.highC, it.lowC) } ?: "")
        val next = Prayer.next(lat, lng, System.currentTimeMillis())
        val mins = (next.atMillis - System.currentTimeMillis()).coerceAtLeast(0) / 60_000
        sideCard(canvas, RectF(side.left, side.top + ch + gap, side.right, side.top + ch * 2 + gap), u, "NEXT PRAYER",
            "${next.name} ${DateFormat.getTimeFormat(context).format(Date(next.atMillis))}",
            if (mins >= 60) "in ${mins / 60}h ${mins % 60}m" else "in ${mins}m")
        val track = NowPlaying.read(context)
        sideCard(canvas, RectF(side.left, side.top + (ch + gap) * 2, side.right, side.bottom), u, "NOW PLAYING",
            track?.title ?: Radio.current?.name ?: "Nothing playing",
            track?.artist ?: if (Radio.current != null) "Radio" else "")

        // Four more stories.
        val row = RectF(r.left, hero.bottom + gap, r.right, r.bottom)
        val cw = (row.width() - gap * 3) / 4
        for (i in 0 until 4) {
            val item = items.getOrNull(i + 1) ?: break
            val c = RectF(row.left + i * (cw + gap), row.top, row.left + i * (cw + gap) + cw, row.bottom)
            drawCard(canvas, item, c, u)
        }
    }

    private fun drawHero(canvas: Canvas, item: NewsItem, r: RectF, u: Float) {
        canvas.drawRoundRect(r, u * 0.25f, u * 0.25f, panel)
        News.picture(item)?.let { cover(canvas, it, r) }
        // Darken the left so the words read over the picture.
        val shade = Paint().apply {
            shader = LinearGradient(r.left, 0f, r.right, 0f, Color.argb(235, 8, 20, 50), Color.argb(40, 8, 20, 50), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(r, u * 0.25f, u * 0.25f, shade)
        val x = r.left + u * 0.4f
        var y = r.top + u * 0.4f
        y = label(canvas, item.source.uppercase(Locale.ROOT), x, y, u)
        white.typeface = bold
        white.textSize = u * 0.55f
        val textW = r.width() * 0.58f
        for (line in wrap(item.title, white, textW, 3)) {
            y += u * 0.65f
            canvas.drawText(line, x, y, white)
        }
        if (item.summary.isNotEmpty()) {
            soft.textSize = u * 0.28f
            for (line in wrap(item.summary, soft, textW, 2)) {
                y += u * 0.38f
                canvas.drawText(line, x, y, soft)
            }
        }
        val btn = RectF(x, r.bottom - u * 0.95f, x + u * 2.8f, r.bottom - u * 0.4f)
        outline.strokeWidth = u * 0.03f
        canvas.drawRoundRect(btn, btn.height() / 2, btn.height() / 2, outline)
        white.typeface = Typeface.DEFAULT
        white.textSize = u * 0.28f
        canvas.drawText("Read full story ›", btn.left + u * 0.3f, btn.centerY() + u * 0.1f, white)
        targets += r to { Action.Open(item.link) }
    }

    private fun drawCard(canvas: Canvas, item: NewsItem, r: RectF, u: Float) {
        canvas.drawRoundRect(r, u * 0.2f, u * 0.2f, panel)
        val pic = RectF(r.left, r.top, r.right, r.top + r.height() * 0.5f)
        val bmp = News.picture(item)
        if (bmp != null) {
            canvas.save()
            canvas.clipRect(pic)
            cover(canvas, bmp, pic)
            canvas.restore()
        }
        val x = r.left + u * 0.2f
        var y = label(canvas, item.source.uppercase(Locale.ROOT), x, pic.bottom + u * 0.12f, u, r.width() - u * 0.4f)
        white.typeface = bold
        white.textSize = u * 0.27f
        for (line in wrap(item.title, white, r.width() - u * 0.4f, 2)) {
            y += u * 0.34f
            canvas.drawText(line, x, y, white)
        }
        soft.textSize = u * 0.22f
        val ago = News.ago(item.timeMs)
        canvas.drawText(ago, r.right - u * 0.2f - soft.measureText(ago), r.bottom - u * 0.15f, soft)
        targets += r to { Action.Open(item.link) }
    }

    private fun sideCard(canvas: Canvas, r: RectF, u: Float, title: String, line1: String, line2: String) {
        canvas.drawRoundRect(r, u * 0.2f, u * 0.2f, panel)
        val x = r.left + u * 0.3f
        val w = r.width() - u * 0.6f
        soft.textSize = u * 0.22f
        canvas.drawText(title, x, r.top + u * 0.38f, soft)
        white.typeface = bold
        white.textSize = u * 0.34f
        canvas.drawText(wrap(line1, white, w, 1).firstOrNull() ?: "", x, r.top + u * 0.8f, white)
        soft.textSize = u * 0.26f
        canvas.drawText(wrap(line2, soft, w, 1).firstOrNull() ?: "", x, r.top + u * 1.15f, soft)
    }

    /** A small red label; returns the y under it. */
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, u: Float, maxW: Float = u * 6): Float {
        white.typeface = bold
        white.textSize = u * 0.2f
        val t = wrap(text, white, maxW - u * 0.3f, 1).firstOrNull() ?: ""
        val box = RectF(x, y, x + white.measureText(t) + u * 0.3f, y + u * 0.36f)
        canvas.drawRoundRect(box, u * 0.08f, u * 0.08f, red)
        canvas.drawText(t, x + u * 0.15f, y + u * 0.26f, white)
        return box.bottom
    }

    /** The red LATEST strip scrolling headlines; tapping it opens the one at the left edge. */
    private fun drawLatest(canvas: Canvas, r: RectF, u: Float, items: List<NewsItem>) {
        canvas.drawRoundRect(r, u * 0.15f, u * 0.15f, panel)
        val tag = RectF(r.left, r.top, r.left + u * 1.5f, r.bottom)
        canvas.drawRoundRect(tag, u * 0.15f, u * 0.15f, red)
        white.typeface = bold
        white.textSize = u * 0.26f
        canvas.drawText("LATEST", tag.centerX() - white.measureText("LATEST") / 2, tag.centerY() + u * 0.09f, white)
        if (items.isEmpty()) return
        val text = items.take(12).joinToString("   |   ") { it.title }
        white.typeface = Typeface.DEFAULT
        white.textSize = u * 0.28f
        val tw = white.measureText(text) + u * 2
        val nowMs = System.currentTimeMillis()
        if (lastFrame != 0L && TripTracker.isParked()) tickerX += (nowMs - lastFrame) * u * 0.0012f
        lastFrame = nowMs
        if (tickerX > tw) tickerX = 0f
        canvas.save()
        canvas.clipRect(tag.right + u * 0.2f, r.top, r.right, r.bottom)
        canvas.drawText(text, tag.right + u * 0.3f - tickerX, r.centerY() + u * 0.1f, white)
        canvas.drawText(text, tag.right + u * 0.3f - tickerX + tw, r.centerY() + u * 0.1f, white)
        canvas.restore()
        targets += r to {
            // Work out which headline is at the left edge of the strip.
            var x = -tickerX
            var hit = items[0]
            for (it in items.take(12)) {
                val w = white.measureText(it.title + "   |   ")
                if (x + w > 0) { hit = it; break }
                x += w
            }
            Action.Open(hit.link)
        }
    }

    private fun cover(canvas: Canvas, bmp: Bitmap, r: RectF) {
        val scale = maxOf(r.width() / bmp.width, r.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        canvas.save()
        canvas.clipRect(r)
        canvas.drawBitmap(bmp, null, RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), img)
        canvas.restore()
    }

    companion object {
        /** Splits [text] into at most [max] lines that fit [width], ending with … if cut short. */
        fun wrap(text: String, p: Paint, width: Float, max: Int): List<String> {
            val words = text.split(' ').filter { it.isNotEmpty() }
            val lines = mutableListOf<String>()
            var cur = ""
            var i = 0
            while (i < words.size && lines.size < max) {
                val next = if (cur.isEmpty()) words[i] else "$cur ${words[i]}"
                if (p.measureText(next) <= width || cur.isEmpty()) {
                    cur = next
                    i++
                } else {
                    lines += cur
                    cur = ""
                }
            }
            if (cur.isNotEmpty() && lines.size < max) lines += cur
            if (i < words.size && lines.isNotEmpty()) {
                var last = lines.last()
                while (last.isNotEmpty() && p.measureText("$last…") > width) last = last.dropLast(1)
                lines[lines.size - 1] = "$last…"
            }
            return lines.map { line ->
                // A single word longer than the width is cut.
                if (p.measureText(line) <= width) line else {
                    var l = line
                    while (l.isNotEmpty() && p.measureText("$l…") > width) l = l.dropLast(1)
                    "$l…"
                }
            }
        }
    }
}
