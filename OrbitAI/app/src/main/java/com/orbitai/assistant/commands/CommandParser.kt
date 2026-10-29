package com.orbitai.assistant.commands

/**
 * Offline-first command parser. Most phone actions work with NO API key.
 */
object CommandParser {

    enum class Type {
        OPEN_APP,
        OPEN_SETTINGS,
        OPEN_URL,
        SEARCH,
        SEARCH_YOUTUBE,
        SEARCH_PLAYSTORE,
        CALL,
        MESSAGE,
        WHATSAPP_CALL,
        WHATSAPP_CHAT,
        FLASHLIGHT,
        TIME,
        BATTERY,
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
        "youtube" to "youtube",
        "yt" to "youtube",
        "chrome" to "chrome",
        "play store" to "play store",
        "playstore" to "play store",
        "settings" to "settings",
        "camera" to "camera",
        "gallery" to "gallery",
        "photos" to "photos",
        "messages" to "messages",
        "phone" to "phone",
        "contacts" to "contacts",
        "clock" to "clock",
        "calculator" to "calculator",
        "maps" to "maps",
        "gmail" to "gmail",
        "whatsapp" to "whatsapp",
        "instagram" to "instagram",
        "facebook" to "facebook",
        "spotify" to "spotify",
        "netflix" to "netflix",
        "telegram" to "telegram",
        "files" to "files",
        "browser" to "browser"
    )

