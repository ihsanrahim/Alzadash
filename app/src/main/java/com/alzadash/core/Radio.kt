package com.alzadash.core

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlin.concurrent.thread

/** Instant Radio: one call to start or stop a saved stream. */
object Radio {
    private var future: ListenableFuture<MediaController>? = null

    @Volatile
    var current: Station? = null
        private set


    private fun withController(context: Context, block: (MediaController) -> Unit) {
        val app = context.applicationContext
        val f = future ?: MediaController.Builder(
            app, SessionToken(app, ComponentName(app, PlaybackService::class.java))
        ).buildAsync().also { future = it }
        f.addListener({
            try {
                block(f.get())
            } catch (e: Exception) {
                future = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    /** Starts a station, looking up its stream link first if needed. [onError] runs on the main thread. */
    fun play(context: Context, station: Station, onError: (String) -> Unit = {}) {
        current = station
        Prefs(context).lastStationName = station.name
        val app = context.applicationContext
        thread(name = "radio-resolve") {
            val url = RadioResolver.resolve(app, station)
            ContextCompat.getMainExecutor(app).execute {
                if (url == null) {
                    current = null
                    onError("Couldn't find a stream for ${station.name}")
                } else if (current == station) {
                    start(app, station, url)
                }
            }
        }
    }

    /** Plays the station [delta] places after the current one (wrapping round). Used by ◀ ▶ and the steering wheel. */
    fun step(context: Context, delta: Int, onError: (String) -> Unit = {}) {
        val stations = Prefs(context).stations
        if (stations.isEmpty()) return
        val cur = current ?: Prefs(context).lastStation
        val idx = stations.indexOfFirst { it.name == cur?.name }
        val next = if (idx < 0) {
            if (delta > 0) stations.first() else stations.last()
        } else {
            stations[(idx + delta).mod(stations.size)]
        }
        play(context, next, onError)
    }

    /** Records a station started from Android Auto's own radio screen. */
    fun markCurrent(context: Context, station: Station) {
        current = station
        Prefs(context).lastStationName = station.name
    }

    private fun start(context: Context, station: Station, url: String) {
        withController(context) { c ->
            val item = MediaItem.Builder()
                .setMediaId(station.name)
                .setUri(url)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build())
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(station.name).setStation(station.name).build()
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun stop(context: Context) {
        current = null
        withController(context) { it.stop() }
    }

    val isPlaying: Boolean get() = current != null
}
