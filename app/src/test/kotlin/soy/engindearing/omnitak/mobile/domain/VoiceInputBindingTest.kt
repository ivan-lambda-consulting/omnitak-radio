package soy.engindearing.omnitak.mobile.domain

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputBindingTest {
    @Test fun input_binding_matches_only_the_learned_device_and_key() {
        val binding = VoiceInputBinding("external-key", 701, 48)
        assertTrue(binding.matches("external-key", 701, 48))
        assertFalse(binding.matches("other-key", 701, 48))
        assertFalse(binding.matches("external-key", 702, 48))
        assertFalse(binding.matches("external-key", 701, 49))
    }

    @Test fun navigation_and_alphabetic_keys_cannot_be_learned_as_ptt() {
        assertFalse(canLearnPtt(KeyEvent.KEYCODE_DPAD_CENTER,
            InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC))
        assertFalse(canLearnPtt(KeyEvent.KEYCODE_BUTTON_1,
            InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        assertTrue(canLearnPtt(KeyEvent.KEYCODE_BUTTON_1,
            InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC))
    }
}
