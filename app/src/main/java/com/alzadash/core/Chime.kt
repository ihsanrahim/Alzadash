package com.alzadash.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** The welcome sound: a short rising four-note chime, synthesised so no audio file is needed. */
object Chime {
    private const val RATE = 44100

    fun play(tone: String = "Chime", onDone: () -> Unit = {}) {
        thread(name = "welcome-chime") {
            try {
                // Each tone: its notes, the gap between them and how long each rings.
                val (notes, gap, ringSec) = when (tone) {
                    "Bell" -> Triple(doubleArrayOf(880.0, 1318.51), 0.35, 1.6)
                    "Soft" -> Triple(doubleArrayOf(392.0, 493.88, 587.33), 0.22, 1.1)
                    "Arcade" -> Triple(doubleArrayOf(659.25, 783.99, 1046.50, 1318.51, 1567.98), 0.08, 0.35)
                    else -> Triple(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 0.16, 0.9) // C5 E5 G5 C6
                }
                val step = (RATE * gap).toInt()
                val ring = (RATE * ringSec).toInt()
                val buf = FloatArray(step * (notes.size - 1) + ring)
                notes.forEachIndexed { i, f ->
                    val start = i * step
                    for (t in 0 until ring) {
                        val env = exp(-t / (RATE * 0.22)) * min(1.0, t / 300.0)
                        val x = 2 * PI * f * t / RATE
                        buf[start + t] += ((sin(x) * 0.7 + sin(2 * x) * 0.2) * env * 0.3).toFloat()
                    }
                }
                val pcm = ShortArray(buf.size) { (buf[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 100)
                track.release()
            } catch (_: Exception) {
            } finally {
                onDone()
            }
        }
    }
}
