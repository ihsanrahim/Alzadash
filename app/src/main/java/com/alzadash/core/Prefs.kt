package com.alzadash.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Station(val name: String, val url: String)

/** Small wrapper around SharedPreferences for everything the user can set. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    var stations: List<Station>
        get() {
            val raw = sp.getString(KEY_STATIONS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Station(o.getString("name"), o.getString("url"))
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
            sp.edit().putString(KEY_STATIONS, arr.toString()).apply()
        }

    var lastStationUrl: String?
        get() = sp.getString("last_station", null)
        set(value) = sp.edit().putString("last_station", value).apply()

    val lastStation: Station? get() = stations.firstOrNull { it.url == lastStationUrl }

    var welcomeSound: Boolean
        get() = sp.getBoolean("welcome_sound", true)
        set(value) = sp.edit().putBoolean("welcome_sound", value).apply()

    var autoStartOnBluetooth: Boolean
        get() = sp.getBoolean("auto_start_bt", true)
        set(value) = sp.edit().putBoolean("auto_start_bt", value).apply()

    var resumeRadioOnConnect: Boolean
        get() = sp.getBoolean("resume_radio", false)
        set(value) = sp.edit().putBoolean("resume_radio", value).apply()

    /** Part of the car's Bluetooth name, e.g. "Alza". Empty means any device. */
    var carBluetoothName: String
        get() = sp.getString("car_bt_name", "") ?: ""
        set(value) = sp.edit().putString("car_bt_name", value.trim()).apply()

    var homeAddress: String
        get() = sp.getString("home_address", "") ?: ""
        set(value) = sp.edit().putString("home_address", value.trim()).apply()

    companion object {
        private const val KEY_STATIONS = "stations"
    }
}
