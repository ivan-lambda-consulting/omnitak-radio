package soy.engindearing.omnitak.mobile.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
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
import soy.engindearing.omnitak.mobile.ui.screens.MessageComposer
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.data.TAKServer

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

    @Test fun server_setup_actions_are_reachable_on_compact_screen() {
        var selected: String? = null
        compose.setContent {
            ServersScreen(
                onAdd = { selected = "add" },
                onQuickConnect = { selected = "enroll" },
            )
        }
        enterKeyboardMode()
        compose.onNodeWithContentDescription("Add server").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add server")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithContentDescription("Add server").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("add", selected)
        compose.onNodeWithText("Quick Connect").assertIsDisplayed()
        compose.onNodeWithText("Quick Connect")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Quick Connect").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("enroll", selected)
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
