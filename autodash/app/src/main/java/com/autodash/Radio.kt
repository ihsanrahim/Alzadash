package com.autodash

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** [search] is the name looked up on radio-browser.info if [url] stops working; [my] limits that lookup to Malaysia. */
data class RadioStation(val name: String, val genre: String, val url: String, val search: String = name, val my: Boolean = true)

val TOP_20_RADIOS = listOf(
    RadioStation("Hitz FM", "Top 40", "https://stream.rcs.revma.com/48864y22k8uvv", "Hitz"),
    RadioStation("Era FM", "Malay", "https://stream.rcs.revma.com/60806y22k8uvv", "Era"),
    RadioStation("Hot FM", "Top Malay", "https://stream.rcs.revma.com/79f72z22k8uvv", "Hot FM"),
    RadioStation("Fly FM", "Pop", "https://stream.rcs.revma.com/20642z22k8uvv", "Fly FM"),
    RadioStation("Mix FM", "90s", "https://stream.rcs.revma.com/17488y22k8uvv", "Mix"),
    RadioStation("Lite FM", "Relax", "https://stream.rcs.revma.com/22823y22k8uvv", "Lite"),
    RadioStation("Sinar", "Retro", "https://stream.rcs.revma.com/52062y22k8uvv", "Sinar"),
    RadioStation("IKIM", "Islamic", "https://stream.rcs.revma.com/99084y22k8uvv", "IKIM"),
    RadioStation("BFM 89.9", "Biz", "https://stream.rcs.revma.com/29584y22k8uvv", "BFM"),
    RadioStation("Eight", "Mandarin", "https://stream.rcs.revma.com/88202z22k8uvv", "Eight"),
    RadioStation("MY FM", "C-Pop", "https://stream.rcs.revma.com/47664y22k8uvv", "MY FM"),
    RadioStation("Melody", "Classic", "https://stream.rcs.revma.com/39284y22k8uvv", "Melody"),
    RadioStation("Raaga", "Tamil", "https://stream.rcs.revma.com/11824y22k8uvv", "Raaga"),
    RadioStation("Zayan", "Muslim", "https://stream.rcs.revma.com/77224y22k8uvv", "Zayan"),
    RadioStation("Gegar", "East", "https://stream.rcs.revma.com/66114y22k8uvv", "Gegar"),
    RadioStation("Lofi", "Chill", "https://play.streamafrica.net/lofiradio", "lofi", my = false),
    RadioStation("Synthwave", "Drive", "https://stream.nightride.fm/nightride.m4a", "Nightride FM", my = false),
    RadioStation("BBC", "News", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "BBC World Service", my = false),
    RadioStation("Jazz24", "Jazz", "https://live.wostreaming.net/direct/ppm-jazz24aac-ibc1", "Jazz24", my = false),
    RadioStation("Classic", "Orchestra", "https://media-ice.musicradio.com/ClassicFMMP3", "Classic FM", my = false)
)

/** Finds a working stream on radio-browser.info when a built-in link has gone dead, and remembers it. */
object RadioLookup {
    private const val PREFS = "radio_streams"
    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    fun cached(context: Context, st: RadioStation): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(st.name, null)

    fun forget(context: Context, st: RadioStation) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(st.name).apply()
    }

    /** Blocking; call off the main thread. Skips any link in [exclude]. */
    fun find(context: Context, st: RadioStation, exclude: Set<String>): String? {
        val q = URLEncoder.encode(st.search, "UTF-8")
        val country = if (st.my) "&countrycode=MY" else ""
        for (base in mirrors) {
            try {
                val conn = URL("$base/json/stations/search?name=$q$country&hidebroken=true&order=clickcount&reverse=true&limit=8").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "Autodash/2.0")
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                    if (stream.startsWith("http") && stream !in exclude) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(st.name, stream).apply()
                        return stream
                    }
                }
                return null
            } catch (_: Exception) {
                // try the next mirror
            }
        }
        return null
    }
}

@Composable
fun Top20RadioRowWidget() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeSt by remember { mutableStateOf<RadioStation?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val mp = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { try { mp.release() } catch (_: Exception) {} } }

    fun tryPlay(st: RadioStation, url: String, onFail: () -> Unit) {
        try {
            mp.reset()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(url)
            mp.setOnPreparedListener { if (activeSt == st) { it.start(); status = null } }
            mp.setOnErrorListener { _, _, _ -> if (activeSt == st) onFail(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
            onFail()
        }
    }

    fun play(st: RadioStation) {
        activeSt = st
        status = "Tuning ${st.name}…"
        val cached = RadioLookup.cached(context, st)
        val first = cached ?: st.url
        tryPlay(st, first) {
            if (cached != null) RadioLookup.forget(context, st)
            scope.launch {
                val found = withContext(Dispatchers.IO) { RadioLookup.find(context.applicationContext, st, setOf(first)) }
                if (activeSt != st) return@launch
                if (found != null) tryPlay(st, found) { status = "${st.name} isn't streaming right now" }
                else status = "${st.name} isn't streaming right now"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        val current = activeSt
        val header = when {
            status != null -> status!!
            current != null -> "PLAYING: ${current.name} (TAP TO STOP)"
            else -> "TOP 20 LIVE RADIO"
        }
        Text(header, color = if (current != null) Color(0xFF00E676) else Color(0xFF90A4AE), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TOP_20_RADIOS.size) { idx ->
                val st = TOP_20_RADIOS[idx]
                val sel = activeSt == st
                Box(modifier = Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) Color(0xFF1B5E20) else Color(0xFF222A3A)).clickable {
                    if (sel) { try { mp.reset() } catch (_: Exception) {}; activeSt = null; status = null }
                    else play(st)
                }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("${idx + 1}. ${st.name}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
