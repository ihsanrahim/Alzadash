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

    private fun send(e: InputEvent, display: Int): Boolean = try {
        setDisplayId.invoke(e, display)
        inject.invoke(manager, e, 0) as Boolean
    } catch (_: Throwable) {
        false
    }
}
