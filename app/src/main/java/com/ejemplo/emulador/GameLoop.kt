package com.ejemplo.emulador

import android.opengl.GLSurfaceView

class GameLoop(
    private val glTop: GLSurfaceView,
    private val glBottom: GLSurfaceView,
    private val getKeyMask: () -> Int,
    private val getTouchState: () -> TouchDigitizer
) : Runnable {

    @Volatile
    var isRunning: Boolean = false
        private set

    private var gameThread: Thread? = null
    private val targetFrameTimeMs = 1000L / 60L

    fun start() {
        if (isRunning) return
        isRunning = true
        gameThread = Thread(this, "DS-GameLoop").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning = false
        try {
            gameThread?.join(500)
            gameThread = null
        } catch (_: InterruptedException) { }
    }

    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()

            val keys = getKeyMask()
            val touch = getTouchState()

            // 1. Ejecutar el fotograma en C++
            NativeBridge.nativeRunFrame(keys, touch.dsX, touch.dsY, touch.isTouching)

            // 2. Renderizar fotograma en GPU
            glTop.requestRender()
            glBottom.requestRender()

            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = targetFrameTimeMs - elapsed

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }
}
