package com.orbitai.assistant.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import com.orbitai.assistant.OrbitApp
import com.orbitai.assistant.R
import com.orbitai.assistant.ui.MainActivity

/**
 * Optional floating bubble. Requires SYSTEM_ALERT_WINDOW permission granted by user.
 * Does not implement hidden overlays.
 */
class FloatingAssistantService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(42, buildNotification())
        showBubble()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, OrbitApp.CHANNEL_OVERLAY)
            .setContentTitle("ORBIT AI")
            .setContentText("Floating assistant active")
            .setSmallIcon(R.drawable.ic_orbit)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        view.findViewById<ImageButton>(R.id.bubbleMic).setOnClickListener {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        view.findViewById<ImageButton>(R.id.bubbleClose).setOnClickListener {
            stopSelf()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }
        windowManager?.addView(view, params)
        bubbleView = view
    }

    override fun onDestroy() {
        bubbleView?.let { windowManager?.removeView(it) }
        bubbleView = null
        super.onDestroy()
    }
}
