package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debug logs must not collect keypad/IME input or accidentally assign a radio action. */
class HardwareInputProbeTest {
    @Test fun only_knob_named_input_devices_are_logged() {
        assertTrue(HardwareInputProbe.isKnobDevice("knob_cw"))
        assertTrue(HardwareInputProbe.isKnobDevice("knob_ccw"))
        assertTrue(HardwareInputProbe.isKnobDevice("knob_ptt"))
        assertFalse(HardwareInputProbe.isKnobDevice("soc:matrix_keypad@0"))
        assertFalse(HardwareInputProbe.isKnobDevice("Virtual"))
        assertFalse(HardwareInputProbe.isKnobDevice(null))
    }
}
