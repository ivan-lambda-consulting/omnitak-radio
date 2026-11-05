package soy.engindearing.omnitak.mobile.ui.navigation

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.data.UserPrefs
import soy.engindearing.omnitak.mobile.ui.components.rememberLocationPermission
import soy.engindearing.omnitak.mobile.ui.screens.AboutScreen
import soy.engindearing.omnitak.mobile.ui.screens.AddServerScreen
import soy.engindearing.omnitak.mobile.ui.screens.ChatScreen
import soy.engindearing.omnitak.mobile.ui.screens.EnrollServerScreen
import soy.engindearing.omnitak.mobile.ui.screens.ImportPreviewDialog
import soy.engindearing.omnitak.mobile.ui.screens.MeshDeviceSettingsScreen
import soy.engindearing.omnitak.mobile.ui.screens.MeshTopologyScreen
import soy.engindearing.omnitak.mobile.ui.screens.MeshtasticScreen
import soy.engindearing.omnitak.mobile.ui.screens.ProfilesScreen
import soy.engindearing.omnitak.mobile.ui.screens.RadioHomeScreen
import soy.engindearing.omnitak.mobile.ui.screens.ServersScreen
import soy.engindearing.omnitak.mobile.ui.screens.SettingsScreen
import soy.engindearing.omnitak.mobile.ui.screens.VoiceScreen

@Composable
fun AppNav() {
    val context = LocalContext.current
    val app = context.applicationContext as OmniTAKApp
    val prefs by app.userPrefsStore.prefs.collectAsState(initial = UserPrefs())
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val locationGranted by rememberLocationPermission()
    LaunchedEffect(locationGranted) {
        app.radioLocationSession.permissionChanged()
        if (locationGranted) app.refreshConnectionService()
    }

    DisposableEffect(prefs.keepScreenOn) {
        val window = context.findActivity()?.window
        if (prefs.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val pendingChat by app.pendingChatConversation.collectAsState()
    LaunchedEffect(pendingChat) {
        pendingChat?.let { id ->
            nav.navigate("chat?convoId=${android.net.Uri.encode(id)}")
            app.pendingChatConversation.value = null
        }
    }

    NavHost(navController = nav, startDestination = "radio") {
        composable("radio") { RadioHomeScreen(onOpen = nav::navigate) }
        composable("voice") { VoiceScreen() }
        composable("chat") { ChatScreen() }
        composable(
            "chat?convoId={convoId}",
            arguments = listOf(navArgument("convoId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry -> ChatScreen(initialConversationId = entry.arguments?.getString("convoId")) }
        composable("servers") {
            ServersScreen(
                onAdd = { nav.navigate("servers/add") },
                onQuickConnect = { nav.navigate("servers/enroll") },
                onScanQr = { nav.navigate("servers/scan") },
            )
        }
        composable("servers/add") { AddServerScreen(onDone = { nav.popBackStack() }) }
        composable("servers/enroll") { EnrollServerScreen(onDone = { nav.popBackStack() }) }
        composable("servers/scan") {
            soy.engindearing.omnitak.mobile.ui.screens.ServerEnrollScanRoute(onDone = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onOpenAbout = { nav.navigate("about") },
                onOpenPlugins = { nav.navigate("settings/plugin/$it") },
                onOpenPluginsList = { nav.navigate("plugins") },
                onOpenProfiles = { nav.navigate("settings/profiles") },
            )
        }
        composable("settings/profiles") { ProfilesScreen(onBack = { nav.popBackStack() }) }
        composable("about") { AboutScreen() }
        composable("mesh") {
            MeshtasticScreen(
                onOpenTopology = { nav.navigate("mesh_topology") },
                onOpenDeviceSettings = { nav.navigate("mesh/device-settings") },
                onOpenChannels = { nav.navigate("mesh/channels") },
                onOpenChat = { nav.navigate("chat?convoId=${android.net.Uri.encode(it)}") },
            )
        }
        composable("mesh_topology") { MeshTopologyScreen(onBack = { nav.popBackStack() }) }
        composable("mesh/device-settings") { MeshDeviceSettingsScreen(onDone = { nav.popBackStack() }) }
        composable("mesh/channels") {
            soy.engindearing.omnitak.mobile.ui.screens.MeshChannelShareScreen(onBack = { nav.popBackStack() })
        }
        composable("plugins") {
            soy.engindearing.omnitak.mobile.ui.screens.PluginsListScreen(
                onBack = { nav.popBackStack() },
                onOpenPlugin = { nav.navigate("settings/plugin/$it") },
            )
        }
        composable("settings/plugin/{pluginId}", arguments = listOf(navArgument("pluginId") {
            type = NavType.StringType
        })) { entry ->
            soy.engindearing.omnitak.mobile.ui.screens.PluginDetailScreen(
                pluginId = entry.arguments?.getString("pluginId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }

    val pendingImport by app.pendingProfileImport.collectAsState()
    pendingImport?.let { candidate ->
        ImportPreviewDialog(
            profile = candidate,
            onConfirm = {
                app.pendingProfileImport.value = null
                scope.launch {
                    app.configProfileStore.saveProfile(candidate)
                    app.configProfileStore.apply(candidate)
                }
            },
            onDismiss = { app.pendingProfileImport.value = null },
        )
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
