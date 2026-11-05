package soy.engindearing.omnitak.mobile.domain

/** Never transmit from an unfocused or disconnected key press. */
internal class VoiceTransmitGate {
    private var connected = false
    private var held = false

    fun connected() { connected = true }
    fun press(): Boolean {
        if (!connected || held) return false
        held = true
        return true
    }
    fun release(): Boolean {
        if (!held) return false
        held = false
        return true
    }
    fun disconnected(): Boolean {
        connected = false
        return release()
    }
}
