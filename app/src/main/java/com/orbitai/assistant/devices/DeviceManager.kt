package com.orbitai.assistant.devices

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orbitai.assistant.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

data class OrbitDevice(
    val id: String,
    var name: String,
    var type: String,          // light, plug, fan, tv, computer, generic
    var address: String,       // IP, HA entity_id, etc.
    var status: String = "unknown",
    var protocol: String = "manual" // manual, homeassistant, etc.
)

/**
 * Modular device registry. Home Assistant integration is optional and requires
 * user-provided URL + long-lived access token. No automatic bypass of auth.
 */
class DeviceManager(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "devices.json")
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun listDevices(): List<OrbitDevice> = load()

    fun addManual(name: String, type: String, address: String) {
        val list = load().toMutableList()
        list.add(
            OrbitDevice(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                address = address,
                status = "manual",
                protocol = "manual"
            )
        )
        save(list)
    }

    fun scan(onDone: () -> Unit) {
        // Attempt Home Assistant entity fetch if configured
        val url = Prefs.haUrl.trimEnd('/')
        val token = Prefs.haToken
        if (url.isBlank() || token.isBlank()) {
            onDone()
            return
        }
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("$url/api/states")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    // Minimal parse: keep existing + mark HA reachable
                    val list = load().toMutableList()
                    val ha = list.firstOrNull { it.protocol == "homeassistant" }
                    if (ha == null) {
                        list.add(
                            OrbitDevice(
                                id = "ha-hub",
                                name = "Home Assistant",
                                type = "hub",
                                address = url,
                                status = "online",
                                protocol = "homeassistant"
                            )
                        )
                    } else {
                        ha.status = "online"
                    }
                    save(list)
                }
            } catch (_: Exception) {
            }
            // Note: full mDNS/SSDP discovery would need extra libraries; left as extension point
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
        }
    }

    fun turnOn(deviceId: String): String = control(deviceId, "on")
    fun turnOff(deviceId: String): String = control(deviceId, "off")

    private fun control(deviceId: String, action: String): String {
        val device = load().firstOrNull { it.id == deviceId }
            ?: return "Device not found"
        if (device.protocol == "homeassistant" && Prefs.haUrl.isNotBlank() && Prefs.haToken.isNotBlank()) {
            // Example service call structure – user must map entity_ids correctly
            return "Home Assistant $action requested for ${device.name}. Ensure entity mapping is configured."
        }
        return "No local control protocol for ${device.name} (${device.protocol}). Add a manufacturer plugin or use Home Assistant."
    }

    private fun load(): MutableList<OrbitDevice> {
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<OrbitDevice>>() {}.type
            gson.fromJson(file.readText(), type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(list: List<OrbitDevice>) {
        file.writeText(gson.toJson(list))
    }
}
