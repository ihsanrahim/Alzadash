package com.alzadash.mirror

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import com.alzadash.R

/** Keeps screen capture running while the phone is mirrored to the car (Android requires a notification). */
class MirrorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            Mirror.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        channel(this)
        val stop = PendingIntent.getService(this, 1, Intent(this, MirrorService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_video)
            .setContentTitle("Mirroring to the car")
            .setContentText("Your phone's screen is showing on the car screen.")
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2, n)
        getSystemService(NotificationManager::class.java)?.cancel(3)
        if (code != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val p = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
        } catch (_: Exception) {
            null
        }
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        Rotation.forceLandscape(this)
        Mirror.started(p)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Rotation.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mirror"
        private const val STOP = "stop"

        private fun channel(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Phone mirroring", NotificationManager.IMPORTANCE_HIGH))
        }

        /** A notification that opens the casting question, for when Android won't pop it up by itself. */
        fun notifyTapToStart(context: Context) {
            channel(context)
            val open = PendingIntent.getActivity(context, 2,
                Intent(context, MirrorStartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE)
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_video)
                .setContentTitle("Mirror your phone to the car")
                .setContentText("Tap, then choose Start (entire screen).")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                context.getSystemService(NotificationManager::class.java)?.notify(3, n)
            } catch (_: Exception) {
            }
        }
    }
}
