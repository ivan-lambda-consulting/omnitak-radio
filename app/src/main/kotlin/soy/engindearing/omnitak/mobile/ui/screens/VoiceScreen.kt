package soy.engindearing.omnitak.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.domain.VoiceConfig
import soy.engindearing.omnitak.mobile.domain.VoiceConfigStore
import soy.engindearing.omnitak.mobile.domain.VoiceController
import soy.engindearing.omnitak.mobile.domain.VoiceTrustStore

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTAKApp
    val voice = app.voiceController
    val status by voice.status.collectAsState()
    val inputStatus by voice.inputStatus.collectAsState()
    val store = remember { runCatching { VoiceConfigStore(context) }.getOrNull() }
    val saved = remember(store) { store?.load() ?: VoiceConfig() }
    var host by remember { mutableStateOf(saved.host) }
    var port by remember { mutableStateOf(saved.port.toString()) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var note by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    fun connect() {
        val config = VoiceConfig(host.trim(), port.toIntOrNull() ?: 0, username.trim(), password)
        note = when {
            !config.isValid() -> "Enter host, port and username"
            store == null || !store.save(config) -> "Secure voice settings unavailable"
            !voice.connect(config) -> "Voice could not start; check permission and certificate"
            else -> ""
        }
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) connect() else note = "Microphone permission required for voice"
    }
    val certificate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            note = runCatching { "Voice cert SHA-256: ${VoiceTrustStore(context).importFrom(uri)}" }
                .getOrElse { "Certificate rejected: ${it.message}" }
        }
    }
    DisposableEffect(voice) { onDispose { voice.release(); voice.cancelLearning() } }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Voice · Mumble", style = MaterialTheme.typography.titleMedium)
        Text(status.label, style = MaterialTheme.typography.bodySmall)
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(host, { host = it }, label = { Text("Voice server") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Voice username") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Voice password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { certificate.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Trust voice certificate")
        }
        Text("Compare the displayed SHA-256 fingerprint before connecting.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            if (status.connected) voice.disconnect() else mic.launch(Manifest.permission.RECORD_AUDIO)
        }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color.White)) {
            Text(if (status.connected) "Disconnect voice" else "Save & connect voice")
        }
        HoldToTalk(voice, status.connected, status.transmitting)
        Text("Hold Select or hold the button to talk. Release to stop. First channel: server root.",
            style = MaterialTheme.typography.bodySmall)
        Text(inputStatus, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { voice.learnPtt() }, modifier = Modifier.fillMaxWidth()) {
            Text("Learn hardware PTT")
        }
        Button(onClick = { voice.resetPtt() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset hardware PTT")
        }
    }
}

@Composable
private fun HoldToTalk(voice: VoiceController, connected: Boolean, transmitting: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().height(58.dp)
            .onFocusChanged { focused = it.isFocused; if (!focused) voice.release() }
            .onPreviewKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown) voice.press()
                    if (event.type == KeyEventType.KeyUp) voice.release()
                    true
                }
            }
            .pointerInput(connected) {
                detectTapGestures(onPress = {
                    voice.press()
                    try { tryAwaitRelease() } finally { voice.release() }
                })
            }
            .semantics {
                role = Role.Button
                contentDescription = if (transmitting) "Stop talking" else "Hold to talk"
                onClick {
                    if (transmitting) voice.release() else voice.press()
                    true
                }
            }
            .focusable()
            .border(if (focused) 3.dp else 1.dp, Color.White, shape)
            .background(if (transmitting) Color(0xFFAE322F) else Color(0xFF137B56), shape)
            .padding(14.dp),
    ) { Text(if (transmitting) "TRANSMITTING · release" else if (connected) "HOLD TO TALK" else "CONNECT VOICE FIRST",
        color = Color.White) }
}
