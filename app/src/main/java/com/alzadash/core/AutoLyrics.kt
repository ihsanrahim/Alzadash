/*
 * Lyrics lookup helpers ported from Auto Lyrics (https://github.com/GitUpGitUp/auto-lyrics,
 * itself from https://github.com/efrayim-dev/auto-lyrics), released under the MIT License:
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of this
 *   software and associated documentation files (the "Software"), to deal in the Software
 *   without restriction, including without limitation the rights to use, copy, modify, merge,
 *   publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons
 *   to whom the Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all copies or
 *   substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 *   INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 *   PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 *   FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 *   OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 *   DEALINGS IN THE SOFTWARE.
 *
 * From lyrics/MetadataCleaner.kt, lyrics/SyncLrcClient.kt and the duration check in
 * lyrics/LrcLibClient.kt, rewritten without OkHttp and Gson.
 */

package com.alzadash.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Auto Lyrics' song-name cleaning, its second lyrics source (SyncLRC) and its length check. */
object AutoLyrics {
    private val QUALITY = Regex("""\b(lossless|hi-?res|hires|flac|hi-?fi|hifi|dolby\s*atmos|spatial\s*audio|atmos|mqa)\b""", RegexOption.IGNORE_CASE)
    private val BITRATE = Regex("""\b\d{2,3}\s*kbps\b""", RegexOption.IGNORE_CASE)
    private val BIT_DEPTH = Regex("""\b\d{2}-?bit\b""", RegexOption.IGNORE_CASE)
    private val SAMPLE_RATE = Regex("""\b\d{2,3}(\.\d)?\s*khz\b""", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE = Regex("""\s{2,}""")
    private val SEPARATOR = Regex("""\s*[•·|]\s*""")

    /** Music apps put quality tags ("Lossless", "24-bit") and extra names ("Artist • Album") in the artist field. */
    fun cleanArtist(raw: String): String {
        val primary = raw.split(SEPARATOR).first().trim()
        val noDash = primary.split(Regex("""\s+[-–—]\s+""")).first().trim()
        return removeQualityTags(noDash).ifBlank { primary }
    }

    fun cleanTitle(raw: String): String {
        val s = raw.trim().replace(
            Regex("""\s*[(\[].*?\b(official|video|lyric|audio|visualizer|live|acoustic)\b.*?[)\]]""", RegexOption.IGNORE_CASE), "",
        )
        return removeQualityTags(s).ifBlank { raw.trim() }
    }

    private fun removeQualityTags(s: String): String = s
        .replace(QUALITY, "").replace(BITRATE, "").replace(BIT_DEPTH, "").replace(SAMPLE_RATE, "")
        .replace(MULTI_SPACE, " ").trim()
        .trimEnd('-', '–', '—', ',', ';', ':', '•', '·', '(', '[')
        .trimStart('-', '–', '—', ',', ';', ':', '•', '·', ')', ']')
        .trim()

    /** A search hit must be within 10 seconds of the song's length, so a remix or live take isn't used. */
    fun lengthMatches(hit: JSONObject, durationMs: Long): Boolean {
        val d = hit.optDouble("duration", -1.0)
        if (durationMs <= 0 || d <= 0) return true
        return kotlin.math.abs(d - durationMs / 1000.0) <= 10
    }

    /**
     * SyncLRC (synclrc.tharuk.pro), tried when LRCLIB has nothing: synced lyrics first, then plain.
     * Returns LRC text ("[mm:ss.xx] words" lines) or plain text, or null.
     */
    fun syncLrc(title: String, artist: String): String? {
        if (title.isBlank()) return null
        for (type in listOf("synced", "plain")) {
            try {
                val url = "https://synclrc.tharuk.pro/lyrics?track=" + enc(title) + "&artist=" + enc(artist) + "&type=" + type
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("User-Agent", "Autodash (https://github.com/ihsanrahim/Alzadash)")
                if (c.responseCode !in 200..299) { c.disconnect(); continue }
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val lyrics = JSONObject(body).optString("lyrics")
                if (lyrics.isNotBlank() && lyrics != "null") return lyrics
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
