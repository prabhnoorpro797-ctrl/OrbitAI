package com.orbitai.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.orbitai.assistant.util.Prefs

class OrbitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_VOICE,
                    "Orbit Voice",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Foreground service while Orbit is listening" },
                NotificationChannel(
                    CHANNEL_OVERLAY,
                    "Orbit Floating Assistant",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Floating bubble service" },
                NotificationChannel(
                    CHANNEL_AUTOMATION,
                    "Orbit Automation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Active automation indicator and emergency stop" }
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_VOICE = "orbit_voice"
        const val CHANNEL_OVERLAY = "orbit_overlay"
        const val CHANNEL_AUTOMATION = "orbit_automation"
    }
}
