package soy.engindearing.omnitak.mobile.ui

import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import soy.engindearing.omnitak.mobile.ui.screens.AddServerScreen
import soy.engindearing.omnitak.mobile.ui.screens.AddServerForm
import soy.engindearing.omnitak.mobile.ui.screens.EnrollServerScreen
import soy.engindearing.omnitak.mobile.ui.screens.SettingsScreen
import soy.engindearing.omnitak.mobile.ui.screens.ServersScreen
import soy.engindearing.omnitak.mobile.ui.screens.ServerCard
import soy.engindearing.omnitak.mobile.domain.ServerHealth
import soy.engindearing.omnitak.mobile.ui.screens.MessageComposer
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.data.TAKServer
import soy.engindearing.omnitak.mobile.domain.TakServerPackage
import soy.engindearing.omnitak.mobile.ui.screens.TakZipPreviewDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File

@OptIn(ExperimentalTestApi::class)
class RadioFormNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun add_server_name_ime_next_moves_to_host() {
        compose.setContent { AddServerScreen(onDone = {}) }
        focus("Name")
        compose.onNodeWithText("Name").performTextInput("Test server")
        compose.onNodeWithText("Name").performImeAction()
        compose.onNodeWithText("Host or IP").assertIsFocused()
    }

    @Test fun quick_connect_host_ime_next_moves_to_port() {
        compose.setContent { EnrollServerScreen(onDone = {}) }
        focus("Server host")
        compose.onNodeWithText("Server host").performTextInput("tak.example.test")
        compose.onNodeWithText("Server host").performImeAction()
        compose.onNodeWithText("Connect port").assertIsFocused()
    }

    @Test fun add_server_hardware_enter_moves_from_name_to_host() {
        compose.setContent { AddServerScreen(onDone = {}) }
        focus("Name")
        compose.onNodeWithText("Name").performTextInput("Test server")
        compose.onNodeWithText("Name").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Host or IP").assertIsFocused()
    }

    @Test fun port_next_reaches_tls_switch_then_certificate_picker() {
        compose.setContent { AddServerScreen(onDone = {}) }
        focus("Port")
        compose.onNodeWithText("Port").performImeAction()
        compose.onNodeWithContentDescription("Use TLS").assertIsFocused()
        compose.onNodeWithContentDescription("Use TLS").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Choose .p12").assertIsFocused()
    }

    @Test fun quick_connect_hardware_enter_moves_from_host_to_port() {
        compose.setContent { EnrollServerScreen(onDone = {}) }
        focus("Server host")
        compose.onNodeWithText("Server host").performTextInput("tak.example.test")
        compose.onNodeWithText("Server host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Connect port").assertIsFocused()
    }

    @Test fun quick_connect_port_next_reaches_enrollment_port_and_display_name() {
        compose.setContent { EnrollServerScreen(onDone = {}) }
        focus("Connect port")
        compose.onNodeWithText("Connect port").performImeAction()
        compose.onNodeWithText("Enrollment port").assertIsFocused()
        compose.onNodeWithText("Enrollment port").performImeAction()
        compose.onNodeWithText("Display name").assertIsFocused()
    }

    @Test fun add_server_password_done_focuses_save_without_saving() {
        compose.setContent { AddServerScreen(onDone = {}) }
        focus("Name")
        compose.onNodeWithText("Name").performTextInput("Test server")
        compose.onNodeWithText("Host or IP").performTextInput("tak.example.test")
        compose.onNodeWithText("Password").performTextInput("test-only")
        compose.onNodeWithText("Password").performImeAction()
        compose.onNodeWithText("Save Server").assertIsFocused()
    }

    @Test fun quick_connect_password_done_focuses_enroll_without_connecting() {
        compose.setContent { EnrollServerScreen(onDone = {}) }
        focus("Server host")
        compose.onNodeWithText("Server host").performTextInput("tak.example.test")
        compose.onNodeWithText("Username").performTextInput("test-user")
        compose.onNodeWithText("Password").performTextInput("test-only")
        compose.onNodeWithText("Password").performImeAction()
        compose.onNodeWithText("Enroll & Save").assertIsFocused()
    }

    @Test fun add_server_save_activates_with_select_without_touch_or_real_server_write() {
        val captured = mutableListOf<TAKServer>()
        var done = false
        val vault = (compose.activity.application as OmniTAKApp).certVault
        compose.setContent {
            AddServerForm(onDone = { done = true }, certVault = vault, addServer = { captured += it })
        }
        focus("Name")
        compose.onNodeWithText("Name").performTextInput("Test server")
        compose.onNodeWithText("Host or IP").performTextInput("tak.example.test")
        compose.onNodeWithText("Password").performTextInput("test-only")
        compose.onNodeWithText("Password").performImeAction()
        compose.onNodeWithText("Save Server").assertIsFocused()
        compose.onNodeWithText("Save Server").performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(done)
        assertEquals("Test server", captured.single().name)
        assertEquals("tak.example.test", captured.single().host)
    }

    @Test fun callsign_done_moves_out_of_editor_to_team_setting() {
        compose.setContent { SettingsScreen() }
        focus("Callsign")
        compose.onNodeWithText("Callsign").performImeAction()
        compose.onNodeWithText("Team").assertIsFocused()
    }

    @Test fun hardware_select_opens_team_menu_without_changing_the_team() {
        compose.setContent { SettingsScreen() }
        focus("Team")
        compose.onNodeWithText("Team").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("White").assertIsDisplayed()
    }

    @Test fun hardware_enter_also_opens_team_menu() {
        compose.setContent { SettingsScreen() }
        focus("Team")
        compose.onNodeWithText("Team").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("White").assertIsDisplayed()
    }

    @Test fun server_setup_actions_are_reachable_on_compact_screen() {
        var selected: String? = null
        compose.setContent {
            ServersScreen(
                onAdd = { selected = "add" },
                onQuickConnect = { selected = "enroll" },
            )
        }
        enterKeyboardMode()
        val title = compose.onNodeWithText("Servers").fetchSemanticsNode().boundsInRoot
        assertTrue("server title wraps on a compact screen: $title", title.width >= 50f && title.height <= 32f)
        compose.onNodeWithText("Import ATAK ZIP").assertIsDisplayed()
        compose.onNodeWithText("Import ATAK ZIP")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Import ATAK ZIP").assertIsFocused()
        compose.onNodeWithText("Add / More").assertIsDisplayed()
        compose.onNodeWithText("Add / More")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Add / More").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Add server").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Add server").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("add", selected)
        compose.onNodeWithText("Add / More").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Add / More").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Quick Connect").assertIsDisplayed()
        compose.onNodeWithText("Quick Connect")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Quick Connect").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("enroll", selected)
    }

    @Test fun server_name_stays_readable_on_compact_screen() {
        val server = TAKServer(name = "A long test server name", host = "radio.test", port = 8089, useTLS = true)
        compose.setContent {
            ServerCard(
                server = server, isActive = true, health = ServerHealth.CONNECTED, compact = true,
                onTap = {}, onToggle = {}, onDelete = {}, onConnectToggle = {},
            )
        }
        val nameBounds = compose.onNodeWithText(server.name).fetchSemanticsNode().boundsInRoot
        assertTrue("server name wraps on the compact screen: $nameBounds",
            nameBounds.width >= 100f && nameBounds.height <= 32f)
        enterKeyboardMode()
        compose.onNodeWithText("Actions").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Actions").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
        compose.onNodeWithText("Disable").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test fun ata_zip_preview_requires_explicit_select_before_import() {
        val zipBytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("server.pref"))
                zip.write("<preferences><entry key=\"connectString0\">radio.test:8089:ssl</entry>".toByteArray())
                zip.write("<entry key=\"certificateLocation\">client.p12</entry></preferences>".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("client.p12"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }.toByteArray()
        val preview = TakServerPackage.read(ByteArrayInputStream(zipBytes), "team.zip")
        var confirmed = false
        compose.setContent {
            TakZipPreviewDialog(preview, onConfirm = { confirmed = true }, onDismiss = {})
        }
        // AlertDialog owns a separate window, so the Activity itself is no longer focused.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        compose.onNodeWithText("radio.test:8089", substring = true).assertIsDisplayed()
        assertTrue("preflight must not import anything", !confirmed)
        compose.onNodeWithText("Import & connect").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Import & connect").performKeyInput { pressKey(Key.DirectionCenter) }
        assertTrue(confirmed)
    }

    @Test fun selecting_a_zip_opens_preflight_without_writing_a_server() {
        val file = File(compose.activity.cacheDir, "radio-zip-picker-test.zip")
        val before = (compose.activity.application as OmniTAKApp).serverManager.servers.value
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("server.pref"))
            zip.write("<preferences><entry key=\"connectString0\">preview.test:8089:tcp</entry></preferences>".toByteArray())
            zip.closeEntry()
        }
        var launched: Intent? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?,
            ) {
                launched = contract.createIntent(compose.activity, input)
                dispatchResult(requestCode, Uri.fromFile(file))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    ServersScreen(onAdd = {}, onQuickConnect = {})
                }
            }
            enterKeyboardMode()
            compose.onNodeWithText("Import ATAK ZIP")
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithText("Import ATAK ZIP").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Import TAK server?").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched?.action)
            assertTrue(launched?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("application/zip") == true)
            compose.onNodeWithText("preview.test:8089", substring = true).assertIsDisplayed()
            assertEquals(before, (compose.activity.application as OmniTAKApp).serverManager.servers.value)
            compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        } finally {
            file.delete()
        }
    }

    @Test fun message_ime_send_trims_text_without_touch_or_network() {
        val sent = mutableListOf<String>()
        compose.setContent { MessageComposer(onSend = { sent += it }) }
        focus("Message")
        compose.onNodeWithText("Message").performTextInput(" Check in ")
        compose.onNodeWithText("Message").performImeAction()
        assertEquals(listOf("Check in"), sent)
        compose.onNodeWithText("Message").performImeAction()
        assertEquals("empty draft must not send twice", 1, sent.size)
    }

    @Test fun message_hardware_enter_sends_when_editing() {
        val sent = mutableListOf<String>()
        compose.setContent { MessageComposer(onSend = { sent += it }) }
        focus("Message")
        compose.onNodeWithText("Message").performTextInput("ping")
        compose.onNodeWithText("Message").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("ping"), sent)
    }

    @Test fun message_shift_enter_keeps_draft_until_explicit_send() {
        val sent = mutableListOf<String>()
        compose.setContent { MessageComposer(onSend = { sent += it }) }
        focus("Message")
        compose.onNodeWithText("Message").performTextInput("two lines")
        compose.onNodeWithText("Message").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        assertTrue("Shift+Enter must not transmit", sent.isEmpty())
        compose.onNodeWithText("Message").performImeAction()
        assertEquals(listOf("two lines"), sent)
    }

    @Test fun message_send_button_works_with_select() {
        val sent = mutableListOf<String>()
        compose.setContent { MessageComposer(onSend = { sent += it }) }
        focus("Message")
        compose.onNodeWithText("Message").performTextInput("ready")
        compose.onNodeWithContentDescription("Send").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription("Send").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf("ready"), sent)
    }

    private fun focus(label: String) {
        enterKeyboardMode()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.RequestFocus) {
            assertTrue("$label did not accept focus", it())
        }
    }

    private fun enterKeyboardMode() {
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
    }
}
