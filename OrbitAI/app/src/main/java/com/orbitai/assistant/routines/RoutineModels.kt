package com.orbitai.assistant.routines

import com.google.gson.annotations.SerializedName

/**
 * Supported recorded actions. Coordinate taps are avoided when possible;
 * package + activity + view text / content-desc are preferred.
 */
data class RecordedAction(
    val type: String,               // TAP, SCROLL, TEXT, OPEN_APP, WAIT, CONFIRM
    val packageName: String? = null,
    val activity: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val x: Float? = null,           // fallback only
    val y: Float? = null,
    val scrollDirection: String? = null, // UP / DOWN
    val waitMs: Long? = null,
    val requiresConfirmation: Boolean = false,
    val note: String? = null
)

data class Routine(
    val id: String,
    var name: String,
    val createdAt: Long,
    var actions: MutableList<RecordedAction> = mutableListOf(),
    var lastRunAt: Long? = null
)

data class RoutineBackup(
    @SerializedName("schema_version") val schemaVersion: Int = 1,
    val routines: List<Routine>,
    val exportedAt: Long = System.currentTimeMillis()
)
