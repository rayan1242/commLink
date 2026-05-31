package com.commlink.app.domain.model

sealed class PTTButtonState {
    object Idle : PTTButtonState()
    object Connecting : PTTButtonState()
    object Established : PTTButtonState()
    object Revoked : PTTButtonState()
}
