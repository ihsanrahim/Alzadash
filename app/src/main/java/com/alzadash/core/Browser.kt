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

    private val DEFAULT_BOOKMARKS = listOf(
        Bookmark("Google", "https://www.google.com"),
        Bookmark("YouTube", "https://m.youtube.com"),
        Bookmark("Wikipedia", "https://www.wikipedia.org"),
        Bookmark("The Star", "https://www.thestar.com.my"),
        Bookmark("Berita Harian", "https://www.bharian.com.my"),
        Bookmark("Harian Metro", "https://www.hmetro.com.my"),
        Bookmark("Netflix", "https://www.netflix.com"),
        Bookmark("Hotstar", "https://www.hotstar.com/my"),
    )

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
        val raw = sp(context).getString("bookmarks", null) ?: return DEFAULT_BOOKMARKS
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Bookmark(o.getString("title"), o.getString("url")) } }
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

    /** The start page: a search box and big tiles for the bookmarks. */
    fun homeHtml(context: Context): String {
        val tiles = bookmarks(context).joinToString("") { b ->
            val t = Html.escapeHtml(b.title)
            val letter = Html.escapeHtml(b.title.take(1).uppercase())
            "<a class='tile' href='${Html.escapeHtml(b.url)}'><span class='ic'>$letter</span><span>$t</span></a>"
        }
        return """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>
 body{margin:0;background:#0E1116;color:#fff;font-family:sans-serif;padding:3vh 3vw}
 form{display:flex;gap:2vw;margin-bottom:4vh}
 input{flex:1;font-size:4vh;padding:2vh 2vw;border-radius:5vh;border:0;background:#1A1F27;color:#fff}
 button{font-size:4vh;padding:0 4vw;border-radius:5vh;border:0;background:#4FD1C5;color:#0E1116;font-weight:bold}
 .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:3vh 2vw}
 .tile{display:flex;flex-direction:column;align-items:center;gap:1.5vh;color:#fff;text-decoration:none;font-size:3.4vh;text-align:center}
 .ic{width:13vh;height:13vh;border-radius:50%;background:#1A1F27;display:flex;align-items:center;justify-content:center;font-size:6vh;font-weight:bold;color:#4FD1C5}
</style></head><body>
<form action='https://www.google.com/search'><input name='q' placeholder='Search or type a web address'><button>Go</button></form>
<div class='grid'>$tiles</div>
</body></html>"""
    }
}
