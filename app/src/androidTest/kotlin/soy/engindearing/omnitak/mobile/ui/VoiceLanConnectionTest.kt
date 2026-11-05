package soy.engindearing.omnitak.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Base64
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import soy.engindearing.omnitak.mobile.OmniTAKApp
import soy.engindearing.omnitak.mobile.domain.VoiceConfig
import soy.engindearing.omnitak.mobile.domain.VoiceTrustStore
import soy.engindearing.omnitak.mobile.ui.screens.VoiceScreen

/** Opt-in: pass host, password and base64 PEM via instrumentation extras against a test LAN server. */
@OptIn(ExperimentalTestApi::class)
class VoiceLanConnectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pinned_voice_server_connects_and_releases_ptt() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("voice_host")
        val password = args.getString("voice_password")
        val pem = args.getString("voice_pem")
        assumeTrue("Voice LAN test is opt-in", !host.isNullOrBlank() && !password.isNullOrBlank() && !pem.isNullOrBlank())
        val app = compose.activity.application as OmniTAKApp
        assumeTrue("Grant RECORD_AUDIO to the emulator before the test",
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        VoiceTrustStore(app).importCertificate(Base64.decode(pem, Base64.DEFAULT))
        val voice = app.voiceController
        try {
            compose.runOnUiThread {
                assertTrue(voice.connect(VoiceConfig(host!!, 64738, "omnitak-test", password!!)))
            }
            compose.waitUntil(20_000) { voice.status.value.connected ||
                voice.status.value.label !in listOf("Voice disconnected", "Voice connecting") }
            assertTrue("Mumble did not connect: ${voice.status.value.label}", voice.status.value.connected)
            compose.setContent { VoiceScreen() }
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithContentDescription("Hold to talk")
                .performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithContentDescription("Hold to talk")
                .performKeyInput { keyDown(Key.DirectionCenter) }
            assertTrue("PTT did not enter transmit state", voice.status.value.transmitting)
            compose.onNodeWithContentDescription("Stop talking")
                .performKeyInput { keyUp(Key.DirectionCenter) }
            assertTrue("PTT remained active", !voice.status.value.transmitting)
        } finally {
            compose.runOnUiThread { voice.disconnect() }
        }
    }

    @Test fun a_different_pinned_certificate_cannot_connect() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("voice_host")
        val password = args.getString("voice_password")
        val pem = args.getString("voice_bad_pem")
        assumeTrue("Wrong-certificate LAN test is opt-in",
            !host.isNullOrBlank() && !password.isNullOrBlank() && !pem.isNullOrBlank())
        val app = compose.activity.application as OmniTAKApp
        assumeTrue(ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED)
        VoiceTrustStore(app).importCertificate(Base64.decode(pem, Base64.DEFAULT))
        val voice = app.voiceController
        try {
            compose.runOnUiThread {
                assertTrue(voice.connect(VoiceConfig(host!!, 64738, "omnitak-test", password!!)))
            }
            compose.waitUntil(20_000) { voice.status.value.label !in
                listOf("Voice disconnected", "Voice connecting") }
            assertTrue("Untrusted server connected", !voice.status.value.connected)
            voice.press()
            assertTrue("Untrusted server could transmit", !voice.status.value.transmitting)
        } finally {
            compose.runOnUiThread { voice.disconnect() }
        }
    }
}
