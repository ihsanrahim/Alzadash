package com.alzadash.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** One news story. [image] is a picture address when the feed has one. */
data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val summary: String,
    val image: String?,
    val timeMs: Long,
)

/** A news section. Each feed is tried in turn until one gives stories. */
data class NewsSection(val name: String, val feeds: List<String>, val group: String? = null)

/**
 * Headlines from RSS feeds: Google News for Malaysia in English and Malay, plus the Malay
 * papers' own feeds where they have pictures. Everything is cached for 10 minutes.
 */
object News {
    private fun gn(path: String, lang: String = "en") =
        if (lang == "ms") "https://news.google.com/rss$path${if (path.contains('?')) "&" else "?"}hl=ms&gl=MY&ceid=MY:ms"
        else "https://news.google.com/rss$path${if (path.contains('?')) "&" else "?"}hl=en-MY&gl=MY&ceid=MY:en"

    private fun site(domain: String) = gn("/search?q=site:$domain+when:2d", "ms")

    const val BM = "Berita BM"

    val sections = listOf(
        NewsSection("Top Stories", listOf(gn(""))),
        NewsSection("Malaysia", listOf(gn("/headlines/section/topic/NATION"))),
        NewsSection("World", listOf(gn("/headlines/section/topic/WORLD"))),
        NewsSection("Business", listOf(gn("/headlines/section/topic/BUSINESS"))),
        NewsSection("Technology", listOf(gn("/headlines/section/topic/TECHNOLOGY"))),
        NewsSection("Sports", listOf(gn("/headlines/section/topic/SPORTS"))),
        NewsSection("Semua", listOf(gn("", "ms")), BM),
        NewsSection("Harian Metro", listOf("https://www.hmetro.com.my/feed", site("hmetro.com.my")), BM),
        NewsSection("Berita Harian", listOf("https://www.bharian.com.my/feed", site("bharian.com.my")), BM),
        NewsSection("Utusan Malaysia", listOf("https://www.utusan.com.my/feed/", site("utusan.com.my")), BM),
        NewsSection("Kosmo", listOf("https://www.kosmo.com.my/feed/", site("kosmo.com.my")), BM),
        NewsSection("Sinar Harian", listOf("https://www.sinarharian.com.my/rssFeed/211", site("sinarharian.com.my")), BM),
    )

    private class Entry(val items: List<NewsItem>, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(2)

    /** Stories for [s], fetching in the background when missing or stale; null until the first fetch ends. */
    fun get(s: NewsSection): List<NewsItem>? {
        val e = cache[s.name]
        if ((e == null || System.currentTimeMillis() - e.at > 10 * 60_000) && pending.add(s.name)) {
            pool.execute {
                var items = emptyList<NewsItem>()
                for (f in s.feeds) {
                    items = try { parse(fetch(f), s) } catch (_: Exception) { emptyList() }
                    if (items.isNotEmpty()) break
                }
                if (items.isNotEmpty() || e == null) cache[s.name] = Entry(items, System.currentTimeMillis())
                pending.remove(s.name)
            }
        }
        return e?.items
    }

    /** Headlines for the dashboard ticker: top stories mixed with Malay news. */
    fun ticker(): List<NewsItem> {
        val en = get(sections[0]) ?: emptyList()
        val ms = get(sections.first { it.name == "Semua" }) ?: emptyList()
        val out = mutableListOf<NewsItem>()
        for (i in 0 until maxOf(en.size, ms.size)) {
            en.getOrNull(i)?.let { out += it }
            ms.getOrNull(i)?.let { out += it }
        }
        return out.take(20)
    }

    private val dateFormats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX")

    private fun date(s: String): Long {
        for (f in dateFormats) {
            try { return SimpleDateFormat(f, Locale.US).parse(s.trim())!!.time } catch (_: Exception) {}
        }
        return 0
    }

    private fun parse(xml: String, s: NewsSection): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val out = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""; var link = ""; var desc = ""; var content = ""; var image: String? = null
        var source = ""; var pub = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item", "entry" -> {
                        inItem = true
                        title = ""; link = ""; desc = ""; content = ""; image = null; source = ""; pub = ""
                    }
                    "media:content", "media:thumbnail" -> if (inItem && image == null) {
                        p.getAttributeValue(null, "url")?.takeIf { p.getAttributeValue(null, "medium") != "video" }?.let { image = it }
                    }
                    "enclosure" -> if (inItem && image == null &&
                        (p.getAttributeValue(null, "type") ?: "image").startsWith("image")) {
                        image = p.getAttributeValue(null, "url")
                    }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem) link = p.getAttributeValue(null, "href") ?: p.nextText()
                    "description", "summary" -> if (inItem) desc = p.nextText()
                    "content:encoded" -> if (inItem) content = p.nextText()
                    "source" -> if (inItem) source = p.nextText()
                    "pubDate", "published", "dc:date" -> if (inItem && pub.isEmpty()) pub = p.nextText()
                }
            } else if (p.eventType == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry") && inItem) {
                inItem = false
                var t = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                // Google News puts the paper's name after the last " - ".
                val googleNews = link.contains("news.google.com")
                if (googleNews && source.isEmpty()) {
                    val i = t.lastIndexOf(" - ")
                    if (i > 0) source = t.substring(i + 3)
                }
                if (googleNews) t = t.removeSuffix(" - $source").trim()
                if (source.isEmpty()) source = s.name
                if (image == null) image = Regex("<img[^>]+src=[\"']([^\"']+)").find(content + desc)?.groupValues?.get(1)
                val summary = if (googleNews) "" else Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY).toString()
                    .replace(Regex("\\s+"), " ").trim().take(300)
                if (t.isNotEmpty() && link.isNotEmpty()) out += NewsItem(t, link.trim(), source, summary, image, date(pub))
            }
        }
        return out.take(30)
    }

    // Pictures, loaded small and kept while the app runs.
    private val pictures = ConcurrentHashMap<String, Bitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val picturePool = Executors.newFixedThreadPool(3)

    /**
     * The picture for [item], or null while it loads. Stories whose feed has no picture get the
     * article page's own preview image (its og:image), except Google News links.
     */
    fun picture(item: NewsItem): Bitmap? {
        pictures[item.link]?.let { return it }
        if (item.link in missing || !pending.add("pic:" + item.link)) return null
        picturePool.execute {
            try {
                val url = item.image ?: if (item.link.contains("news.google.com")) null else {
                    Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)")
                        .find(fetch(item.link, 200_000))?.groupValues?.get(1)
                }
                val bmp = url?.let { loadBitmap(Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()) }
                if (bmp != null) pictures[item.link] = bmp else missing += item.link
            } catch (_: Exception) {
                missing += item.link
            }
            pending.remove("pic:" + item.link)
        }
        return null
    }

    private fun loadBitmap(url: String): Bitmap? {
        val bytes = open(url).inputStream.use { it.readBytes() }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= 640) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AlzaDash")
        return c
    }

    private fun fetch(url: String, max: Int = 2_000_000): String =
        open(url).inputStream.use { s ->
            val buf = ByteArray(max)
            var n = 0
            while (n < max) {
                val r = s.read(buf, n, max - n)
                if (r < 0) break
                n += r
            }
            String(buf, 0, n, Charsets.UTF_8)
        }

    fun ago(ms: Long): String {
        if (ms <= 0) return ""
        val m = (System.currentTimeMillis() - ms) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "${m}m ago"
            m < 24 * 60 -> "${m / 60}h ago"
            else -> "${m / (24 * 60)}d ago"
        }
    }
}
