package com.alzadash.core

import android.content.Context

/** Parts of the dashboard and app behaviour that can be switched on or off, from the car or the phone. */
enum class Feature(val label: String, val default: Boolean = true) {
    HIJRI("Hijri date"),
    SPEED("Speed and trip"),
    PRAYER("Prayer times"),
    WEATHER("Weather"),
    RADIO_ROW("Radio station row"),
    NOW_PLAYING("Now playing bar"),
    LYRICS("Song lyrics"),
    NEWS("News headlines"),
    DOCK("Shortcut bar"),
    BACKGROUND("Background picture"),
    WELCOME("Welcome sound"),
    AUTO_START("Auto start on car Bluetooth"),
    RESUME_RADIO("Resume radio on connect"),
}

object Features {
    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    fun on(context: Context, f: Feature): Boolean = when (f) {
        // These three existed before the settings list and keep their old storage.
        Feature.WELCOME -> Prefs(context).welcomeSound
        Feature.AUTO_START -> Prefs(context).autoStartOnBluetooth
        Feature.RESUME_RADIO -> Prefs(context).resumeRadioOnConnect
        else -> sp(context).getBoolean("feature_${f.name}", f.default)
    }

    fun set(context: Context, f: Feature, value: Boolean) {
        when (f) {
            Feature.WELCOME -> Prefs(context).welcomeSound = value
            Feature.AUTO_START -> Prefs(context).autoStartOnBluetooth = value
            Feature.RESUME_RADIO -> Prefs(context).resumeRadioOnConnect = value
            else -> sp(context).edit().putBoolean("feature_${f.name}", value).apply()
        }
    }
}
