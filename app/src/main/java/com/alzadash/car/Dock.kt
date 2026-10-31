package com.alzadash.car

import android.content.Context
import android.graphics.drawable.Drawable
import com.alzadash.core.Pkg

/** One shortcut in the vertical bar on the right of the car screen. */
data class DockApp(val label: String, val packages: List<String>, val webUrl: String?, val isVideo: Boolean)

object Dock {
    val apps = listOf(
        DockApp("YouTube", listOf("app.revanced.android.youtube", "app.rvx.android.youtube", "com.google.android.youtube"), "https://m.youtube.com", true),
        DockApp("Netflix", listOf(Pkg.NETFLIX), "https://www.netflix.com", true),
        DockApp("Hotstar", listOf("in.startv.hotstar.dplus", "in.startv.hotstar", "com.disney.disneyplus"), "https://www.hotstar.com/my", true),
        DockApp("Maps", listOf(Pkg.MAPS), null, false),
        DockApp("Waze", listOf(Pkg.WAZE), null, false),
    )

    /** The first of the app's packages that is installed (so YouTube ReVanced wins over plain YouTube). */
    fun installed(context: Context, app: DockApp): String? =
        app.packages.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun icon(context: Context, app: DockApp): Drawable? = installed(context, app)?.let {
        try { context.packageManager.getApplicationIcon(it) } catch (_: Exception) { null }
    }
}
