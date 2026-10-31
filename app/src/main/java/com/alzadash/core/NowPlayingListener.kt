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
)

object NowPlaying {
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
