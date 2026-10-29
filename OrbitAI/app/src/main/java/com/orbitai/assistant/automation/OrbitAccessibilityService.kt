package com.orbitai.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.orbitai.assistant.routines.RecordedAction
import com.orbitai.assistant.routines.Routine
import com.orbitai.assistant.routines.RoutineRepository
import kotlinx.coroutines.*

/**
 * Optional AccessibilityService for limited, user-approved screen automation
 * and the "Copy My Steps" recorder.
 *
 * Limitations (honest status):
 * - Recording captures OPEN_APP (from window changes) and basic TAP by text/content-desc when possible.
 * - Coordinate taps are fallback only.
 * - Full reliable replay of arbitrary UI is not guaranteed across apps / OS updates.
 * - Sensitive actions require confirmation.
 * - User must enable the service manually in Accessibility settings.
 */
class OrbitAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recording = false
    private val recorded = mutableListOf<RecordedAction>()
    private var currentPackage: String? = null
    private var replayJob: Job? = null
    @Volatile var emergencyStop = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (recording) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    val pkg = event.packageName?.toString()
                    val cls = event.className?.toString()
                    if (pkg != null && pkg != currentPackage) {
                        currentPackage = pkg
                        recorded.add(
                            RecordedAction(
                                type = "OPEN_APP",
                                packageName = pkg,
                                activity = cls,
                                note = "Window changed"
                            )
                        )
                    }
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    val source = event.source ?: return
                    val text = source.text?.toString()
                    val desc = source.contentDescription?.toString()
                    val viewId = source.viewIdResourceName
                    recorded.add(
                        RecordedAction(
                            type = "TAP",
                            packageName = event.packageName?.toString(),
                            viewId = viewId,
                            text = text,
                            contentDescription = desc,
                            requiresConfirmation = isSensitive(text, desc)
                        )
                    )
                    source.recycle()
                }
            }
        }
    }

    override fun onInterrupt() {
        emergencyStop = true
        stopRecording()
        cancelReplay()
    }

    fun startRecording() {
        recorded.clear()
        recording = true
        emergencyStop = false
    }

    fun stopRecording(): List<RecordedAction> {
        recording = false
        return recorded.toList()
    }

    fun isRecording(): Boolean = recording

    fun cancelReplay() {
        emergencyStop = true
        replayJob?.cancel()
        replayJob = null
    }

    fun replay(routine: Routine, onProgress: (String) -> Unit, onFinished: (Boolean, String) -> Unit) {
        cancelReplay()
        emergencyStop = false
        replayJob = scope.launch {
            try {
                for ((index, action) in routine.actions.withIndex()) {
                    if (emergencyStop) {
                        onFinished(false, "Stopped by user")
                        return@launch
                    }
                    onProgress("Step ${index + 1}/${routine.actions.size}: ${action.type}")
                    when (action.type) {
                        "OPEN_APP" -> {
                            action.packageName?.let { pkg ->
                                val intent = packageManager.getLaunchIntentForPackage(pkg)
                                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                intent?.let { startActivity(it) }
                            }
                            delay(800)
                        }
                        "TAP" -> {
                            val root = rootInActiveWindow
                            if (root != null) {
                                val node = findNode(root, action)
                                if (node != null) {
                                    if (action.requiresConfirmation) {
                                        onProgress("Sensitive action – confirm in Orbit UI")
                                        // Caller should handle confirmation; for now we skip
                                        node.recycle()
                                        continue
                                    }
                                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    node.recycle()
                                } else if (action.x != null && action.y != null) {
                                    performTap(action.x, action.y)
                                } else {
                                    onFinished(false, "Could not find UI element for step ${index + 1}. Screen may have changed.")
                                    return@launch
                                }
                                root.recycle()
                            }
                            delay(500)
                        }
                        "TEXT" -> {
                            val root = rootInActiveWindow
                            if (root != null && action.text != null) {
                                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                                    ?: findEditable(root)
                                if (focused != null) {
                                    val args = Bundle()
                                    args.putCharSequence(
                                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                        action.text
                                    )
                                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                                    focused.recycle()
                                }
                                root.recycle()
                            }
                            delay(300)
                        }
                        "SCROLL" -> {
                            val root = rootInActiveWindow
                            root?.performAction(
                                if (action.scrollDirection == "UP")
                                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                                else
                                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            )
                            root?.recycle()
                            delay(400)
                        }
                        "WAIT" -> delay(action.waitMs ?: 500)
                        else -> delay(200)
                    }
                }
                onFinished(true, "Routine \"${routine.name}\" finished")
            } catch (e: Exception) {
                onFinished(false, "Replay error: ${e.message}")
            }
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, action: RecordedAction): AccessibilityNodeInfo? {
        if (!action.viewId.isNullOrBlank()) {
            val list = root.findAccessibilityNodeInfosByViewId(action.viewId)
            if (list.isNotEmpty()) return list[0]
        }
        if (!action.text.isNullOrBlank()) {
            val list = root.findAccessibilityNodeInfosByText(action.text)
            if (list.isNotEmpty()) return list[0]
        }
        if (!action.contentDescription.isNullOrBlank()) {
            return findByContentDesc(root, action.contentDescription)
        }
        return null
    }

    private fun findByContentDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == desc) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findByContentDesc(child, desc)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun isSensitive(text: String?, desc: String?): Boolean {
        val s = ((text ?: "") + " " + (desc ?: "")).lowercase()
        return s.contains("send") || s.contains("call") || s.contains("delete") ||
                s.contains("pay") || s.contains("confirm") || s.contains("purchase")
    }

    companion object {
        @Volatile
        var instance: OrbitAccessibilityService? = null
            private set
    }
}
