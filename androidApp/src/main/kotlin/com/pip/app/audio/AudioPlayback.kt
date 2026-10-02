package com.pip.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * TTS reply playback, matching the real WS TTS-down contract (PRD §4.4):
 * 24kHz mono PCM16, streamed. Android counterpart to iOS's `AVAudioEngine`
 * player node in `VoiceSocketClient.swift` — native/Android-only (PRD §3).
 */
class AudioPlayback {
    private val sampleRate = 24_000
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (audioTrack != null) return

        val minBufferSize =
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferSize <= 0) return

        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        track.play()
        audioTrack = track
    }

    /** Blocking write (MODE_STREAM) — call from a background dispatcher, never the main thread. */
    fun play(bytes: ByteArray) {
        audioTrack?.write(bytes, 0, bytes.size)
    }

    /** Barge-in: drop whatever's queued for playback immediately, matching iOS's `stopPlayback()`. */
    fun clearQueued() {
        audioTrack?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.play() }
        }
    }

    fun stop() {
        audioTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioTrack = null
    }
}
