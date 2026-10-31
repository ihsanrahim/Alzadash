package com.alzadash.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Pkg {
    const val PLAY_STORE = "com.android.vending"
    const val NETFLIX = "com.netflix.mediaclient"
    const val YOUTUBE = "com.google.android.youtube"
    const val GEMINI = "com.google.android.apps.bard"
    const val MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"
}

/** Phone-side app launching. */
object Launch {
    /** Opens an app, or its Play Store page if it isn't installed. */
    fun app(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            false
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun gemini(context: Context) {
        if (context.packageManager.getLaunchIntentForPackage(Pkg.GEMINI) != null) {
            app(context, Pkg.GEMINI)
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            app(context, Pkg.GEMINI)
        }
    }
}
