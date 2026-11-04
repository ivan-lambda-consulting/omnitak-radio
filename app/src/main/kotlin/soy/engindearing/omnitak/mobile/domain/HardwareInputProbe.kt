package soy.engindearing.omnitak.mobile.domain

/**
 * Restrict the debug-only input probe to knob-named devices. This does NOT
 * translate keys or assign channel/PTT actions; those bindings will be
 * user-configurable. In particular, never log keypad/IME text entry.
 */
internal object HardwareInputProbe {
    fun isKnobDevice(name: String?): Boolean = name?.startsWith("knob_", ignoreCase = true) == true
}
