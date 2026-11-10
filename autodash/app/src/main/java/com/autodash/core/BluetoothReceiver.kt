package com.autodash.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

/**
 * When the phone connects to the car's Bluetooth: play the welcome sound, resume radio if wanted,
 * and post a notification that opens the dashboard with one tap (Android doesn't let apps open
 * screens by themselves from the background).
 */
class BluetoothReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val prefs = Prefs(context)
        if (!prefs.autoStartOnBluetooth) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val wanted = prefs.carBluetoothName
        if (wanted.isNotEmpty()) {
            val name = try { device?.name } catch (_: SecurityException) { null } ?: return
            if (!name.contains(wanted, ignoreCase = true)) return
        }

        if (prefs.resumeRadioOnConnect) prefs.lastStation?.let { Radio.play(context, it) }
        offerDashboard(context)
        if (prefs.welcomeSound) {
            val pending = goAsync()
            Chime.play(Look.tone(context)) { pending.finish() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun offerDashboard(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Car connected", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.autodash.phone.DashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.autodash.R.drawable.ic_dash)
            .setContentTitle("Autodash")
            .setContentText("Car connected. Tap to open the dashboard.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(7, n)
    }

    companion object {
        private const val CHANNEL = "car"
    }
}
