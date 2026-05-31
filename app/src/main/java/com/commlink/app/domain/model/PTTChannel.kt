package com.commlink.app.domain.model

data class PTTChannel(
    val id: String,
    val name: String,
    val participants: List<String> = emptyList()
)
