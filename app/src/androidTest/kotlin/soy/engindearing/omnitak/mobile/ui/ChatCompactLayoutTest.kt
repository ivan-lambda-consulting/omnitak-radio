package soy.engindearing.omnitak.mobile.ui

import android.view.View
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import soy.engindearing.omnitak.mobile.data.ChatConversation
import soy.engindearing.omnitak.mobile.ui.screens.ConversationDetailView

class ChatCompactLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun conversation_header_and_send_control_clear_system_bars() {
        compose.setContent {
            ConversationDetailView(
                conversation = ChatConversation("test", "Test broadcast", isGroup = true),
                messages = emptyList(), selfUid = "self", selfCallsign = "Test",
                onBack = {}, onSend = {}, multiServer = false, serverNameFor = { null },
            )
        }
        val title = compose.onNodeWithText("Test broadcast").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithContentDescription("Send").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val content = compose.activity.findViewById<View>(android.R.id.content)
        val origin = IntArray(2).also(content::getLocationOnScreen)
        val statusBar = content.rootWindowInsets.getInsets(WindowInsets.Type.statusBars()).top
        assertTrue("Header overlaps the status bar: $title, origin=${origin[1]}, inset=$statusBar",
            title.top + origin[1] >= statusBar)
        assertTrue("Send is cut by the navigation bar: $send", send.bottom <= screen.bottom - 16f)
    }
}
