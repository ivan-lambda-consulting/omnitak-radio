package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConfigTest {
    @Test fun voice_endpoint_requires_a_real_host_port_and_username() {
        assertTrue(VoiceConfig("192.168.1.131", 64738, "operator").isValid())
        assertFalse(VoiceConfig("", 64738, "operator").isValid())
        assertFalse(VoiceConfig("host name", 64738, "operator").isValid())
        assertFalse(VoiceConfig("localhost", 0, "operator").isValid())
        assertFalse(VoiceConfig("localhost", 65536, "operator").isValid())
        assertFalse(VoiceConfig("localhost", 64738, " ").isValid())
    }
}
