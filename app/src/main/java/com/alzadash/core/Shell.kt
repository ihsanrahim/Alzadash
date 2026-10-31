package com.alzadash.core

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Runs shell commands through Shizuku. That lets AlzaDash open real apps on the car screen
 * and pass taps to them. Everything here fails quietly when Shizuku isn't running.
 */
object Shell {
    private val queue = Executors.newSingleThreadExecutor()

    fun available(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(1)
        } catch (_: Throwable) {
        }
    }

    fun run(vararg cmd: String) {
        queue.execute {
            try {
                // newProcess is hidden in the current Shizuku API but still present.
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val p = m.invoke(null, arrayOf(*cmd), null, null) as Process
                p.waitFor()
            } catch (_: Throwable) {
            }
        }
    }

    fun tap(display: Int, x: Float, y: Float) =
        run("input", "-d", "$display", "tap", "${x.toInt()}", "${y.toInt()}")

    fun swipe(display: Int, x1: Float, y1: Float, x2: Float, y2: Float) =
        run("input", "-d", "$display", "swipe", "${x1.toInt()}", "${y1.toInt()}", "${x2.toInt()}", "${y2.toInt()}", "250")

    fun key(display: Int, code: Int) = run("input", "-d", "$display", "keyevent", "$code")

    /** Opens an activity in a new task on the given display. */
    fun startOnDisplay(display: Int, component: String, extraKey: String? = null, extraValue: String? = null) {
        val cmd = mutableListOf("am", "start", "--display", "$display", "-f", "0x18000000", "-n", component)
        if (extraKey != null && extraValue != null) cmd += listOf("--es", extraKey, extraValue)
        run(*cmd.toTypedArray())
    }
}
