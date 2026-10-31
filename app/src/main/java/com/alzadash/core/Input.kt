package com.alzadash.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.view.MotionEvent
import com.alzadash.IInput
import rikka.shizuku.Shizuku

/**
 * Fast touches for apps on the car screen, through [InputService]. Every call returns false
 * when the service isn't connected yet, so the caller can fall back to the `input` command.
 */
object Input {
    @Volatile private var service: IInput? = null
    @Volatile private var binding = false
    private val handler = Handler(HandlerThread("AlzaDash input").apply { start() }.looper)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) service = IInput.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = false
            service = null
        }
    }

    /** Starts the service once Shizuku is allowed. Cheap to call often. */
    fun connect(context: Context) {
        if (service != null || binding || !Shell.available()) return
        binding = true
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, InputService::class.java.name))
                .daemon(false)
                .processNameSuffix("input")
                .debuggable(false)
                .version(4)
            Shizuku.bindUserService(args, connection)
        } catch (_: Throwable) {
            binding = false
        }
    }

    private fun send(display: Int, action: Int, x: Float, y: Float, down: Long) {
        try {
            service?.touch(display, action, x, y, down)
        } catch (_: Throwable) {
            service = null
        }
    }

    fun tap(display: Int, x: Float, y: Float): Boolean {
        if (service == null) return false
        handler.post {
            endDrag.run()
            val down = SystemClock.uptimeMillis()
            send(display, MotionEvent.ACTION_DOWN, x, y, down)
            handler.postDelayed({ send(display, MotionEvent.ACTION_UP, x, y, down) }, 40)
        }
        return true
    }

    fun key(display: Int, code: Int): Boolean {
        val s = service ?: return false
        handler.post {
            try {
                s.key(display, code)
            } catch (_: Throwable) {
                service = null
            }
        }
        return true
    }

    private var dragDisplay = -1
    private var dragX = 0f
    private var dragY = 0f
    private var dragDown = 0L
    private val endDrag = Runnable {
        if (dragDisplay >= 0) send(dragDisplay, MotionEvent.ACTION_UP, dragX, dragY, dragDown)
        dragDisplay = -1
    }

    /**
     * Android Auto reports a finger drag as a stream of small scrolls. They become one held-down
     * finger that moves with them and lifts shortly after the scrolls stop.
     */
    fun drag(display: Int, width: Int, height: Int, dx: Float, dy: Float): Boolean {
        if (service == null) return false
        handler.post {
            handler.removeCallbacks(endDrag)
            val nx = dragX - dx
            val ny = dragY - dy
            if (dragDisplay != display || nx !in 1f..width - 1f || ny !in 1f..height - 1f) {
                endDrag.run()
                dragDisplay = display
                dragX = width / 2f
                dragY = height / 2f
                dragDown = SystemClock.uptimeMillis()
                send(display, MotionEvent.ACTION_DOWN, dragX, dragY, dragDown)
                dragX -= dx
                dragY -= dy
            } else {
                dragX = nx
                dragY = ny
            }
            send(display, MotionEvent.ACTION_MOVE, dragX, dragY, dragDown)
            handler.postDelayed(endDrag, 150)
        }
        return true
    }
}
