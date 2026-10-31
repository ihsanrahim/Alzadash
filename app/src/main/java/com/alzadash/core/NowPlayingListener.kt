package com.alzadash.core

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Needed only so the app may read which song other apps are playing. */
class NowPlayingListener : NotificationListenerService()

data class Track(val title: String, val artist: String)

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
            val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: return null
            val md = playing.metadata ?: return null
            val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
            val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            Track(title, artist)
        } catch (e: SecurityException) {
            null
        }
    }
}
