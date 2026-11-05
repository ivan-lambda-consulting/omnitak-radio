package soy.engindearing.omnitak.mobile.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import se.lublin.humla.HumlaService
import se.lublin.humla.util.HumlaException
import soy.engindearing.omnitak.mobile.MainActivity
import soy.engindearing.omnitak.mobile.R

/** The audio session owns a microphone foreground service, separate from TAK data/location. */
class VoiceService : HumlaService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CONNECT) {
            try {
                val notification = notification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(VOICE_NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(VOICE_NOTIFICATION_ID, notification)
                }
            } catch (e: RuntimeException) {
                Log.w("VoiceService", "Microphone foreground start refused", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onConnectionDisconnected(e: HumlaException?) {
        super.onConnectionDisconnected(e)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "voice", "Voice communication", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "voice")
            .setSmallIcon(R.mipmap.app_icon)
            .setContentTitle("OmniTAK voice active")
            .setContentText("Microphone available for push-to-talk")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object { private const val VOICE_NOTIFICATION_ID = 1003 }
}
