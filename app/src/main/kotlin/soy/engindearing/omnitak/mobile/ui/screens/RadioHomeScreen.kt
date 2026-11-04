package soy.engindearing.omnitak.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.domain.ConnectionState
import soy.engindearing.omnitak.mobile.domain.RadioLocationSession

@Composable
fun RadioHomeScreen(onOpen: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as OmniTAKApp
    val connection by app.serverManager.connectionState.collectAsState()
    val location by app.radioLocationSession.status.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("OmniTAK Radio")
        Text(connectionLabel(connection))
        Text(if (location == RadioLocationSession.Status.RUNNING) "GPS updating" else "GPS unavailable")
        Button(onClick = { onOpen("chat") }, modifier = Modifier.fillMaxWidth()) { Text("Messages") }
        Button(onClick = { onOpen("servers") }, modifier = Modifier.fillMaxWidth()) { Text("TAK servers") }
        Button(onClick = { onOpen("settings") }, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}

internal fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "TAK connected: ${state.serverName}"
    is ConnectionState.Connecting -> "TAK connecting"
    is ConnectionState.Failed -> "TAK connection failed"
    ConnectionState.Disconnected -> "TAK disconnected"
}
