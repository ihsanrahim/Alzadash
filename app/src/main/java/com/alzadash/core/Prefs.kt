package com.alzadash.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A radio station. [url] may be blank, in which case [search] is looked up on radio-browser.info when played. */
data class Station(val name: String, val url: String, val search: String = "")

/** Popular Malaysian English and Malay stations, built in so Instant Radio works out of the box. */
val DEFAULT_STATIONS = listOf(
    Station("Era", "", "era"),
    Station("Sinar", "", "sinar"),
    Station("Hot FM", "", "hot fm"),
    Station("Kool 101", "", "kool"),
    Station("Hitz", "", "hitz"),
    Station("Gegar", "", "gegar"),
    Station("Suria FM", "", "suria"),
    Station("Molek FM", "", "molek"),
    Station("Zayan", "", "zayan"),
    Station("IKIM", "", "ikim"),
    Station("Mix", "", "mix"),
    Station("Lite", "", "lite"),
    Station("Fly FM", "", "fly fm"),
    Station("BFM 89.9", "", "bfm"),
    Station("Traxx FM", "", "traxx"),
    Station("Nasional FM", "", "nasional"),
)

/** Stations shown first on the car screen's radio row, in this order. The rest follow as saved. */
private val ROW_FIRST = listOf("bfm", "traxx", "hitz", "mix", "ikim", "zayan", "lite")

/** Small wrapper around SharedPreferences for everything the user can set. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    var stations: List<Station>
        get() {
            val raw = sp.getString(KEY_STATIONS, null) ?: return DEFAULT_STATIONS
            val arr = JSONArray(raw)
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Station(o.getString("name"), o.optString("url"), o.optString("search"))
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("search", it.search)) }
            sp.edit().putString(KEY_STATIONS, arr.toString()).apply()
        }

    /** Stations in radio row order: [ROW_FIRST] first, then the rest. Also the order ◀ ▶ steps through. */
    val orderedStations: List<Station>
        get() {
            var list = stations
            // Lists saved before IKIM was built in get it once.
            if (!sp.getBoolean("ikim_added", false)) {
                sp.edit().putBoolean("ikim_added", true).apply()
                if (sp.contains(KEY_STATIONS) && list.none { it.name.lowercase().startsWith("ikim") }) {
                    list = list + Station("IKIM", "", "ikim")
                    stations = list
                }
            }
            fun rank(st: Station): Int {
                val n = st.name.lowercase()
                val i = ROW_FIRST.indexOfFirst { n.startsWith(it) }
                return if (i < 0) ROW_FIRST.size else i
            }
            return list.sortedBy { rank(it) } // stable, so the rest keep their saved order
        }

    var lastStationName: String?
        get() = sp.getString("last_station_name", null)
        set(value) = sp.edit().putString("last_station_name", value).apply()

    val lastStation: Station? get() = stations.firstOrNull { it.name == lastStationName }

    /** Stream links found for built-in stations, so the lookup happens once. */
    fun resolvedUrl(name: String): String? = sp.getString("resolved_$name", null)
    fun saveResolvedUrl(name: String, url: String) = sp.edit().putString("resolved_$name", url).apply()

    /** Last known position, used for prayer times and weather. Defaults to Kuala Lumpur. */
    var lastLat: Double
        get() = sp.getFloat("lat", 3.139f).toDouble()
        set(value) = sp.edit().putFloat("lat", value.toFloat()).apply()
    var lastLng: Double
        get() = sp.getFloat("lng", 101.687f).toDouble()
        set(value) = sp.edit().putFloat("lng", value.toFloat()).apply()

    /** Packages shown in the car screen's shortcut bar, or null to use the defaults. */
    var dockPackages: List<String>?
        get() = sp.getString("dock_packages", null)?.split(",")?.filter { it.isNotBlank() }
        set(value) = sp.edit().putString("dock_packages", value?.joinToString(",")).apply()

    var quranOffered: Boolean
        get() = sp.getBoolean("quran_offered", false)
        set(value) = sp.edit().putBoolean("quran_offered", value).apply()

    var askedPermissions: Boolean
        get() = sp.getBoolean("asked_permissions", false)
        set(value) = sp.edit().putBoolean("asked_permissions", value).apply()

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
