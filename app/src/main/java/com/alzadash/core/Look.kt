package com.alzadash.core

import android.content.Context
import android.graphics.Typeface

/** Clock options, car button size, welcome tone and the card beside the map, all set from the phone. */
object Look {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("alzadash", Context.MODE_PRIVATE)

    val CLOCK_FONTS = listOf("Light", "Thin", "Bold", "Black", "Condensed", "Wide", "Italic", "Serif", "Digital", "Script")

    /** -1 follows the phone, 0 is 12-hour, 1 is 24-hour. */
    fun clock24(c: Context): Int = sp(c).getInt("clock_24", -1)
    fun setClock24(c: Context, v: Int) = sp(c).edit().putInt("clock_24", v).apply()

    fun clockFont(c: Context): String = sp(c).getString("clock_font", "Light") ?: "Light"
    fun setClockFont(c: Context, v: String) = sp(c).edit().putString("clock_font", v).apply()

    fun typeface(font: String): Typeface = when (font) {
        "Thin" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        "Bold" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        "Black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        "Condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "Italic" -> Typeface.create("sans-serif-light", Typeface.ITALIC)
        "Serif" -> Typeface.SERIF
        "Digital" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        "Script" -> Typeface.create("cursive", Typeface.NORMAL)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    /** Extra spacing between letters for the "Wide" clock. */
    fun letterSpacing(font: String) = if (font == "Wide") 0.15f else 0f

    /** Size of the car screen's own buttons (shortcut bar, ◀ ▶, quick buttons), 0.7–1.5. */
    fun buttonScale(c: Context): Float = sp(c).getFloat("button_scale", 1f)
    fun setButtonScale(c: Context, v: Float) = sp(c).edit().putFloat("button_scale", v).apply()

    val TONES = listOf("Chime", "Bell", "Soft", "Arcade")
    fun tone(c: Context): String = sp(c).getString("welcome_tone", "Chime") ?: "Chime"
    fun setTone(c: Context, v: String) = sp(c).edit().putString("welcome_tone", v).apply()

    /** The card under the clock beside Waze or Maps in split screen. */
    val SPLIT_CARDS = listOf("Now playing", "Lyrics", "Music player", "Calendar", "Air quality", "Nothing")
    fun splitCard(c: Context): String = sp(c).getString("split_card", "Now playing") ?: "Now playing"
    fun setSplitCard(c: Context, v: String) = sp(c).edit().putString("split_card", v).apply()
}
