package com.alzadash.core

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Needed only so the app may read which song other apps are playing. */
class NowPlayingListener : NotificationListenerService()

/**
 * A song another app is playing. [positionMs] is where it was when read, worked out from the
 * app's last reported position, so it can drive synced lyrics.
 */
data class Track(
    val title: String,
    val artist: String,
    val pkg: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val playing: Boolean = true,
)

object NowPlaying {
    /** The music app to show in the player widget: the one playing, else the last one paused. */
    private fun pick(context: Context): android.media.session.MediaController? {
        if (!hasAccess(context)) return null
        return try {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            val list = msm.getActiveSessions(ComponentName(context, NowPlayingListener::class.java))
                .filter { it.packageName != context.packageName && it.metadata != null }
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    /** Like [read], but also returns a paused song, for the player widget. */
    fun readAny(context: Context): Track? {
        val c = pick(context) ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        val pos = if (st == null || st.position < 0) 0L else if (!playing) st.position else {
            st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return Track(title, artist, c.packageName, md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos, playing)
    }

    private var artKey: String? = null
    private var artCache: android.graphics.Bitmap? = null

    /** Album art (or the video thumbnail) of the song in the player widget. */
    fun art(context: Context): android.graphics.Bitmap? {
        val c = pick(context) ?: return null
        val md = c.metadata ?: return null
        val key = c.packageName + md.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (key == artKey) return artCache
        artKey = key
        artCache = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return artCache
    }

    /** Previous (-1), play/pause (0) or next (+1) in that music app. */
    fun control(context: Context, what: Int) {
        val c = pick(context) ?: return
        val t = c.transportControls
        when (what) {
            -1 -> t.skipToPrevious()
            1 -> t.skipToNext()
            else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
    }

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(context.packageName)
    }

    /** The track of the first media app that is playing, or null. */
    fun read(context: Context): Track? {
        if (!hasAccess(context)) return null
        return try {
            val msm = context.getSystemService(MediaSessionManager::class.java)
            val controllers = msm.getActiveSessions(ComponentName(context, NowPlayingListener::class.java))
            // Our own radio is shown separately, so skip it here.
            val playing = controllers.firstOrNull {
                it.packageName != context.packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: return null
            val md = playing.metadata ?: return null
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            val st = playing.playbackState
            val pos = if (st == null || st.position < 0) 0L else {
                st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            }
            Track(title, artist, playing.packageName, md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos)
        } catch (e: SecurityException) {
            null
        }
    }
}
