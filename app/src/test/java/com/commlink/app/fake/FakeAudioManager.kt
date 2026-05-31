package com.commlink.app.fake

import com.commlink.app.data.audio.PTTAudioManager

// No-op AudioManager for unit tests — prevents Android AudioRecord/AudioTrack stubs from crashing
class FakeAudioManager : PTTAudioManager() {
    override fun startRecording(onAudioChunk: (ByteArray) -> Unit) {}
    override fun stopRecording() {}
    override fun playAudioChunk(audioBytes: ByteArray) {}
    override fun release() {}
}
