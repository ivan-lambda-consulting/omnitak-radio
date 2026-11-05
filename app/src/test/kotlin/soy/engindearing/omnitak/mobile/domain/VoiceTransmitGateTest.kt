package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTransmitGateTest {
    @Test fun only_a_connected_new_press_transmits() {
        val gate = VoiceTransmitGate()
        assertFalse(gate.press())
        gate.connected()
        assertTrue(gate.press())
        assertFalse(gate.press())
        assertTrue(gate.release())
        assertFalse(gate.release())
    }

    @Test fun disconnect_clears_transmit_and_requires_a_new_press() {
        val gate = VoiceTransmitGate()
        gate.connected()
        assertTrue(gate.press())
        assertTrue(gate.disconnected())
        assertFalse(gate.press())
        assertFalse(gate.release())
        gate.connected()
        assertTrue(gate.press())
    }
}
