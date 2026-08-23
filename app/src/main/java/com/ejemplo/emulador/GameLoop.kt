package com.ejemplo.emulador

import android.opengl.GLSurfaceView
import com.dsemu.drastic.DraSticBridge

class GameLoop(
    private val glTop: GLSurfaceView,
    private val glBottom: GLSurfaceView,
    private val getKeyMask: () -> Int
) : Runnable {

    @Volatile
    var isRunning: Boolean = false
        private set

    private var gameThread: Thread? = null
    private val targetFrameTimeMs = 1000L / 60L // ~16.6ms por cuadro (60 FPS)

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

            // 1. Enviar estado de botones al motor
            val currentKeys = getKeyMask()
            try {
                DraSticBridge.updateFrame(currentKeys)
            } catch (_: Throwable) { }

            // 2. Solicitar actualización de gráficos en pantalla
            glTop.requestRender()
            glBottom.requestRender()

            // 3. Control de velocidad a 60 FPS
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
