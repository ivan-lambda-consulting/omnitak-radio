package soy.engindearing.omnitak.mobile.domain

import android.content.Context

data class VoiceInputBinding(val device: String, val keyCode: Int, val scanCode: Int) {
    fun matches(deviceName: String?, key: Int, scan: Int): Boolean =
        deviceName == device && key == keyCode && scan == scanCode

    val label get() = "$device · key $keyCode / scan $scanCode"
}

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
