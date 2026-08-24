package com.ejemplo.emulador

import android.opengl.GLSurfaceView

class GameLoop(
    private var glTop: GLSurfaceView,
    private var glBottom: GLSurfaceView,
    private val audioEngine: AudioEngine,
    private val getKeyMask: () -> Int,
    private val getTouchState: () -> TouchDigitizer
) : Runnable {

    @Volatile
    var isRunning: Boolean = false
        private set

    private var gameThread: Thread? = null
    private val targetFrameNs = 1_000_000_000L / 60L
    private val audioPullBuffer = ShortArray(2048)

    fun updateSurfaces(top: GLSurfaceView, bottom: GLSurfaceView) {
        this.glTop = top
        this.glBottom = bottom
    }

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
        } catch (e: InterruptedException) {
        }
    }

    override fun run() {
        var nextFrameTime = System.nanoTime()

        while (isRunning) {
            val keys = getKeyMask()
            val touch = getTouchState()

            NativeBridge.nativeRunFrame(keys, touch.dsX, touch.dsY, touch.isTouching)

            val samplesRead = NativeBridge.nativeGetAudioSamples(audioPullBuffer, audioPullBuffer.size)
            if (samplesRead > 0) {
                audioEngine.writeAudio(audioPullBuffer, 0, samplesRead)
            }

            glTop.requestRender()
            glBottom.requestRender()

            nextFrameTime += targetFrameNs
            val sleepNs = nextFrameTime - System.nanoTime()

            if (sleepNs > 0) {
                val sleepMs = sleepNs / 1_000_000L
                if (sleepMs > 2) {
                    try {
                        Thread.sleep(sleepMs - 1)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                while (System.nanoTime() < nextFrameTime) {
                }
            } else {
                if (sleepNs < -targetFrameNs * 4) {
                    nextFrameTime = System.nanoTime()
                }
            }
        }
    }
}
