package soy.engindearing.omnitak.mobile.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import soy.engindearing.omnitak.mobile.ui.onboarding.OnboardingFlow
import soy.engindearing.omnitak.mobile.ui.screens.RadioHomeContent
import soy.engindearing.omnitak.mobile.ui.screens.VoiceScreen
import soy.engindearing.omnitak.mobile.domain.ConnectionState
import soy.engindearing.omnitak.mobile.domain.RadioLocationSession

@OptIn(ExperimentalTestApi::class)
class RadioButtonNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dpad_focus_and_select_open_servers_from_radio_home() {
        var route: String? = null
        compose.setContent {
            RadioHomeContent(
                ConnectionState.Disconnected,
                RadioLocationSession.Status.PERMISSION_REQUIRED,
                onOpen = { route = it },
            )
        }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.waitForIdle()
        enterKeyboardMode()
        compose.onNodeWithText("Contacts & messages").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertTrue("radio home did not accept focus", it())
        }
        compose.onNodeWithText("Contacts & messages").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("TAK servers").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("servers", route)
    }

    @Test fun compact_home_keeps_settings_fully_visible_with_long_server_name() {
        compose.setContent {
            RadioHomeContent(
                ConnectionState.Connected("A deliberately long test server label for a small phone", true),
                RadioLocationSession.Status.RUNNING,
                onOpen = {},
            )
        }
        val settings = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Settings is clipped by the bottom edge: $settings / $rootBottom",
            settings.height >= 40f && settings.bottom <= rootBottom - 16f)
    }

    @Test fun select_opens_voice_from_radio_home_without_transmitting() {
        var route: String? = null
        compose.setContent {
            RadioHomeContent(ConnectionState.Disconnected,
                RadioLocationSession.Status.PERMISSION_REQUIRED, onOpen = { route = it })
        }
        enterKeyboardMode()
        compose.onNodeWithText("Voice").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Voice").performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("voice", route)
    }

    @Test fun voice_screen_cannot_transmit_before_connect() {
        compose.setContent { VoiceScreen() }
        compose.onNodeWithText("CONNECT VOICE FIRST").assertExists()
        compose.onNodeWithText("Voice disconnected").assertIsDisplayed()
        enterKeyboardMode()
        compose.onNodeWithContentDescription("Hold to talk")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("CONNECT VOICE FIRST").assertIsDisplayed()
        compose.onNodeWithText("Learn hardware PTT").assertExists()
    }

    @Test fun voice_ptt_learning_can_be_started_and_reset_without_touch() {
        compose.setContent { VoiceScreen() }
        enterKeyboardMode()
        compose.onNodeWithText("Learn hardware PTT")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Learn hardware PTT")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Press a hardware PTT key").assertExists()
        compose.onNodeWithText("Reset hardware PTT")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Reset hardware PTT")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("PTT key not bound").assertExists()
    }

    @Test fun compact_first_run_can_advance_with_select_without_touch() {
        var finished = false
        compose.setContent { OnboardingFlow(onComplete = { finished = true }) }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.waitForIdle()
        enterKeyboardMode()
        val titles = listOf(
            "Welcome to OmniTAK", "Secure & Certified", "Quick & Easy Setup", "Ready to Connect?",
        )
        compose.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertTrue("onboarding did not accept focus", it())
        }
        compose.onNodeWithText("Continue").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Skip").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Continue").assertIsFocused()
        // Back must return to the previous page without touch or a lost focus path.
        compose.onNodeWithText("Continue").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Secure & Certified").assertIsDisplayed()
        compose.onNodeWithText("Back").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Back").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Welcome to OmniTAK").assertIsDisplayed()

        titles.forEachIndexed { index, title ->
            compose.onNodeWithText(title).assertIsDisplayed()
            val label = if (index == titles.lastIndex) "Get Started" else "Continue"
            compose.onNodeWithText(label)
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitForIdle()
        }
        assertTrue("onboarding must finish without touch", finished)
    }

    private fun enterKeyboardMode() {
        // Compose focus requests fail in touch mode; a real D-pad press switches input modes.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
    }
}
