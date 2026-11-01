package com.alzadash.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One line of lyrics. [atMs] is when it is sung, or -1 when the lyrics have no timing. */
data class LyricLine(val atMs: Long, val text: String)

/**
 * Song lyrics from LRCLIB (lrclib.net), a free open lyrics database. Synced lyrics are
 * preferred so the line being sung can be shown; plain lyrics are used otherwise.
 */
object Lyrics {
    private val cache = ConcurrentHashMap<String, List<LyricLine>>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    // Two at a time, so a new song isn't stuck behind a slow lookup for the last one.
    private val pool = Executors.newFixedThreadPool(2)

    /** Where found lyrics are kept between drives, so a song heard before shows its lyrics at once. */
    @Volatile var diskDir: java.io.File? = null

    private fun diskFile(k: String) = diskDir?.let { java.io.File(it, "lyr_" + Integer.toHexString(k.hashCode()) + ".lrc") }

    private fun fromDisk(k: String): List<LyricLine>? = try {
        diskFile(k)?.takeIf { it.exists() }?.readText()?.let { txt ->
            if (txt.isBlank()) emptyList() else if (txt.startsWith("[")) parseLrc(txt) else txt.lines().map { LyricLine(-1, it) }
        }
    } catch (_: Exception) { null }

    private fun toDisk(k: String, lines: List<LyricLine>) {
        try {
            val f = diskFile(k) ?: return
            f.parentFile?.mkdirs()
            f.writeText(if (lines.isNotEmpty() && lines[0].atMs >= 0) lines.joinToString("\n") {
                val m = it.atMs / 60_000
                val sec = (it.atMs % 60_000) / 1000.0
                String.format(java.util.Locale.US, "[%02d:%05.2f]%s", m, sec, it.text)
            } else lines.joinToString("\n") { it.text })
        } catch (_: Exception) {
        }
    }

    /** Called when any music app starts a new song, so its lyrics are ready before the card is looked at. */
    fun prefetch(t: Track) { get(t) }

    /** Lets the car screen redraw as soon as lyrics arrive. */
    @Volatile var onFound: () -> Unit = {}

    private fun key(t: Track) = "${t.title}|${t.artist}".lowercase()

    /** Lyrics for [t] if already fetched; otherwise starts fetching and returns null. Empty means none found. */
    fun get(t: Track): List<LyricLine>? {
        val k = key(t)
        cache[k]?.let { return it }
        if (pending.add(k)) {
            pool.execute {
                val saved = fromDisk(k)
                // Not-found results aren't kept on disk, so they get another try next drive.
                val lines = saved?.takeIf { it.isNotEmpty() } ?: try { find(t) } catch (_: Exception) { emptyList() }
                cache[k] = lines
                if (lines.isNotEmpty() && saved.isNullOrEmpty()) toDisk(k, lines)
                pending.remove(k)
                onFound()
            }
        }
        return null
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line or for unsynced lyrics. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || lines[0].atMs < 0) return -1
        return lines.indexOfLast { it.atMs <= positionMs + 300 }
    }

    private fun find(t: Track): List<LyricLine> {
        // Fastest: an exact match on title, artist and length (Spotify and YouTube Music give clean names).
        exact(t)?.let { hit ->
            hit.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { return parseLrc(it) }
            hit.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { plain ->
                return plain.lines().map { LyricLine(-1, it.trim()) }
            }
        }
        for ((title, artist) in guesses(t)) {
            val hit = search(title, artist) ?: continue
            hit.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { return parseLrc(it) }
            hit.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }?.let { plain ->
                return plain.lines().map { LyricLine(-1, it.trim()) }
            }
        }
        return emptyList()
    }

    /**
     * Title and artist pairs to try. YouTube titles often look like "Artist - Song (Official Video)"
     * with the channel as the artist, so the title is cleaned and split as well.
     */
    private fun guesses(t: Track): List<Pair<String, String>> {
        val title = clean(t.title)
        val artist = t.artist.removeSuffix(" - Topic").replace(Regex("(?i)vevo$"), "").trim()
        val out = mutableListOf(title to artist)
        val dash = Regex("\\s[-–—|]\\s").find(title)
        if (dash != null) {
            val a = title.substring(0, dash.range.first).trim()
            val b = title.substring(dash.range.last + 1).trim()
            out += b to a
            out += a to b
        }
        out += title to ""
        return out.distinct()
    }

    private fun clean(s: String): String = s
        .replace(Regex("(?i)[(\\[][^)\\]]*(official|video|lyric|audio|mv|m/v|visuali[sz]er|live|hd|4k)[^)\\]]*[)\\]]"), "")
        .replace(Regex("(?i)\\s(ft\\.?|feat\\.?)\\s.*$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun exact(t: Track): JSONObject? {
        if (t.title.isBlank() || t.artist.isBlank()) return null
        var url = "https://lrclib.net/api/get?track_name=" + enc(t.title) + "&artist_name=" + enc(t.artist.removeSuffix(" - Topic"))
        if (t.durationMs > 0) url += "&duration=" + (t.durationMs / 1000)
        return try { JSONObject(fetch(url)) } catch (_: Exception) { null }
    }

    private fun search(title: String, artist: String): JSONObject? {
        if (title.isBlank()) return null
        var url = "https://lrclib.net/api/search?track_name=" + enc(title)
        if (artist.isNotBlank()) url += "&artist_name=" + enc(artist)
        val arr = JSONArray(fetch(url))
        var plain: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("syncedLyrics").let { it.isNotBlank() && it != "null" }) return o
            if (plain == null && o.optString("plainLyrics").let { it.isNotBlank() && it != "null" }) plain = o
        }
        return plain
    }

    /** "[01:23.45] words" lines into timed lines. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val tag = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)]")
        val out = mutableListOf<LyricLine>()
        lrc.lines().forEach { line ->
            val tags = tag.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { m ->
                val ms = m.groupValues[1].toLong() * 60_000 + (m.groupValues[2].toDouble() * 1000).toLong()
                out += LyricLine(ms, text)
            }
        }
        return out.sortedBy { it.atMs }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "AlzaDash (https://github.com/ihsanrahim/Alzadash)")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}
