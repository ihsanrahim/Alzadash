package com.alzadash.core

import android.content.Context
import android.net.Uri
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject

/** A saved website. */
data class Bookmark(val title: String, val url: String)

/** Settings and bookmarks for the built-in browser on the car screen. */
object Browser {
    const val HOME = "alzadash://home"

    /** Built-in shortcuts on the Sites page. They can't be removed; bookmarks are added after them. */
    val SITES = listOf(
        Bookmark("Google", "https://www.google.com"),
        Bookmark("RTM Klik", "https://rtmklik.rtm.gov.my/"),
        Bookmark("Wikipedia", "https://www.wikipedia.org"),
        Bookmark("Google News", "https://news.google.com"),
        Bookmark("Astro Awani", "https://www.astroawani.com"),
        Bookmark("The Star", "https://www.thestar.com.my"),
        Bookmark("Berita Harian", "https://www.bharian.com.my"),
        Bookmark("Harian Metro", "https://www.hmetro.com.my"),
    )

    /** Buttons that can be shown on the browser's floating bar, in order. */
    val BAR_BUTTONS = listOf(
        "back" to "Back", "forward" to "Forward", "home" to "Sites", "reload" to "Reload",
        "star" to "Bookmark", "tabs" to "Tabs", "zoomout" to "Zoom out", "zoomin" to "Zoom in",
        "keyboard" to "Keyboard", "fullscreen" to "Full screen", "move" to "Move bar", "close" to "Close",
    )
    private val DEFAULT_BAR = BAR_BUTTONS.map { it.first }.filter { it != "forward" }.toSet()

    /** Bar colours: name to ARGB without alpha. */
    val BAR_COLOURS = listOf(
        "Dark" to 0x1A1F27, "Black" to 0x000000, "Teal" to 0x2C7A7B, "Blue" to 0x2B5797,
        "Red" to 0x9B2C2C, "Grey" to 0x4A5568,
    )
    val THEMES = listOf("Auto", "Dark", "Light")

