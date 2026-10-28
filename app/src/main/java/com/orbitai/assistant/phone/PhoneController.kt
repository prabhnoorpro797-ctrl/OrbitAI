package com.orbitai.assistant.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.orbitai.assistant.util.Prefs

/**
 * Executes supported phone actions via official Intents and APIs.
 * Always respects runtime permissions; never bypasses security.
 */
class PhoneController(private val context: Context) {

    private var torchOn = false

    fun openApp(packageOrName: String): String {
        val pm = context.packageManager
        // Try as package name first
        var intent = pm.getLaunchIntentForPackage(packageOrName)
        if (intent == null) {
            // Search by label
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val match = apps.firstOrNull {
                it.loadLabel(pm).toString().equals(packageOrName, ignoreCase = true) ||
                        it.activityInfo.packageName.contains(packageOrName, ignoreCase = true)
            }
            if (match != null) {
                intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            }
        }
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $packageOrName"
        } else {
            "I could not find an app matching \"$packageOrName\"."
        }
    }

    fun openSettings(page: String): String {
        val intent = when (page.lowercase()) {
            "wifi", "wi-fi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "overlay" -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Opening $page settings"
    }

    fun openUrl(url: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $url"
        } catch (e: Exception) {
            "Could not open URL: ${e.message}"
        }
    }

    fun searchWeb(query: String): String {
        val base = Prefs.searchEngine.ifBlank { "https://www.google.com/search?q=" }
        val url = base + Uri.encode(query)
        return openUrl(url)
    }

    fun startCall(target: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Call permission is required. Please grant it in Settings."
        }
        // If it looks like a number, dial; otherwise try contact lookup (simplified)
        val uri = if (target.matches(Regex("^[+\\d\\s\\-()]+$"))) {
            Uri.parse("tel:${target.replace(" ", "")}")
        } else {
            // Simplified: open dialer with name as hint
            Uri.parse("tel:")
        }
        val intent = Intent(Intent.ACTION_CALL, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Calling $target"
        } catch (e: Exception) {
            // Fallback to dialer
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${target.replace(" ", "")}"))
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dial)
            "Opening dialer for $target"
        }
    }

    fun prepareMessage(raw: String): String {
        // Parse roughly: "message John hello" or "text +1234 hi"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
            putExtra("sms_body", raw)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opening messages. Please review and send manually for safety."
    }

    fun toggleFlashlight(mode: String): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight available on this device."
            when (mode) {
                "on" -> {
                    cameraManager.setTorchMode(cameraId, true)
                    torchOn = true
                    "Flashlight on"
                }
                "off" -> {
                    cameraManager.setTorchMode(cameraId, false)
                    torchOn = false
                    "Flashlight off"
                }
                else -> {
                    torchOn = !torchOn
                    cameraManager.setTorchMode(cameraId, torchOn)
                    if (torchOn) "Flashlight on" else "Flashlight off"
                }
            }
        } catch (e: Exception) {
            "Flashlight error: ${e.message}. Camera permission may be required."
        }
    }

    fun speakTime(): String {
        val now = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "The time is $now"
    }
}
