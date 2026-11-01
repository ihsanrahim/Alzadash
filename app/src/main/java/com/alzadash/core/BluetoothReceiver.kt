package com.alzadash.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** When the phone connects to the car's Bluetooth: play the welcome sound and, if wanted, resume radio. */
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
        if (prefs.welcomeSound) {
            val pending = goAsync()
            Chime.play(Look.tone(context)) { pending.finish() }
        }
    }
}
