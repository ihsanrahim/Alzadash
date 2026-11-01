package com.alzadash.core

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.alzadash.IInput
import kotlin.system.exitProcess

/**
 * Started by Shizuku as the shell user. Injecting events here takes about a millisecond,
 * where the `input` command starts a whole new process for every tap.
 */
class InputService() : IInput.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    private val manager: Any by lazy {
        try {
            // Android 14 and later.
            Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)!!
        } catch (_: Throwable) {
            InputManager::class.java.getDeclaredMethod("getInstance").invoke(null)!!
        }
    }
    private val inject by lazy {
        manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }
    private val setDisplayId by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun touch(display: Int, action: Int, x: Float, y: Float, downTime: Long): Boolean {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 1f, 1f, 0, 1f, 1f, 0, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        return send(e, display).also { e.recycle() }
    }

    override fun key(display: Int, code: Int): Boolean {
        val t = SystemClock.uptimeMillis()
        val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        return send(down, display) && send(KeyEvent.changeAction(down, KeyEvent.ACTION_UP), display)
    }

    /**
     * Moves an app that is already open on the phone onto [display] (the car screen).
     * Without this, opening Spotify or YouTube while it runs on the phone just brings it
     * up on the phone again. Returns true when a task was moved.
     */
    override fun moveTask(pkg: String, display: Int): Boolean = try {
        val atm = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
        val getTasks = atm.javaClass.methods.first { it.name == "getTasks" }
        // Fill in whatever this Android version's getTasks wants: 50 tasks, no filters, any display.
        val args = getTasks.parameterTypes.mapIndexed { i, t ->
            when (t) {
                Int::class.javaPrimitiveType -> if (i == 0) 50 else -1
                Boolean::class.javaPrimitiveType -> false
                else -> null
            }
        }.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        val tasks = getTasks.invoke(atm, *args) as List<Any>
        var moved = false
        for (t in tasks) {
            val cls = Class.forName("android.app.TaskInfo")
            val base = (cls.getField("baseActivity").get(t) as? android.content.ComponentName)?.packageName
                ?: (cls.getField("baseIntent").get(t) as? android.content.Intent)?.component?.packageName
            if (base != pkg) continue
            val onDisplay = try { cls.getField("displayId").getInt(t) } catch (_: Throwable) { -1 }
            if (onDisplay == display) continue
            val id = cls.getField("taskId").getInt(t)
            val move = atm.javaClass.methods.firstOrNull { it.name == "moveRootTaskToDisplay" }
                ?: atm.javaClass.methods.firstOrNull { it.name == "moveStackToDisplay" }
                ?: break
            val target = if (move.name == "moveStackToDisplay") {
                try { cls.getField("stackId").getInt(t) } catch (_: Throwable) { id }
            } else id
            move.invoke(atm, target, display)
            moved = true
        }
        moved
    } catch (_: Throwable) {
        false
    }

    private fun send(e: InputEvent, display: Int): Boolean = try {
        setDisplayId.invoke(e, display)
        inject.invoke(manager, e, 0) as Boolean
    } catch (_: Throwable) {
        false
    }
}
