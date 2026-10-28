package com.orbitai.assistant.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.orbitai.assistant.util.Prefs

/**
 * Phone actions via official Intents. Works offline — no API key needed.
 */
class PhoneController(private val context: Context) {

    private var torchOn = false

    private val knownPackages = mapOf(
        "youtube" to listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"),
        "yt" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.opera.browser"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "store" to listOf("com.android.vending"),
        "settings" to listOf("com.android.settings"),
        "camera" to listOf("com.sec.android.app.camera", "com.android.camera2", "com.android.camera"),
        "gallery" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
        "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
        "messages" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "message" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "sms" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "phone" to listOf("com.samsung.android.dialer", "com.google.android.dialer"),
        "dialer" to listOf("com.samsung.android.dialer", "com.google.android.dialer"),
        "contacts" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
        "clock" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
        "calculator" to listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
        "maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm"),
        "mail" to listOf("com.google.android.gm"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "spotify" to listOf("com.spotify.music"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "telegram" to listOf("org.telegram.messenger"),
        "files" to listOf("com.sec.android.app.myfiles", "com.google.android.apps.nbu.files"),
        "my files" to listOf("com.sec.android.app.myfiles"),
        "calendar" to listOf("com.samsung.android.calendar", "com.google.android.calendar"),
        "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser")
    )

    fun openApp(packageOrName: String): String {
        val raw = packageOrName.trim()
        if (raw.isBlank()) return "Which app should I open?"

        val pm = context.packageManager

        // 1) Known alias → try package list
        val key = raw.lowercase()
        val candidates = knownPackages[key] ?: knownPackages.entries
            .firstOrNull { key.contains(it.key) || it.key.contains(key) }
            ?.value
        if (candidates != null) {
            for (pkg in candidates) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opening ${prettyName(key)}"
                }
            }
        }

        // 2) Exact package name
        pm.getLaunchIntentForPackage(raw)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
            return "Opening $raw"
        }

        // 3) Fuzzy match installed app labels
        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
        val needle = raw.lowercase()
        val match = apps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label == needle || label.contains(needle) || needle.contains(label)
        } ?: apps.firstOrNull {
            it.activityInfo.packageName.lowercase().contains(needle.replace(" ", ""))
        }

        return if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "Opening ${match.loadLabel(pm)}"
            } else {
                "Found ${match.loadLabel(pm)} but could not launch it."
            }
        } else {
            // Fallback: open Play Store search for that app
            searchPlayStore(raw)
            "App \"$raw\" not installed. Searching Play Store…"
        }
    }

    /** Search Play Store for an app name. */
    fun searchPlayStore(query: String): String {
        val q = query.trim()
        return try {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(q)}"))
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            market.setPackage("com.android.vending")
            context.startActivity(market)
            "Searching Play Store for \"$q\""
        } catch (_: Exception) {
            openUrl("https://play.google.com/store/search?q=${Uri.encode(q)}")
            "Opening Play Store search for \"$q\""
        }
    }

    /** Search or open a YouTube video/query. */
    fun searchYouTube(query: String): String {
        val q = query.trim()
        return try {
            // Prefer official YouTube app search intent
            val yt = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(yt)
            "Searching YouTube for \"$q\""
        } catch (_: Exception) {
            try {
                val view = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(q)}")
                )
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(view)
                "Opening YouTube search for \"$q\""
            } catch (e: Exception) {
                "Could not open YouTube: ${e.message}"
            }
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
        return openUrl(base + Uri.encode(query))
    }

    fun startCall(target: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Call permission is required. Grant it in Settings."
        }
        val number = target.replace(" ", "")
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Calling $target"
        } catch (_: Exception) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dial)
            "Opening dialer for $target"
        }
    }

    fun prepareMessage(raw: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
            putExtra("sms_body", raw)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Opening messages. Review and send yourself."
    }

    fun toggleFlashlight(mode: String): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight on this device."
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
            "Flashlight error: ${e.message}"
        }
    }

    fun speakTime(): String {
        val now = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "The time is $now"
    }

    /**
     * Open WhatsApp chat. Payload can be:
     * - phone number: +919876543210 or 9876543210
     * - "Name" or "Name hello there" (looks up contact, rest is message)
     *
     * WhatsApp does not allow apps to start a voice/video call silently.
     * We open the chat; for "call" we open chat and guide the user to tap Call.
     */
    fun whatsappChat(payload: String): String {
        if (!isWhatsAppInstalled()) {
            searchPlayStore("WhatsApp")
            return "WhatsApp is not installed. Opening Play Store."
        }
        val (number, message) = resolveWhatsAppTarget(payload)
        if (number.isNullOrBlank()) {
            // Open WhatsApp main
            return openApp("whatsapp")
        }
        return try {
            val digits = number.filter { it.isDigit() }
            val uri = if (message.isNullOrBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$digits")
            } else {
                Uri.parse(
                    "https://api.whatsapp.com/send?phone=$digits&text=${Uri.encode(message)}"
                )
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (message.isNullOrBlank()) "Opening WhatsApp chat with $number"
            else "Opening WhatsApp chat with $number"
        } catch (e: Exception) {
            // Business WhatsApp fallback
            try {
                val digits = number.filter { it.isDigit() }
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://api.whatsapp.com/send?phone=$digits")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                "Opening WhatsApp for $number"
            } catch (e2: Exception) {
                "Could not open WhatsApp: ${e2.message}"
            }
        }
    }

    /**
     * "Call on WhatsApp": opens the chat so you can tap the voice/video call button.
     * Android does not allow third-party apps to start a WhatsApp call automatically.
     */
    fun whatsappCall(payload: String): String {
        if (payload.isBlank()) {
            openApp("whatsapp")
            return "Opening WhatsApp. Pick a chat, then tap the phone icon to call."
        }
        val result = whatsappChat(payload)
        return if (result.startsWith("Opening WhatsApp chat")) {
            "$result Tap the phone icon at the top to start a WhatsApp call."
        } else {
            result
        }
    }

    private fun isWhatsAppInstalled(): Boolean {
        val pm = context.packageManager
        return listOf("com.whatsapp", "com.whatsapp.w4b").any {
            try {
                pm.getPackageInfo(it, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Returns pair of E.164-ish number and optional message text.
     */
    private fun resolveWhatsAppTarget(payload: String): Pair<String?, String?> {
        val p = payload.trim()
        if (p.isBlank()) return null to null

        // Pure number (with optional + and spaces)
        val asNumber = p.replace(" ", "")
        if (asNumber.matches(Regex("^\\+?\\d{7,15}$"))) {
            return asNumber to null
        }

        // "Name message text..." — first token(s) may be contact
        // Try full string as contact name first
        lookupContactNumber(p)?.let { return it to null }

        // Split: first word = name, rest = message
        val parts = p.split(" ", limit = 2)
        val name = parts[0]
        val msg = parts.getOrNull(1)
        lookupContactNumber(name)?.let { return it to msg }

        // Two-word name
        if (parts.size == 1) {
            // maybe number mixed
            return null to null
        }
        val two = p.split(" ", limit = 3)
        if (two.size >= 2) {
            val fullName = "${two[0]} ${two[1]}"
            val rest = two.getOrNull(2)
            lookupContactNumber(fullName)?.let { return it to rest }
        }

        // Fallback: treat whole thing as unknown — open wa.me won't work without digits
        return null to p
    }

    private fun lookupContactNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val needle = name.trim()
        if (needle.isBlank()) return null
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$needle%"),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(0)?.replace(" ", "")?.replace("-", "")
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun prettyName(key: String) = key.replaceFirstChar { it.uppercase() }
}
