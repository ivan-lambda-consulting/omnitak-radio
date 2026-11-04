package soy.engindearing.omnitak.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import soy.engindearing.omnitak.mobile.ui.onboarding.OnboardingFlow
import soy.engindearing.omnitak.mobile.ui.screens.RadioHomeContent
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
        compose.onNodeWithText("Contacts & messages").performSemanticsAction(SemanticsActions.RequestFocus) {
            assertTrue("radio home did not accept focus", it())
        }
        compose.onNodeWithText("Contacts & messages").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("TAK servers").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals("servers", route)
    }

    @Test fun compact_first_run_can_advance_with_select_without_touch() {
        var finished = false
        compose.setContent { OnboardingFlow(onComplete = { finished = true }) }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.waitForIdle()
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
}
