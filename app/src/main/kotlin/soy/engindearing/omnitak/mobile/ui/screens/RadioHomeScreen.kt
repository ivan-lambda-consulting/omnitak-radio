package soy.engindearing.omnitak.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.domain.ConnectionState
import soy.engindearing.omnitak.mobile.domain.RadioLocationSession

@Composable
fun RadioHomeScreen(onOpen: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as OmniTAKApp
    val connection by app.serverManager.connectionState.collectAsState()
    val location by app.radioLocationSession.status.collectAsState()

    RadioHomeContent(connection, location, onOpen)
}

@Composable
internal fun RadioHomeContent(
    connection: ConnectionState,
    location: RadioLocationSession.Status,
    onOpen: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(connectionLabel(connection), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (location == RadioLocationSession.Status.RUNNING) "GPS updating" else "GPS unavailable")
        RadioAction("Contacts & messages", "chat", onOpen)
        RadioAction("TAK servers", "servers", onOpen)
        RadioAction("Mesh", "mesh", onOpen)
        RadioAction("Settings", "settings", onOpen)
    }
}

@Composable
private fun RadioAction(label: String, route: String, onOpen: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = { onOpen(route) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        border = if (focused) BorderStroke(3.dp, Color.White) else null,
    ) { Text(label) }
}

internal fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "TAK connected: ${state.serverName}"
    is ConnectionState.Connecting -> "TAK connecting"
    is ConnectionState.Failed -> "TAK connection failed"
    ConnectionState.Disconnected -> "TAK disconnected"
}
