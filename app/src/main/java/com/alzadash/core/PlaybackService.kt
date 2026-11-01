package com.alzadash.core

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlin.concurrent.thread

/**
 * Plays internet radio in the background. It is also a media library, so Android Auto lists
 * "AlzaDash Radio" as a media app: it can sit in the small half next to Waze, and the
 * steering wheel's next/previous buttons change station.
 */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(attrs, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Stations send the song or show on air in the stream (ICY); the dashboard shows it.
        exo.addListener(object : Player.Listener {
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                for (i in 0 until metadata.length()) {
                    val e = metadata.get(i)
                    if (e is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        Radio.streamTitle = e.title?.trim()?.trim('-', ' ')?.takeIf { it.isNotEmpty() }
                    }
                }
            }
        })
        // Next/previous always mean "change station", whatever is in the player's queue.
        val player = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    )
                    .build()
            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
            override fun hasNextMediaItem() = true
            override fun hasPreviousMediaItem() = true
            override fun seekToNext() = Radio.step(this@PlaybackService, 1)
            override fun seekToNextMediaItem() = Radio.step(this@PlaybackService, 1)
            override fun seekToPrevious() = Radio.step(this@PlaybackService, -1)
            override fun seekToPreviousMediaItem() = Radio.step(this@PlaybackService, -1)
        }
        session = MediaLibrarySession.Builder(this, player, Library()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun find(id: String): Station? = Prefs(this).stations.firstOrNull { it.name == id }

    private inner class Library : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = Prefs(this@PlaybackService).stations.map { stationItem(it) }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val st = find(mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(stationItem(st), null))
        }

        /** Stream links don't travel with requests from other apps, so look them up here. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val result = SettableFuture.create<MutableList<MediaItem>>()
            thread(name = "radio-add") {
                val out = mediaItems.map { item ->
                    val st = find(item.mediaId)
                    if (st != null) Radio.markCurrent(this@PlaybackService, st)
                    val uri = item.requestMetadata.mediaUri
                        ?: st?.let { RadioResolver.resolve(this@PlaybackService, it) }?.let { Uri.parse(it) }
                    item.buildUpon()
                        .setUri(uri)
                        .setMediaMetadata(st?.let { stationItem(it).mediaMetadata } ?: item.mediaMetadata)
                        .build()
                }
                result.set(out.toMutableList())
            }
            return result
        }
    }

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId("root")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Stations")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .build()
        )
        .build()

    private fun stationItem(st: Station): MediaItem = MediaItem.Builder()
        .setMediaId(st.name)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(st.name)
                .setStation(st.name)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        )
        .build()
}