    fun parseOffline(text: String): Command {
        val t = text.lowercase().trim()
        if (t.isBlank()) return Command(Type.UNKNOWN, raw = text)

        // Routines
        when {
            t.contains("record my steps") || t.contains("start recording") ->
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

        // Battery
        if (t.contains("battery") || t.contains("charge level") || t.contains("how much charge")) {
            return Command(Type.BATTERY, raw = text)
        }

        // Remember
        if (t.startsWith("remember that ") || t.startsWith("remember ")) {
            val body = t.removePrefix("remember that ").removePrefix("remember ").trim()
            return Command(Type.CONVERSATION, "REMEMBER|$body", text)
        }

        // Flashlight
        if (t.contains("flashlight") || t.contains("torch")) {
            val on = t.contains("on") || t.contains("enable") || t.contains("turn on")
            val off = t.contains("off") || t.contains("disable") || t.contains("turn off")
            return Command(Type.FLASHLIGHT, if (off) "off" else if (on) "on" else "toggle", text)
        }

        // YouTube search — "youtube <query>", "search youtube <q>", "play on youtube <q>", "yt <q>"
        when {
            t.startsWith("youtube ") ->
                return Command(Type.SEARCH_YOUTUBE, t.removePrefix("youtube ").trim(), text)
            t.startsWith("yt ") ->
                return Command(Type.SEARCH_YOUTUBE, t.removePrefix("yt ").trim(), text)
            t.startsWith("search youtube ") ->
                return Command(Type.SEARCH_YOUTUBE, t.removePrefix("search youtube ").trim(), text)
            t.startsWith("play on youtube ") ->
                return Command(Type.SEARCH_YOUTUBE, t.removePrefix("play on youtube ").trim(), text)
            t.contains(" on youtube") -> {
                val q = t.replace(" on youtube", "").replace("search ", "").replace("play ", "").trim()
                if (q.isNotBlank()) return Command(Type.SEARCH_YOUTUBE, q, text)
            }
        }

        // Play Store search — "play store <app>", "search play store <app>", "find app <name>"
        when {
            t.startsWith("play store ") || t.startsWith("playstore ") -> {
                val q = t.removePrefix("play store ").removePrefix("playstore ").trim()
                return Command(Type.SEARCH_PLAYSTORE, q, text)
            }
            t.startsWith("search play store ") || t.startsWith("search playstore ") -> {
                val q = t.removePrefix("search play store ").removePrefix("search playstore ").trim()
                return Command(Type.SEARCH_PLAYSTORE, q, text)
            }
            t.startsWith("find app ") || t.startsWith("search app ") -> {
                val q = t.removePrefix("find app ").removePrefix("search app ").trim()
                return Command(Type.SEARCH_PLAYSTORE, q, text)
            }
            t.startsWith("install ") -> {
                val q = t.removePrefix("install ").trim()
                return Command(Type.SEARCH_PLAYSTORE, q, text)
            }
        }

        // Open app by alias
        for ((alias, name) in appAliases) {
            if (t == "open $alias" || t == "launch $alias" || t == alias ||
                t == "open the $alias" || t == "launch the $alias"
            ) {
                return Command(Type.OPEN_APP, name, text)
            }
        }
        if (t.startsWith("open ") || t.startsWith("launch ")) {
            val name = t.removePrefix("open ").removePrefix("launch ").removePrefix("the ").trim()
            // Avoid treating "open youtube funny cats" as open-app
            if (name.startsWith("youtube ")) {
                return Command(Type.SEARCH_YOUTUBE, name.removePrefix("youtube ").trim(), text)
            }
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

        // Web search
        if (t.startsWith("search ") || t.startsWith("google ")) {
            val q = t.removePrefix("search ").removePrefix("google ").trim()
            return Command(Type.SEARCH, q, text)
        }
        if (t.startsWith("open url ") || t.startsWith("browse ") || t.startsWith("go to ")) {
            var url = t.removePrefix("open url ").removePrefix("browse ").removePrefix("go to ").trim()
            if (!url.startsWith("http")) url = "https://$url"
            return Command(Type.OPEN_URL, url, text)
        }

        // WhatsApp call / chat
        // "whatsapp call mom", "call mom on whatsapp", "wa call +9198...", "whatsapp message john hi"
        when {
            t.startsWith("whatsapp call ") || t.startsWith("wa call ") -> {
                val target = t.removePrefix("whatsapp call ").removePrefix("wa call ").trim()
                return Command(Type.WHATSAPP_CALL, target, text)
            }
            t.startsWith("call ") && t.contains(" on whatsapp") -> {
                val target = t.removePrefix("call ").substringBefore(" on whatsapp").trim()
                return Command(Type.WHATSAPP_CALL, target, text)
            }
            t.startsWith("whatsapp call") || t == "wa call" ->
                return Command(Type.WHATSAPP_CALL, "", text)
            t.startsWith("whatsapp message ") || t.startsWith("wa message ") ||
                    t.startsWith("whatsapp chat ") || t.startsWith("message on whatsapp ") -> {
                val body = t.removePrefix("whatsapp message ")
                    .removePrefix("wa message ")
                    .removePrefix("whatsapp chat ")
                    .removePrefix("message on whatsapp ")
                    .trim()
                return Command(Type.WHATSAPP_CHAT, body, text)
            }
            t.startsWith("whatsapp ") && !t.startsWith("whatsapp call") -> {
                // "whatsapp mom" or "whatsapp +91... hello"
                return Command(Type.WHATSAPP_CHAT, t.removePrefix("whatsapp ").trim(), text)
            }
        }

        // Normal phone call / SMS
        if (t.startsWith("call ") || t.startsWith("dial ")) {
            return Command(Type.CALL, t.removePrefix("call ").removePrefix("dial ").trim(), text)
        }
        if (t.startsWith("message ") || t.startsWith("text ") || t.startsWith("sms ")) {
            return Command(Type.MESSAGE, t, text)
        }

        // Smart home keywords
        if ((t.contains("turn on") || t.contains("turn off")) &&
            (t.contains("light") || t.contains("plug") || t.contains("fan"))
        ) {
            return Command(Type.SMART_HOME, t, text)
        }

        if (t == "help" || t.contains("what can you do")) {
            return Command(Type.HELP, raw = text)
        }

        return Command(Type.CONVERSATION, t, text)
    }

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
