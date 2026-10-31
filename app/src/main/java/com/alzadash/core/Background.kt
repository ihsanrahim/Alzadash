package com.alzadash.core

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File
import kotlin.math.max

/** The car dashboard's background picture, saved as a small JPEG in the app's files. */
object Background {
    private const val MAX_SIDE = 1600
    private var cached: Bitmap? = null
    private var cachedStamp = 0L

    private fun file(context: Context) = File(context.filesDir, "dash_bg.jpg")

    /** Copies the phone's wallpaper. Returns false when Android doesn't let apps read it. */
    fun fromWallpaper(context: Context): Boolean = try {
        val d = WallpaperManager.getInstance(context).drawable
        d != null && save(context, toBitmap(d))
    } catch (_: Exception) {
        false
    }

    fun fromUri(context: Context, uri: Uri): Boolean = try {
        val bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        bmp != null && save(context, bmp)
    } catch (_: Exception) {
        false
    }

    fun clear(context: Context) {
        file(context).delete()
        cached = null
    }

    /** The picture, or null if none was chosen. Reloaded when the file changes. */
    fun get(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists()) {
            cached = null
            return null
        }
        if (cached == null || f.lastModified() != cachedStamp) {
            cached = BitmapFactory.decodeFile(f.path)
            cachedStamp = f.lastModified()
        }
        return cached
    }

    private fun save(context: Context, src: Bitmap): Boolean {
        val scale = MAX_SIDE.toFloat() / max(src.width, src.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        file(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cached = null
        return true
    }

    private fun toBitmap(d: Drawable): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 1080
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }
}
