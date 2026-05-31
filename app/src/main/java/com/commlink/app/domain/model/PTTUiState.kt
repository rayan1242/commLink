package com.commlink.app.domain.model

sealed class PTTUiState {
    object Idle : PTTUiState()
    object Loading : PTTUiState()
    data class Active(val sessionState: PTTSessionState, val networkState: NetworkState) : PTTUiState()
    data class Error(val message: String) : PTTUiState()
}
