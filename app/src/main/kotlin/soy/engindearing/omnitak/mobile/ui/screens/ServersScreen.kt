package soy.engindearing.omnitak.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.data.TAKServer
import soy.engindearing.omnitak.mobile.domain.TakServerPackage
import soy.engindearing.omnitak.mobile.domain.ServerHealth
import soy.engindearing.omnitak.mobile.domain.ServerHealthProjection
import soy.engindearing.omnitak.mobile.i18n.Loc
import soy.engindearing.omnitak.mobile.ui.components.dotColor
import soy.engindearing.omnitak.mobile.ui.theme.HostileRed
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import soy.engindearing.omnitak.mobile.ui.theme.TacticalBackground
import soy.engindearing.omnitak.mobile.ui.theme.TacticalSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onAdd: () -> Unit,
    onQuickConnect: () -> Unit = {},
    onScanQr: () -> Unit = {},
) {
    val compact = LocalConfiguration.current.screenWidthDp < 360
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as OmniTAKApp
    val manager = app.serverManager
    val scope = rememberCoroutineScope()
    var candidate by remember { mutableStateOf<TakServerPackage?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val parsed = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        TakServerPackage.read(it, "TAK server")
                    } ?: error("Cannot open selected ZIP")
                }
            }
            parsed.onSuccess { candidate = it; importError = null }
                .onFailure { importError = takZipError(it) }
            importing = false
        }
    }
    val servers by manager.servers.collectAsState()
    val active by manager.activeServer.collectAsState()
    // Per-server live state — every card reflects its own connection, not
    // just whichever server happens to be "active" (multi-server).
    val serverStates by manager.serverStates.collectAsState()
    // Delete is destructive (a row carries enrollment artifacts — cert,
    // CA pin — that may need admin credentials to redo), so a fat-finger
    // on the trash icon must confirm first. Same pattern as the
    // overlays sheet's "Delete all overlays?" dialog.
    var pendingDelete by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<TAKServer?>(null)
    }

    Scaffold(
        containerColor = TacticalBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (compact) "Servers" else "TAK Servers",
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                },
                actions = {
                    if (compact) {
                        Box {
                            TextButton(onClick = { showMore = true }) { Text("Add / More") }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                DropdownMenuItem(text = { Text("Add server") }, onClick = {
                                    showMore = false
                                    onAdd()
                                })
                                DropdownMenuItem(text = { Text("Quick Connect") }, onClick = {
                                    showMore = false
                                    onQuickConnect()
                                })
                                DropdownMenuItem(text = { Text("Scan enrollment QR") }, onClick = {
                                    showMore = false
                                    onScanQr()
                                })
                            }
                        }
                    } else {
                    // #100 — scan a standard TAK / ATAK enrollment QR
                    // (tak://…/enroll) to CSR-enroll a cert and connect. The
                    // reliable in-app on-ramp that doesn't depend on the OS
                    // camera app routing the tak:// scheme back to us.
                    IconButton(onClick = onScanQr) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan enrollment QR",
                            tint = TacticalAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // Quick Connect — request a client cert from the server's
                    // enrollment endpoint instead of importing a pre-issued .p12.
                    // BUG-C (closed-test, May 2026) — the icon-only bolt was
                    // not discoverable; testers couldn't tell it was for cert
                    // enrollment vs. the + (manual add). Show a visible label.
                    TextButton(onClick = onQuickConnect) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = TacticalAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Quick Connect", color = TacticalAccent)
                    }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TacticalBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        floatingActionButton = {
            if (!compact) {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = TacticalAccent,
                    contentColor = TacticalBackground,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add server")
                }
            }
        },
    ) { inner: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedButton(
                onClick = {
                    importError = null
                    runCatching {
                        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }.onFailure { importError = "No document picker available on this device" }
                },
                enabled = !importing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(if (importing) "Reading ZIP…" else "Import ATAK ZIP") }
            importError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (servers.isEmpty()) {
                EmptyServers(Modifier.weight(1f), compact)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = servers, key = { it.id }) { server ->
                        val health = ServerHealthProjection.project(
                            enabled = server.enabled,
                            state = serverStates[server.id],
                        )
                        ServerCard(
                            server = server,
                            isActive = active?.id == server.id,
                            health = health,
                            compact = compact,
                            onTap = { manager.setActive(server.id) },
                            onToggle = { manager.toggleEnabled(server.id) },
                            onDelete = { pendingDelete = server },
                            onConnectToggle = {
                                if (health.isLive) manager.disconnect(server.id)
                                else manager.connect(server)
                            },
                        )
                    }
                }
            }
        }
    }

    candidate?.let { selected ->
        TakZipPreviewDialog(
            selected,
            onDismiss = { candidate = null },
            onConfirm = {
                candidate = null
                importing = true
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            require(manager.servers.value.none { it.matchesEndpoint(selected.server) }) {
                                "Server already configured"
                            }
                            selected.install(app.certVault, manager::addServer)
                        }
                    }
                    result.onFailure { importError = takZipError(it) }
                    importing = false
                }
            },
        )
    }

    pendingDelete?.let { server ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(Loc.t("servers.delete.title", server.name)) },
            text = { Text(Loc.t("servers.delete.body")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        manager.deleteServer(server.id)
                        pendingDelete = null
                    },
                ) { Text(Loc.t("servers.delete.confirm"), color = HostileRed) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(Loc.t("common.cancel"))
                }
            },
        )
    }
}

