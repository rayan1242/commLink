package com.commlink.app.domain.model

sealed class NetworkState {
    object Connected : NetworkState()
    object Disconnected : NetworkState()
    data class Reconnecting(val attempt: Int) : NetworkState()
}
