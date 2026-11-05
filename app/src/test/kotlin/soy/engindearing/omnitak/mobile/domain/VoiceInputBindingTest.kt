package soy.engindearing.omnitak.mobile.domain

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
}
