package com.commlink.app.ui.ptt

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.commlink.app.domain.model.MessageType
import com.commlink.app.domain.model.NetworkState
import com.commlink.app.domain.model.PTTButtonState
import com.commlink.app.domain.model.PTTChannel
import com.commlink.app.domain.model.PTTMessage
import com.commlink.app.domain.model.PTTSessionState
import com.commlink.app.domain.model.PTTUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PTTScreen(viewModel: PTTViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val pttButtonState by viewModel.pttButtonState.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (selectedChannel == null) {
        ChannelListScreen(
            channels = viewModel.channels,
            onChannelSelect = { viewModel.selectChannel(it) }
        )
        return
    }

    // Only ask for mic permission once the user has chosen a channel — not on cold start
    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NetworkStatusBar(networkState = networkState)
        SessionStatusBar(sessionState = sessionState)

        MessageList(
            messages = messages,
            modifier = Modifier.weight(1f)
        )

        when (val state = uiState) {
            is PTTUiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is PTTUiState.Error -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )
            else -> {}
        }

        if (!permissionState.status.isGranted) {
            PermissionDeniedContent(onRequestPermission = { permissionState.launchPermissionRequest() })
        } else {
            BottomControls(
                pttButtonState = pttButtonState,
                onSendMessage = { viewModel.sendTextMessage(it) },
                onPTTPress = { viewModel.startPTT() },
                onPTTRelease = { viewModel.stopPTT() },
                onLeaveChannel = { viewModel.leaveChannel() }
            )
        }
    }
}

@Composable
private fun ChannelListScreen(
    channels: List<PTTChannel>,
    onChannelSelect: (PTTChannel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Select Channel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tap a channel to join",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items = channels,
                key = { channel -> channel.id }  // stable key — prevents unnecessary recomposition
            ) { channel ->
                ChannelCard(channel = channel, onClick = { onChannelSelect(channel) })
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: PTTChannel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${channel.participants.size} participants · ${channel.participants.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun NetworkStatusBar(networkState: NetworkState) {
    val (color, text) = when (networkState) {
        is NetworkState.Connected -> Color(0xFF4CAF50) to "Connected"
        is NetworkState.Disconnected -> Color(0xFFF44336) to "Disconnected"
        is NetworkState.Reconnecting -> Color(0xFFFF9800) to "Reconnecting... attempt ${networkState.attempt}"
    }
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
private fun SessionStatusBar(sessionState: PTTSessionState) {
    val text = when (sessionState) {
        is PTTSessionState.Idle -> "Not in channel"
        is PTTSessionState.Inviting -> "Joining channel..."
        is PTTSessionState.Active -> "Channel Active — ${sessionState.participants.size} participants"
        is PTTSessionState.Terminating -> "Leaving channel..."
        is PTTSessionState.Terminated -> "Left channel"
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = text, modifier = Modifier.padding(8.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun MessageList(messages: List<PTTMessage>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        reverseLayout = true
    ) {
        items(
            items = messages.reversed(),
            key = { it.id }  // stable key — only changed items recompose
        ) { message ->
            MessageCard(message = message)
        }
    }
}

@Composable
private fun MessageCard(message: PTTMessage) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.senderName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            when (message.type) {
                MessageType.TEXT -> Text(message.content)
                MessageType.AUDIO -> Text("Voice message", color = Color.Gray, fontStyle = FontStyle.Italic)
                MessageType.INVITE -> Text("Joined channel", color = Color(0xFF4CAF50))
                MessageType.BYE -> Text("Left channel", color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
private fun BottomControls(
    pttButtonState: PTTButtonState,
    onSendMessage: (String) -> Unit,
    onPTTPress: () -> Unit,
    onPTTRelease: () -> Unit,
    onLeaveChannel: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    onSendMessage(messageText)
                    messageText = ""
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PttButton(
                state = pttButtonState,
                onPress = onPTTPress,
                onRelease = onPTTRelease
            )

            Button(
                onClick = onLeaveChannel,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Leave")
            }
        }
    }
}

@Composable
private fun PttButton(
    state: PTTButtonState,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val (containerColor, label) = when (state) {
        PTTButtonState.Idle        -> MaterialTheme.colorScheme.primary to "PTT"
        PTTButtonState.Connecting  -> Color(0xFFFF9800) to "..."
        PTTButtonState.Established -> Color(0xFFD32F2F) to "LIVE"
        PTTButtonState.Revoked     -> Color(0xFF9E9E9E) to "REVOKED"
    }

    // rememberUpdatedState lets pointerInput(Unit) read the current state without
    // restarting the coroutine — a key change (pointerInput(state)) would cancel
    // tryAwaitRelease() mid-hold every time state transitions, eating the release event.
    val currentState = rememberUpdatedState(state)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(containerColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (currentState.value != PTTButtonState.Revoked) {
                                onPress()
                                tryAwaitRelease()
                                onRelease()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (state) {
                PTTButtonState.Idle        -> "Hold to talk"
                PTTButtonState.Connecting  -> "Connecting..."
                PTTButtonState.Established -> "Transmitting"
                PTTButtonState.Revoked     -> "Floor taken"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun PermissionDeniedContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Microphone permission required for Push-to-Talk")
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestPermission) { Text("Grant Permission") }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
