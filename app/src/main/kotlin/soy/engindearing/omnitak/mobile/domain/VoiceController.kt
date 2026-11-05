package soy.engindearing.omnitak.mobile.domain

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.lublin.humla.Constants
import se.lublin.humla.HumlaService
import se.lublin.humla.IHumlaService
import se.lublin.humla.model.Server
import se.lublin.humla.util.HumlaException
import se.lublin.humla.util.HumlaObserver
import java.security.cert.X509Certificate

data class VoiceStatus(val label: String = "Voice disconnected", val connected: Boolean = false,
    val transmitting: Boolean = false)

/** One app-owned session: only explicit Connect starts the microphone service. */
class VoiceController(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val gate = VoiceTransmitGate()
    private val inputStore = VoiceInputBindingStore(app)
    private var inputBinding = inputStore.load()
    private var learning = false
    private val _inputStatus = MutableStateFlow(inputBinding?.label ?: "PTT key not bound")
    val inputStatus = _inputStatus.asStateFlow()
    private val _status = MutableStateFlow(VoiceStatus())
    val status = _status.asStateFlow()
    private var session: IHumlaService? = null
    private var bound = false
    private var serverLabel = ""
    private var trustFailed = false
    private val timeout = Runnable { release() }

    private val observer = object : HumlaObserver() {
        override fun onConnected() {
            gate.connected()
            _status.value = VoiceStatus("Voice connected: $serverLabel", connected = true)
        }

        override fun onConnecting() {
            _status.value = VoiceStatus("Voice connecting")
        }

        override fun onTLSHandshakeFailed(chain: Array<out X509Certificate>?) {
            trustFailed = true
            _status.value = VoiceStatus("Voice certificate not trusted; import its PEM")
        }

        override fun onDisconnected(e: HumlaException?) {
            gate.disconnected()
            handler.removeCallbacks(timeout)
            _status.value = VoiceStatus(if (trustFailed) "Voice certificate not trusted; import its PEM"
                else "Voice disconnected${e?.let { ": ${it.message}" } ?: ""}")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            session = (binder as HumlaService.HumlaBinder).service.also {
                it.registerObserver(observer)
                if (it.isConnected) observer.onConnected()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            session = null
            gate.disconnected()
            handler.removeCallbacks(timeout)
            _status.value = VoiceStatus("Voice service stopped")
        }
    }

    fun connect(config: VoiceConfig): Boolean {
        if (!config.isValid() || ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            _status.value = VoiceStatus("Enter a voice server and grant microphone access")
            return false
        }
        val trustStore = VoiceTrustStore(app).path ?: run {
            _status.value = VoiceStatus("Import the voice server certificate before connecting")
            return false
        }
        if (bound) disconnect()
        trustFailed = false
        serverLabel = config.host
        val intent = Intent(app, VoiceService::class.java).apply {
            action = HumlaService.ACTION_CONNECT
            putExtra(HumlaService.EXTRAS_SERVER,
                Server(0, "Voice", config.host, config.port, config.username, config.password))
            putExtra(HumlaService.EXTRAS_CLIENT_NAME, "OmniTAK Radio")
            putExtra(HumlaService.EXTRAS_TRANSMIT_MODE, Constants.TRANSMIT_PUSH_TO_TALK)
            putExtra(HumlaService.EXTRAS_USE_OPUS, true)
            putExtra(HumlaService.EXTRAS_INPUT_RATE, 48_000)
            putExtra(HumlaService.EXTRAS_INPUT_QUALITY, 40_000)
            putExtra(HumlaService.EXTRAS_FRAMES_PER_PACKET, 2)
            putExtra(HumlaService.EXTRAS_HALF_DUPLEX, true)
            putExtra(HumlaService.EXTRAS_ECHO_CANCELLATION_METHOD, "system")
            putExtra(HumlaService.EXTRAS_ACCESS_TOKENS, arrayListOf<String>())
            putExtra(HumlaService.EXTRAS_LOCAL_MUTE_HISTORY, arrayListOf<Int>())
            putExtra(HumlaService.EXTRAS_LOCAL_IGNORE_HISTORY, arrayListOf<Int>())
            putExtra(HumlaService.EXTRAS_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            putExtra(HumlaService.EXTRAS_AUDIO_STREAM, AudioManager.STREAM_VOICE_CALL)
            putExtra(HumlaService.EXTRAS_TRUST_STORE, trustStore)
            putExtra(HumlaService.EXTRAS_TRUST_STORE_PASSWORD, "")
            putExtra(HumlaService.EXTRAS_TRUST_STORE_FORMAT, "PKCS12")
        }
        return try {
            bound = app.bindService(Intent(app, VoiceService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) throw IllegalStateException("Voice service unavailable")
            ContextCompat.startForegroundService(app, intent)
            _status.value = VoiceStatus("Voice connecting")
            true
        } catch (e: RuntimeException) {
            disconnect()
            _status.value = VoiceStatus("Voice could not start: ${e.message}")
            false
        }
    }

    fun press() {
        val voice = session ?: return
        if (!voice.isConnected || !gate.press()) return
        try {
            voice.HumlaSession().setTalkingState(true)
            _status.value = VoiceStatus("Transmitting", connected = true, transmitting = true)
            // shortcut: cap one press at 30 seconds until screen-off key-up is verified.
            handler.postDelayed(timeout, 30_000)
        } catch (e: RuntimeException) {
            gate.release()
            _status.value = VoiceStatus("Voice transmit failed: ${e.message}", connected = true)
        }
    }

    fun release() {
        if (!gate.release()) return
        handler.removeCallbacks(timeout)
        runCatching { session?.takeIf { it.isConnected }?.HumlaSession()?.setTalkingState(false) }
        _status.value = VoiceStatus("Voice connected: $serverLabel", connected = session?.isConnected == true)
    }

    fun disconnect() {
        release()
        gate.disconnected()
        session?.unregisterObserver(observer)
        session?.disconnect()
        session = null
        if (bound) {
            app.unbindService(connection)
            bound = false
        }
        app.stopService(Intent(app, VoiceService::class.java))
        _status.value = VoiceStatus()
    }

    fun learnPtt() {
        release()
        learning = true
        _inputStatus.value = "Press a hardware PTT key"
    }

    fun cancelLearning() {
        if (!learning) return
        learning = false
        _inputStatus.value = inputBinding?.label ?: "PTT key not bound"
    }

    fun resetPtt() {
        cancelLearning()
        if (inputStore.clear()) {
            inputBinding = null
            _inputStatus.value = "PTT key not bound"
        }
    }

    fun handleHardwareKey(event: KeyEvent): Boolean {
        if (learning && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                cancelLearning()
                return true
            }
            val name = event.device?.name ?: return false
            if (!canLearnPtt(event.keyCode, event.device?.keyboardType ?: return false)) return false
            val learned = VoiceInputBinding(name, event.keyCode, event.scanCode)
            if (inputStore.save(learned)) {
                inputBinding = learned
                learning = false
                _inputStatus.value = learned.label
                return true
            }
        }
        if (_status.value.connected && inputBinding?.matches(event.device?.name,
                event.keyCode, event.scanCode) == true) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) press()
                KeyEvent.ACTION_UP -> release()
            }
            return true
        }
        return false
    }

}
