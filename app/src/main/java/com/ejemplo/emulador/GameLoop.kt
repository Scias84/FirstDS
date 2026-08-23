package com.ejemplo.emulador

import android.opengl.GLSurfaceView

class GameLoop(
    private val glTop: GLSurfaceView,
    private val glBottom: GLSurfaceView,
    private val audioEngine: AudioEngine,
    private val getKeyMask: () -> Int,
    private val getTouchState: () -> TouchDigitizer
) : Runnable {

    @Volatile
    var isRunning: Boolean = false
        private set

    private var gameThread: Thread? = null
    private val targetFrameTimeMs = 1000L / 60L
    private val audioSamplesPerFrame = 735 * 2 // 1470 shorts por cuadro
    private val audioFrameBuffer = ShortArray(audioSamplesPerFrame)

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

            // 1. Ejecutar ciclo de CPU, GPU y SPU
            NativeBridge.nativeRunFrame(keys, touch.dsX, touch.dsY, touch.isTouching)

            // 2. Extraer y reproducir audio en tiempo real
            NativeBridge.nativeGetAudioSamples(audioFrameBuffer, audioSamplesPerFrame)
            audioEngine.writeAudio(audioFrameBuffer, 0, audioSamplesPerFrame)

            // 3. Renderizar imagen en pantalla
            glTop.requestRender()
            glBottom.requestRender()

            // 4. Control de sincronización a 60 FPS
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
