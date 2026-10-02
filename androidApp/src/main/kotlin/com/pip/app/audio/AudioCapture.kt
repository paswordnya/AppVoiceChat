package com.pip.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mic capture, matching the real WS mic-up contract (PRD §4.4): 16kHz mono
 * PCM16, streamed continuously. Android counterpart to iOS's `AVAudioEngine`
 * mic tap in `VoiceSocketClient.swift`'s `prepareAudio()` — this class is
 * intentionally native/Android-only (PRD §3's "Native Audio Engine"
 * exclusion): PCM bytes cross into the shared module as raw `ByteArray`,
 * nothing about capture itself is shared.
 *
 * `VOICE_COMMUNICATION` source enables the platform's own echo
 * cancellation/noise suppression where available — same reasoning as iOS's
 * `.voiceChat` `AVAudioSession` mode (barge-in relies on it not picking up
 * the device's own TTS playback as if it were the user talking).
 */
class AudioCapture(private val onChunk: suspend (ByteArray) -> Unit) {
    private val sampleRate = 16_000
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Gates whether captured bytes actually get sent — mirrors iOS's
     * `VoiceSocketClient`, where the mic tap is always installed but PTT
     * mode gates on this instead of adding/removing the tap live (which
     * would require tearing down and rebuilding `AudioRecord`). Defaults
     * to `true` so continuous listening modes are unaffected.
     */
    @Volatile private var sendingEnabled = true

    fun setSendingEnabled(enabled: Boolean) {
        sendingEnabled = enabled
    }

    /** Caller must have already verified `RECORD_AUDIO` is granted. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (audioRecord != null) return

        val minBufferSize =
            AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBufferSize <= 0) return

        val record =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        audioRecord = record
        record.startRecording()

        job =
            scope.launch {
                val buffer = ByteArray(minBufferSize)
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0 && sendingEnabled) {
                        onChunk(if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read))
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }
}
