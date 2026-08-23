package com.ejemplo.emulador

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioEngine(private val sampleRate: Int = 44100) {

    private var audioTrack: AudioTrack? = null
    private val bufferSize: Int
    @Volatile
    private var isPlaying = false

    init {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        bufferSize = (minBufferSize * 2).coerceAtLeast(8192)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun start() {
        if (!isPlaying) {
            try {
                audioTrack?.play()
                isPlaying = true
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (isPlaying) {
            try {
                isPlaying = false
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (_: Exception) {}
        }
    }

    fun writeAudio(buffer: ShortArray, offset: Int, count: Int) {
        if (isPlaying && audioTrack != null) {
            // Modo NO BLOQUEANTE para que la emulación jamás se congele
            audioTrack?.write(buffer, offset, count, AudioTrack.WRITE_NON_BLOCKING)
        }
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