@Composable
internal fun TakZipPreviewDialog(candidate: TakServerPackage, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val details = if (candidate.server.useTLS) {
        "(TLS)\nClient: ${candidate.clientFileName}\nCA: ${candidate.caFileName ?: "system trust"}"
    } else {
        "(TCP)\nNo transport encryption"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import TAK server?") },
        text = { Text("${candidate.server.name}\n${candidate.server.host}:${candidate.server.port} $details\n\nCertificates are checked on import.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Import & connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun takZipError(e: Throwable): String =
    if (e is IllegalArgumentException) e.message ?: "Invalid ATAK ZIP" else "Could not read ATAK ZIP"

@Composable
private fun EmptyServers(modifier: Modifier = Modifier, compact: Boolean = false) {
    // #207 — the hint text is taller than a landscape phone viewport.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = TacticalAccent.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            "No TAK servers configured",
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            if (compact) "Use Add / More or Import ATAK ZIP."
            else "Select Import ATAK ZIP for a server package, use + to enter a server " +
                "manually, or Quick Connect to enroll a certificate.",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@Composable
internal fun ServerCard(
    server: TAKServer,
    isActive: Boolean,
    health: ServerHealth,
    compact: Boolean = false,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onConnectToggle: () -> Unit,
) {
    if (compact) {
        var focused by remember { mutableStateOf(false) }
        var showActions by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus }
                .clip(shape).background(TacticalSurface)
                .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(health.dotColor()))
                Spacer(Modifier.width(8.dp))
                Text(server.name, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (server.useTLS) Icon(Icons.Filled.Lock, contentDescription = "TLS",
                    modifier = Modifier.size(14.dp), tint = TacticalAccent)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${server.host}:${server.port}", modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
                Text(health.name.lowercase().replaceFirstChar(Char::uppercaseChar),
                    style = MaterialTheme.typography.labelSmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTap) { Text(if (isActive) "Selected" else "Select") }
                Box {
                    TextButton(onClick = { showActions = true }) { Text("Actions") }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(if (health.isLive) "Disconnect" else "Connect") },
                            enabled = server.enabled,
                            onClick = { showActions = false; onConnectToggle() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (server.enabled) "Disable" else "Enable") },
                            onClick = { showActions = false; onToggle() },
                        )
                        DropdownMenuItem(text = { Text("Delete") }, onClick = {
                            showActions = false
                            onDelete()
                        })
                    }
                }
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TacticalSurface)
            .clickable(onClick = onTap)
            .padding(start = 0.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent stripe — bright when active, dim otherwise
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .background(if (isActive) TacticalAccent else Color.Transparent),
        )
        Spacer(Modifier.width(12.dp))

        // Connection dot: the shared health projection (#209), same colours as
        // the map's top bar. The active server is marked by the stripe on the left.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(health.dotColor()),
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                if (server.useTLS) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "TLS",
                        modifier = Modifier.size(12.dp),
                        tint = TacticalAccent,
                    )
                }
            }
            Text(
                "${server.host}:${server.port}  ·  ${server.protocol.uppercase()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )
        }

        val isLive = health.isLive
        IconButton(
            onClick = onConnectToggle,
            enabled = server.enabled,
        ) {
            Icon(
                if (isLive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isLive) "Disconnect" else "Connect",
                // #209: Stop is an action, not a status. A red Stop on a healthy
                // green row read as "this server is down", so it stays neutral and
                // the dot alone carries the state.
                tint = if (isLive) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                } else {
                    TacticalAccent
                },
            )
        }

        Switch(
            checked = server.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TacticalBackground,
                checkedTrackColor = TacticalAccent,
            ),
        )

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${server.name}",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
