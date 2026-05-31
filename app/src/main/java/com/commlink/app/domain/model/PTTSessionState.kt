package com.commlink.app.domain.model

sealed class PTTSessionState {
    object Idle : PTTSessionState()
    data class Inviting(val channelId: String) : PTTSessionState()
    data class Active(val channelId: String, val participants: List<String>) : PTTSessionState()
    object Terminating : PTTSessionState()
    object Terminated : PTTSessionState()
}
