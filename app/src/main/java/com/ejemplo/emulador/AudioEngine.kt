package com.ejemplo.emulador

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioEngine(private val sampleRate: Int = 44100) {

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    fun start() {
        if (isPlaying) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Búfer ajustado para baja latencia (evita retraso en efectos de sonido)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        isPlaying = true
    }

    fun writeAudio(samples: ShortArray, offset: Int, length: Int) {
        if (isPlaying && audioTrack != null) {
            audioTrack?.write(samples, offset, length)
        }
    }

    fun stop() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) { }
        audioTrack = null
    }
}
