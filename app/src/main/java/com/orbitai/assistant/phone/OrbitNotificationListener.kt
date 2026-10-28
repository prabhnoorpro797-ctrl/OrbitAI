package com.orbitai.assistant.phone

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional notification access. User must enable in system settings.
 * Does not secretly monitor; only active when user grants access.
 */
class OrbitNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Extension point: surface summary to Orbit if user requests "read notifications"
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
