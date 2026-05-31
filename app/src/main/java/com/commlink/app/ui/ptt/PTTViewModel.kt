package com.commlink.app.ui.ptt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commlink.app.data.repository.PTTRepository
import com.commlink.app.domain.model.MessageType
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTButtonState
import com.commlink.app.domain.model.PTTChannel
import com.commlink.app.domain.model.PTTMessage
import com.commlink.app.domain.model.PTTSessionState
import com.commlink.app.domain.model.PTTUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PTTViewModel @Inject constructor(
    private val repository: PTTRepository
) : ViewModel() {

    val channels = listOf(
        PTTChannel(id = "channel_alpha",    name = "Alpha Team",  participants = listOf("Officer A", "Officer B")),
        PTTChannel(id = "channel_bravo",    name = "Bravo Squad", participants = listOf("Officer C", "Officer D")),
        PTTChannel(id = "channel_dispatch", name = "Dispatch",    participants = listOf("Dispatch", "All Units")),
        PTTChannel(id = "channel_command",  name = "Command",     participants = listOf("Commander")),
    )

    private val _selectedChannel = MutableStateFlow<PTTChannel?>(null)
    val selectedChannel: StateFlow<PTTChannel?> = _selectedChannel

    private val _currentChannelId = MutableStateFlow("")

    // flatMapLatest re-subscribes to Room whenever the channel changes
    val messages: StateFlow<List<PTTMessage>> = _currentChannelId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else repository.getMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sessionState: StateFlow<PTTSessionState> = repository.sessionState
    val networkState: StateFlow<NetworkState> = repository.networkState

    private val _uiState = MutableStateFlow<PTTUiState>(PTTUiState.Idle)
    val uiState: StateFlow<PTTUiState> = _uiState

    private val _pttButtonState = MutableStateFlow<PTTButtonState>(PTTButtonState.Idle)
    val pttButtonState: StateFlow<PTTButtonState> = _pttButtonState

    private var pttJob: Job? = null

    init {
        viewModelScope.launch {
            repository.incomingAudio.collect { audioBytes ->
                repository.playReceivedAudio(audioBytes)
            }
        }
        // Revoke PTT when another user takes the floor (server sends a non-audio message while we're transmitting)
        viewModelScope.launch {
            repository.incomingMessages.collect {
                if (_pttButtonState.value == PTTButtonState.Established) {
                    revokePTT()
                }
            }
        }
    }

    fun selectChannel(channel: PTTChannel) {
        _selectedChannel.value = channel
        _currentChannelId.value = channel.id
        joinChannel(channel.id)
    }

    fun joinChannel(channelId: String) {
        viewModelScope.launch {
            _uiState.value = PTTUiState.Loading
            try {
                repository.joinChannel(channelId)
                _uiState.value = PTTUiState.Active(
                    sessionState = sessionState.value,
                    networkState = networkState.value
                )
            } catch (e: Exception) {
                _uiState.value = PTTUiState.Error(e.message ?: "Failed to join channel")
            }
        }
    }

    fun sendTextMessage(content: String) {
        if (content.isEmpty()) return
        viewModelScope.launch {
            val message = PTTMessage(
                channelId = _currentChannelId.value,
                senderId = PTTRepository.OFFICER_ID,
                senderName = PTTRepository.OFFICER_NAME,
                content = content,
                type = MessageType.TEXT
            )
            repository.sendTextMessage(message)
        }
    }

    fun startPTT() {
        pttJob = viewModelScope.launch {
            _pttButtonState.value = PTTButtonState.Connecting
            repository.startPTT()
            delay(500L) // wait for floor-control ACK
            _pttButtonState.value = PTTButtonState.Established
        }
    }

    fun stopPTT() {
        pttJob?.cancel()
        _pttButtonState.value = PTTButtonState.Idle
        repository.stopPTT()
    }

    fun leaveChannel() {
        viewModelScope.launch {
            repository.leaveChannel()
            _selectedChannel.value = null
            _currentChannelId.value = ""
            _pttButtonState.value = PTTButtonState.Idle
            _uiState.value = PTTUiState.Idle
        }
    }

    private fun revokePTT() {
        pttJob?.cancel()
        repository.stopPTT()
        viewModelScope.launch {
            _pttButtonState.value = PTTButtonState.Revoked
            delay(1500L)
            _pttButtonState.value = PTTButtonState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.releaseAudio()
    }
}
