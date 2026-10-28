package com.orbitai.assistant.commands

/**
 * Parses natural language and AI ACTION lines into structured commands.
 * Offline path uses rule-based matching; online path can use AI-suggested actions.
 */
object CommandParser {

    enum class Type {
        OPEN_APP,
        OPEN_SETTINGS,
        OPEN_URL,
        SEARCH,
        CALL,
        MESSAGE,
        FLASHLIGHT,
        TIME,
        ROUTINE_RECORD_START,
        ROUTINE_RECORD_STOP,
        ROUTINE_PLAY,
        ROUTINE_LIST,
        SMART_HOME,
        WEB_SEARCH,
        HELP,
        UNKNOWN,
        CONVERSATION
    }

    data class Command(
        val type: Type,
        val payload: String = "",
        val raw: String = ""
    )

    private val appAliases = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "settings" to "com.android.settings",
        "camera" to "com.sec.android.app.camera",
        "gallery" to "com.sec.android.gallery3d",
        "messages" to "com.samsung.android.messaging",
        "phone" to "com.samsung.android.dialer",
        "contacts" to "com.samsung.android.app.contacts",
        "clock" to "com.sec.android.app.clockpackage",
        "calculator" to "com.sec.android.app.popupcalculator",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "play store" to "com.android.vending",
        "spotify" to "com.spotify.music",
        "whatsapp" to "com.whatsapp"
    )

    fun parseOffline(text: String): Command {
        val t = text.lowercase().trim()
        if (t.isBlank()) return Command(Type.UNKNOWN, raw = text)

        // Record / replay routines
        when {
            t.contains("record my steps") || t.contains("start recording") || t == "orbit record" ->
                return Command(Type.ROUTINE_RECORD_START, raw = text)
            t.contains("stop recording") || t.contains("stop record") ->
                return Command(Type.ROUTINE_RECORD_STOP, raw = text)
            t.startsWith("repeat") || t.contains("replay") || t.contains("run routine") || t.contains("play routine") -> {
                val name = t.replace(Regex("(repeat|replay|run routine|play routine|my steps|the routine)"), "")
                    .trim().ifBlank { "last" }
                return Command(Type.ROUTINE_PLAY, name, text)
            }
            t.contains("list routines") || t.contains("show routines") ->
                return Command(Type.ROUTINE_LIST, raw = text)
        }

        // Time
        if (t.contains("what time") || t.contains("current time") || t == "time") {
            return Command(Type.TIME, raw = text)
        }

        // Flashlight
        if (t.contains("flashlight") || t.contains("torch")) {
            val on = t.contains("on") || t.contains("enable") || t.contains("turn on")
            val off = t.contains("off") || t.contains("disable") || t.contains("turn off")
            return Command(Type.FLASHLIGHT, if (off) "off" else if (on) "on" else "toggle", text)
        }

        // Open app
        for ((alias, pkg) in appAliases) {
            if (t.contains("open $alias") || t.contains("launch $alias") || t == alias) {
                return Command(Type.OPEN_APP, pkg, text)
            }
        }
        if (t.startsWith("open ") || t.startsWith("launch ")) {
            val name = t.removePrefix("open ").removePrefix("launch ").trim()
            return Command(Type.OPEN_APP, name, text)
        }

        // Settings
        if (t.contains("wifi settings") || t.contains("wi-fi settings")) {
            return Command(Type.OPEN_SETTINGS, "wifi", text)
        }
        if (t.contains("bluetooth settings")) {
            return Command(Type.OPEN_SETTINGS, "bluetooth", text)
        }
        if (t.contains("open settings") || t == "settings") {
            return Command(Type.OPEN_SETTINGS, "main", text)
        }

        // Search / URL
        if (t.startsWith("search ") || t.startsWith("google ")) {
            val q = t.removePrefix("search ").removePrefix("google ").trim()
            return Command(Type.SEARCH, q, text)
        }
        if (t.startsWith("open url ") || t.startsWith("browse ") || t.startsWith("go to ")) {
            var url = t.removePrefix("open url ").removePrefix("browse ").removePrefix("go to ").trim()
            if (!url.startsWith("http")) url = "https://$url"
            return Command(Type.OPEN_URL, url, text)
        }

        // Call
        if (t.startsWith("call ") || t.startsWith("dial ")) {
            val target = t.removePrefix("call ").removePrefix("dial ").trim()
            return Command(Type.CALL, target, text)
        }

        // Message (prepare only)
        if (t.startsWith("message ") || t.startsWith("text ") || t.startsWith("sms ")) {
            return Command(Type.MESSAGE, t, text)
        }

        // Smart home keywords
        if (t.contains("turn on") || t.contains("turn off") || t.contains("lights") ||
            t.contains("plug") || t.contains("fan") || t.contains("routine")
        ) {
            if (t.contains("bedroom") || t.contains("living") || t.contains("light") ||
                t.contains("plug") || t.contains("fan") || t.contains("bedtime")
            ) {
                return Command(Type.SMART_HOME, t, text)
            }
        }

        // Help
        if (t == "help" || t.contains("what can you do")) {
            return Command(Type.HELP, raw = text)
        }

        return Command(Type.CONVERSATION, t, text)
    }

    /** Parse ACTION line returned by the AI model. */
    fun parseAiAction(aiText: String): Command? {
        val line = aiText.lines().firstOrNull { it.trim().startsWith("ACTION:", ignoreCase = true) }
            ?: return null
        val parts = line.substringAfter(":").split("|").map { it.trim() }
        if (parts.isEmpty()) return null
        val typeStr = parts[0].uppercase().replace(" ", "_")
        val payload = parts.getOrElse(1) { "" }
        val type = try {
            Type.valueOf(typeStr)
        } catch (_: Exception) {
            Type.UNKNOWN
        }
        return Command(type, payload, aiText)
    }
}
