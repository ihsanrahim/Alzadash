package com.autodash

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TimeDisplayWidget() {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    LaunchedEffect(Unit) { while (true) { currentTime = Calendar.getInstance().time; delay(1000) } }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val secFmt = remember { SimpleDateFormat(":ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(timeFmt.format(currentTime), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(secFmt.format(currentTime), fontSize = 18.sp, color = Color(0xFF64B5F6), modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(dateFmt.format(currentTime).uppercase(), fontSize = 12.sp, color = Color(0xFF90A4AE))
    }
}

@Composable
fun GpsSpeedometerWidget() {
    val context = LocalContext.current
    var useKmh by remember { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Speed.start(context) }
    val speedMps = Speed.speedMps
    val spd = if (useKmh) (speedMps * 3.6f).roundToInt() else (speedMps * 2.23694f).roundToInt()
    Column(modifier = Modifier.fillMaxSize().clickable { useKmh = !useKmh }.padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!Speed.hasPermission) {
            Button(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Enable GPS", fontSize = 11.sp) }
        } else {
            Text("$spd", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF00E676))
            Text(if (useKmh) "KM/H" else "MPH", fontSize = 11.sp, color = Color(0xFF90A4AE), fontWeight = FontWeight.Bold)
        }
    }
}

private fun mediaSourceName(pkg: String): String = when {
    pkg.contains("spotify") -> "Spotify"
    pkg.contains("youtube.music") -> "YT Music"
    pkg.contains("youtube") -> "YouTube"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

@Composable
fun NowPlayingWidget() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("No Active Media") }
    var artist by remember { mutableStateOf("Play Spotify or YT Music") }
    var source by remember { mutableStateOf("Media Bridge") }
    var isPlaying by remember { mutableStateOf(false) }
    var art by remember { mutableStateOf<Bitmap?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var hasPerm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val comp = ComponentName(context, MediaNotificationListener::class.java)
        val mgr = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        while (true) {
            val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            hasPerm = listeners?.contains(comp.flattenToString()) == true
            if (hasPerm) {
                try {
                    val targets = mgr.getActiveSessions(comp)
                    val t = targets.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                        ?: targets.firstOrNull { it.packageName.contains("spotify") || it.packageName.contains("youtube") }
                        ?: targets.firstOrNull()
                    controller = t
                    if (t != null) {
                        val m = t.metadata
                        title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Track"
                        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        isPlaying = t.playbackState?.state == PlaybackState.STATE_PLAYING
                        source = mediaSourceName(t.packageName)
                    }
                } catch (_: SecurityException) { hasPerm = false }
            }
            delay(1000)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(source.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1DB954))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Spotify", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { launchAppSideBySide(context, "com.spotify.music") })
                Text("YT Music", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { launchAppSideBySide(context, "com.google.android.apps.youtube.music") })
            }
        }
        if (!hasPerm) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Grant Media Bridge Access", fontSize = 11.sp) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF232B3E)), contentAlignment = Alignment.Center) {
                    val bmp = art
                    if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, fontSize = 12.sp, color = Color(0xFFB0BEC5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { controller?.transportControls?.skipToPrevious() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White) }
                IconButton(onClick = { if (isPlaying) controller?.transportControls?.pause() else controller?.transportControls?.play() }, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2979FF))) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                }
                IconButton(onClick = { controller?.transportControls?.skipToNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White) }
            }
        }
    }
}

data class PrayerSchedule(val fajr: String = "05:52", val dhuhr: String = "13:12", val asr: String = "16:33", val maghrib: String = "19:11", val isha: String = "20:24")

/** Today's times from aladhan.com using JAKIM's method; Kuala Lumpur until the phone knows where it is. */
private fun fetchPrayerTimes(context: Context): PrayerSchedule? {
    var lat = 3.1073
    var lng = 101.6067
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val last = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (last != null) { lat = last.latitude; lng = last.longitude }
        } catch (_: SecurityException) {}
    }
    return try {
        val conn = URL("https://api.aladhan.com/v1/timings?latitude=$lat&longitude=$lng&method=17").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val t = json.getJSONObject("data").getJSONObject("timings")
        PrayerSchedule(t.getString("Fajr").take(5), t.getString("Dhuhr").take(5), t.getString("Asr").take(5), t.getString("Maghrib").take(5), t.getString("Isha").take(5))
    } catch (_: Exception) { null }
}

