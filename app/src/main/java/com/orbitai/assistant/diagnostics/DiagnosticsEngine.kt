package com.orbitai.assistant.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.orbitai.assistant.ai.AiOrchestrator
import com.orbitai.assistant.ai.providers.ProviderId
import com.orbitai.assistant.memory.MemoryRepository
import com.orbitai.assistant.util.Prefs

data class DiagnosticItem(
    val name: String,
    val ok: Boolean,
    val detail: String,
    val fixHint: String = ""
)

class DiagnosticsEngine(private val context: Context) {

    suspend fun run(): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        val online = isOnline()
        items += DiagnosticItem(
            "Network",
            online,
            if (online) "Internet reachable" else "No active network",
            "Enable Wi‑Fi or mobile data"
        )

        val mic = has(Manifest.permission.RECORD_AUDIO)
        items += DiagnosticItem(
            "Microphone",
            mic,
            if (mic) "Granted" else "Missing",
            "Settings → Apps → ORBIT AI → Permissions → Microphone"
        )

        val pid = ProviderId.entries.firstOrNull { it.id == Prefs.aiProvider } ?: ProviderId.OPENAI
        val key = Prefs.keyFor(pid)
        items += DiagnosticItem(
            "Active AI provider",
            key.isNotBlank() || pid == ProviderId.OLLAMA,
            "${pid.displayName} · model ${Prefs.modelFor(pid)} · key ${if (key.isNotBlank()) "set" else "missing"}",
            "Open Settings and paste an API key. Groq model: llama-3.1-8b-instant"
        )

        val mem = MemoryRepository(context)
        val (mc, cc) = try { mem.stats() } catch (_: Exception) { 0 to 0 }
        items += DiagnosticItem("Memory store", true, "$mc memories · $cc chat messages", "")

        if (online && (key.isNotBlank() || pid == ProviderId.OLLAMA)) {
            val reply = AiOrchestrator().healthCheck(pid)
            items += DiagnosticItem(
                "AI backend health",
                reply.success,
                if (reply.success) "Responded in ${reply.latencyMs} ms" else (reply.error ?: "Failed"),
                "Check model name and API key"
            )
        }

        items += DiagnosticItem(
            "Prefer offline mode",
            !Prefs.offlineModePreferred,
            if (Prefs.offlineModePreferred) "ON — AI chat disabled" else "OFF",
            "Turn off in Settings to enable AI chat"
        )
        return items
    }

    fun restoreSafeDefaults(): List<String> {
        val changes = mutableListOf<String>()
        if (Prefs.offlineModePreferred) {
            Prefs.offlineModePreferred = false
            changes += "Turned off Prefer offline mode"
        }
        if (Prefs.aiProvider == "groq") {
            val m = Prefs.modelFor(ProviderId.GROQ)
            if (m.contains("3.3-70b") || m.isBlank()) {
                Prefs.setModelFor(ProviderId.GROQ, "llama-3.1-8b-instant")
                changes += "Set Groq model to llama-3.1-8b-instant"
            }
        }
        if (Prefs.aiModel.contains("3.3-70b")) {
            Prefs.aiModel = "llama-3.1-8b-instant"
            changes += "Updated default model string"
        }
        return changes
    }

    private fun has(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (_: Exception) {
            false
        }
    }
}
