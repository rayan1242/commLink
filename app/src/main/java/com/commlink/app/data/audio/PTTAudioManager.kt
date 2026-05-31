package com.commlink.app.data.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class PTTAudioManager @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 16000       // 16kHz — good voice quality, low bandwidth
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    // lazy — Android APIs not called until first use (safe for unit-test instantiation)
    private val bufferSize by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false

    open fun startRecording(onAudioChunk: (ByteArray) -> Unit) {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            bufferSize
        )
        audioRecord?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    onAudioChunk(buffer.copyOf(bytesRead))
                }
            }
        }
    }

    open fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    open fun playAudioChunk(audioBytes: ByteArray) {
        if (audioTrack == null) {
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL, // takes audio priority — can't be muted by silent mode
                SAMPLE_RATE,
                CHANNEL_OUT,
                ENCODING,
                bufferSize,
                AudioTrack.MODE_STREAM          // real-time stream, not buffered
            )
            audioTrack?.play()
        }
        audioTrack?.write(audioBytes, 0, audioBytes.size)
    }

    open fun release() {
        stopRecording()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