@Composable
fun PrayerTimesWidget() {
    val context = LocalContext.current
    var sched by remember { mutableStateOf(PrayerSchedule()) }
    var curPrv by remember { mutableStateOf("Subuh") }
    var nxtPrv by remember { mutableStateOf("Zohor") }
    var cdown by remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) {
        // Refresh every few hours so the times roll over to the new day.
        while (true) {
            val fetched = withContext(Dispatchers.IO) { fetchPrayerTimes(context.applicationContext) }
            if (fetched != null) sched = fetched
            delay(if (fetched != null) 3 * 3600_000L else 60_000L)
        }
    }
    LaunchedEffect(sched) {
        val prayers = listOf("Subuh" to sched.fajr, "Zohor" to sched.dhuhr, "Asar" to sched.asr, "Maghrib" to sched.maghrib, "Isyak" to sched.isha)
        while (true) {
            val now = Calendar.getInstance()
            val nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val pSec = prayers.map { (n, ts) -> val p = ts.split(":").map { it.trim().toIntOrNull() ?: 0 }; n to (p[0] * 3600 + p.getOrElse(1) { 0 } * 60) }
            val nIdx = pSec.indexOfFirst { it.second > nowSec }
            val cIdx = if (nIdx <= 0) pSec.lastIndex else nIdx - 1
            curPrv = pSec[cIdx].first
            val (nName, nSec) = if (nIdx != -1) pSec[nIdx] else pSec[0].first to (pSec[0].second + 86400)
            nxtPrv = nName
            val diff = (nSec - nowSec).coerceAtLeast(0)
            cdown = String.format(Locale.US, "-%02d:%02d:%02d", diff / 3600, (diff % 3600) / 60, diff % 60)
            delay(1000L)
        }
    }
    val list = listOf("Subuh" to sched.fajr, "Zohor" to sched.dhuhr, "Asar" to sched.asr, "Maghrib" to sched.maghrib, "Isyak" to sched.isha)
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("NOW: ${curPrv.uppercase()}", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text("NEXT: $nxtPrv ($cdown)", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            list.forEach { (name, time) ->
                val active = name == curPrv
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (active) Color(0xFF004D40) else Color(0xFF222A3A)).padding(vertical = 6.dp)) {
                    Text(name, color = if (active) Color(0xFF00E676) else Color(0xFF90A4AE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(time, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class CalendarEventItem(val title: String, val timeText: String)

@Composable
fun GoogleCalendarWidget(onSplit: () -> Unit) {
    val context = LocalContext.current
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    var evs by remember { mutableStateOf<List<CalendarEventItem>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(hasPerm) {
        if (!hasPerm) return@LaunchedEffect
        while (true) {
            evs = withContext(Dispatchers.IO) {
                val list = mutableListOf<CalendarEventItem>()
                val now = System.currentTimeMillis()
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(uri, now)
                ContentUris.appendId(uri, now + 7 * 86400000L)
                val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
                try {
                    context.contentResolver.query(uri.build(), arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                        while (c.moveToNext() && list.size < 5) {
                            list.add(CalendarEventItem(c.getString(0) ?: "Event", fmt.format(Date(c.getLong(1)))))
                        }
                    }
                } catch (_: Exception) {}
                list
            }
            delay(15 * 60_000L)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CALENDAR", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("SPLIT", color = Color(0xFF42A5F5), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSplit(); launchAppSideBySide(context, "com.google.android.calendar") })
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (!hasPerm) { Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Sync Calendar", fontSize = 11.sp) } }
        else if (evs.isEmpty()) { Text("No upcoming events", color = Color.Gray, fontSize = 11.sp) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(evs) { ev ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF222A3A)).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(ev.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(ev.timeText, color = Color(0xFF64B5F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class CarApp(val name: String, val pkg: String)
val PRESETS = listOf(
    CarApp("Waze", "com.waze"), CarApp("Maps", "com.google.android.apps.maps"),
    CarApp("Spotify", "com.spotify.music"), CarApp("YT Music", "com.google.android.apps.youtube.music"),
    CarApp("Podcasts", "au.com.shiftyjelly.pocketcasts"), CarApp("YouTube", "com.google.android.youtube"), CarApp("Brave", "com.brave.browser")
)

fun launchAppSideBySide(context: Context, pkg: String) {
    val i = context.packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) {
        i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try { context.startActivity(i) } catch (_: Exception) { Toast.makeText(context, "Couldn't open $pkg", Toast.LENGTH_SHORT).show() }
    } else { Toast.makeText(context, "$pkg not installed", Toast.LENGTH_SHORT).show() }
}

@Composable
fun SideBySideAppDockWidget(onSplit: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf(false) }
    val apps = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { CarApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }.sortedBy { it.name.lowercase() }
    }
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SIDE-BY-SIDE APPS", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(if (all) "Presets" else "All Apps", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { all = !all })
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(85.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(if (all) apps else PRESETS) { app ->
                Box(modifier = Modifier.height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF222A3A)).clickable { onSplit(); launchAppSideBySide(context, app.pkg) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(app.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
