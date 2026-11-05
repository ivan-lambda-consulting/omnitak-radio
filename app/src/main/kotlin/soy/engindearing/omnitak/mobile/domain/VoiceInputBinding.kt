package soy.engindearing.omnitak.mobile.domain

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent

data class VoiceInputBinding(val device: String, val keyCode: Int, val scanCode: Int) {
    fun matches(deviceName: String?, key: Int, scan: Int): Boolean =
        deviceName == device && key == keyCode && scan == scanCode

    val label get() = "$device · key $keyCode / scan $scanCode"
}

internal fun canLearnPtt(keyCode: Int, keyboardType: Int): Boolean =
    keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC && keyCode !in setOf(
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
    )

class VoiceInputBindingStore(context: Context) {
    private val prefs = context.getSharedPreferences("voice_input", Context.MODE_PRIVATE)
    fun load(): VoiceInputBinding? = prefs.getString("device", null)?.let { name ->
        VoiceInputBinding(name, prefs.getInt("key", 0), prefs.getInt("scan", 0))
    }

    fun save(binding: VoiceInputBinding): Boolean = prefs.edit()
        .putString("device", binding.device).putInt("key", binding.keyCode)
        .putInt("scan", binding.scanCode).commit()

    fun clear(): Boolean = prefs.edit().clear().commit()
}