    /** Ad and tracker hosts blocked when ad blocking is on; subdomains are blocked too. */
    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "adnxs.com",
        "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "amazon-adsystem.com",
        "scorecardresearch.com", "pubmatic.com", "rubiconproject.com", "openx.net", "adsrvr.org",
        "moatads.com", "casalemedia.com", "smartadserver.com", "teads.tv", "yieldmo.com",
        "facebook.net", "ads.yahoo.com", "advertising.com", "zedo.com", "popads.net",
        "propellerads.com", "mgid.com", "revcontent.com", "adcolony.com", "inmobi.com",
    )

    private fun sp(context: Context) = context.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    fun bookmarks(context: Context): List<Bookmark> {
        val raw = sp(context).getString("bookmarks", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Bookmark(o.getString("title"), o.getString("url")) } }
            .filter { !it.url.contains("youtube.com") }
    }

    fun saveBookmarks(context: Context, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        sp(context).edit().putString("bookmarks", arr.toString()).apply()
    }

    fun addBookmark(context: Context, b: Bookmark) =
        saveBookmarks(context, bookmarks(context).filter { it.url != b.url } + b)

    fun removeBookmark(context: Context, url: String) =
        saveBookmarks(context, bookmarks(context).filter { it.url != url })

    fun adBlock(context: Context) = sp(context).getBoolean("browser_adblock", true)
    fun setAdBlock(context: Context, on: Boolean) = sp(context).edit().putBoolean("browser_adblock", on).apply()
    fun desktop(context: Context) = sp(context).getBoolean("browser_desktop", false)
    fun setDesktop(context: Context, on: Boolean) = sp(context).edit().putBoolean("browser_desktop", on).apply()

    // ---- AutoDeck-style bar and page settings ----
    fun barButtons(context: Context): Set<String> = sp(context).getStringSet("browser_bar", null) ?: DEFAULT_BAR
    fun setBarButton(context: Context, key: String, on: Boolean) {
        val now = barButtons(context).toMutableSet()
        if (on) now += key else now -= key
        if (key == "close" && !on) now += "close" // the bar always keeps a way out
        sp(context).edit().putStringSet("browser_bar", now).apply()
    }
    fun barColour(context: Context) = sp(context).getString("browser_bar_colour", "Dark")!!
    fun setBarColour(context: Context, v: String) = sp(context).edit().putString("browser_bar_colour", v).apply()
    fun barColourInt(context: Context): Int {
        val rgb = BAR_COLOURS.firstOrNull { it.first == barColour(context) }?.second ?: 0x1A1F27
        val a = (barOpacity(context) * 255 / 100).coerceIn(30, 255)
        return (a shl 24) or rgb
    }
    /** Percent, 30..100. */
    fun barOpacity(context: Context) = sp(context).getInt("browser_bar_opacity", 85)
    fun setBarOpacity(context: Context, v: Int) = sp(context).edit().putInt("browser_bar_opacity", v.coerceIn(30, 100)).apply()
    /** Percent, 60..160. */
    fun barSize(context: Context) = sp(context).getInt("browser_bar_size", 100)
    fun setBarSize(context: Context, v: Int) = sp(context).edit().putInt("browser_bar_size", v.coerceIn(60, 160)).apply()
    /** Where the bar sits, as fractions of the free space (0 = left/top, 1 = right/bottom). */
    fun barX(context: Context) = sp(context).getFloat("browser_bar_x", 0.5f)
    fun barY(context: Context) = sp(context).getFloat("browser_bar_y", 1f)
    fun setBarPos(context: Context, x: Float, y: Float) =
        sp(context).edit().putFloat("browser_bar_x", x.coerceIn(0f, 1f)).putFloat("browser_bar_y", y.coerceIn(0f, 1f)).apply()
    fun theme(context: Context) = sp(context).getString("browser_theme", "Auto")!!
    fun setTheme(context: Context, v: String) = sp(context).edit().putString("browser_theme", v).apply()
    /** Page zoom in percent, 50..250. */
    fun zoom(context: Context) = sp(context).getInt("browser_zoom", 100)
    fun setZoom(context: Context, v: Int) = sp(context).edit().putInt("browser_zoom", v.coerceIn(50, 250)).apply()
    /** The last link sent from the phone app, shown as "From phone" on the Sites page. */
    fun phoneUrl(context: Context) = sp(context).getString("browser_phone_url", null)
    fun setPhoneUrl(context: Context, url: String) = sp(context).edit().putString("browser_phone_url", url).apply()

    fun isAd(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        while (true) {
            if (h in AD_HOSTS) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** Turns what was typed or said into a web address: a link as is, anything else as a Google search. */
    fun toUrl(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return HOME
        if (Regex("^[a-z]+://", RegexOption.IGNORE_CASE).containsMatchIn(s)) return s
        if (!s.contains(' ') && Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/.*)?$").matches(s)) return "https://$s"
        return "https://www.google.com/search?q=" + Uri.encode(s)
    }

    /** The Sites page: a search box, then the built-in shortcuts, "From phone" and bookmarks as square tiles. */
    fun homeHtml(context: Context): String {
        fun tile(b: Bookmark, mark: String = "") =
            "<a class='tile' href='${Html.escapeHtml(b.url)}'><span class='ic'>${Html.escapeHtml(mark.ifEmpty { b.title.take(1).uppercase() })}</span><span class='t'>${Html.escapeHtml(b.title)}</span></a>"
        val phone = phoneUrl(context)?.let { tile(Bookmark("From phone", it), "📱") } ?: ""
        val sites = SITES.joinToString("") { tile(it) }
        val marks = bookmarks(context).joinToString("") { tile(it, "★") }
        return """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>
 body{margin:0;background:#0E1116;color:#fff;font-family:sans-serif;padding:3vh 3vw 20vh}
 form{display:flex;gap:2vw;margin-bottom:4vh}
 input{flex:1;font-size:4vh;padding:2vh 2vw;border-radius:5vh;border:0;background:#1A1F27;color:#fff}
 button{font-size:4vh;padding:0 4vw;border-radius:5vh;border:0;background:#4FD1C5;color:#0E1116;font-weight:bold}
 h2{font-size:3vh;color:#8A94A6;font-weight:normal;margin:3vh 0 2vh}
 .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(17vh,1fr));gap:2vh}
 .tile{aspect-ratio:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1.2vh;border-radius:2vh;background:#1A1F27;color:#fff;text-decoration:none;font-size:2.8vh;text-align:center;padding:1vh}
 .ic{width:8vh;height:8vh;border-radius:1.6vh;background:#0E1116;display:flex;align-items:center;justify-content:center;font-size:4.4vh;font-weight:bold;color:#4FD1C5}
 .t{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;max-width:100%}
</style></head><body>
<form action='https://www.google.com/search'><input name='q' placeholder='Search or type a web address'><button>Go</button></form>
<div class='grid'>$phone$sites</div>
${if (marks.isEmpty()) "" else "<h2>Bookmarks</h2><div class='grid'>$marks</div>"}
</body></html>"""
    }
}
