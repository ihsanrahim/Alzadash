package com.alzadash.car

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.alzadash.core.Prefs

/** One shortcut in the vertical bar on the right of the car screen. */
data class DockApp(val label: String, val pkg: String)

object Dock {
    const val MAX = 6

    /** Default picks by app name (so patched apps like ReVanced and Morphe are found), with words to skip. */
    private val defaults = listOf(
        "quran" to null,
        "youtube" to "music",
        "reezn" to null,
        "spotify" to null,
        "youtube music" to null,
        "yt music" to null,
    )

    /** Every app with a launcher icon, sorted by name. */
    fun launchable(context: Context): List<DockApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { DockApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun load(context: Context): List<DockApp> {
        val all = launchable(context)
        val prefs = Prefs(context)
        prefs.dockPackages?.let { saved ->
            var list = saved
            // Shortcuts picked before the Quran app was added to the defaults: put it on top once.
            if (!prefs.quranOffered) {
                prefs.quranOffered = true
                quran(all)?.takeIf { it.pkg !in saved }?.let {
                    list = (listOf(it.pkg) + saved).take(MAX)
                    prefs.dockPackages = list
                }
            }
            return list.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
        }
        val picked = mutableListOf<DockApp>()
        for ((name, skip) in defaults) {
            if (name == "quran") {
                quran(all)?.let { picked += it }
                continue
            }
            if (name == "yt music" && picked.any { it.label.lowercase().contains("music") }) continue
            all.filter { a ->
                val l = a.label.lowercase()
                l.startsWith(name) && (skip == null || !l.contains(skip)) && a !in picked
            }
                // Prefer patched builds over the stock Google app with the same name.
                .minByOrNull { if (it.pkg.startsWith("com.google")) 1 else 0 }
                ?.let { picked += it }
        }
        return picked
    }

    /** A Quran app such as Al Quran (Tafsir & by Word). */
    private fun quran(all: List<DockApp>): DockApp? =
        all.firstOrNull { "quran" in it.label.lowercase() || "qur'an" in it.label.lowercase() || "quran" in it.pkg }

    fun icon(context: Context, app: DockApp): Drawable? =
        try { context.packageManager.getApplicationIcon(app.pkg) } catch (_: Exception) { null }
}
