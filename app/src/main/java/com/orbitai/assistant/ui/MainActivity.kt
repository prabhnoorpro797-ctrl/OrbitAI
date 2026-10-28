package com.orbitai.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.orbitai.assistant.R
import com.orbitai.assistant.ai.AiClient
import com.orbitai.assistant.automation.OrbitAccessibilityService
import com.orbitai.assistant.commands.CommandParser
import com.orbitai.assistant.phone.PhoneController
import com.orbitai.assistant.routines.RoutineRepository
import com.orbitai.assistant.util.Prefs
import com.orbitai.assistant.voice.SpeechHelper
import com.orbitai.assistant.voice.TtsHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var conversationList: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var micButton: FloatingActionButton
    private lateinit var statusText: TextView
    private lateinit var waveform: View
    private lateinit var emergencyStop: ImageButton

    private lateinit var adapter: ConversationAdapter
    private lateinit var tts: TtsHelper
    private lateinit var phone: PhoneController
    private lateinit var routines: RoutineRepository
    private val ai = AiClient()
    private val history = mutableListOf<Pair<String, String>>() // role, content
    private var speechHelper: SpeechHelper? = null
    private var listening = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.filter { it.value }.keys
        if (granted.isNotEmpty()) {
            appendSystem("Permissions granted: ${granted.joinToString { it.substringAfterLast('.') }}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "ORBIT AI"

        conversationList = findViewById(R.id.conversationList)
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)
        waveform = findViewById(R.id.waveform)
        emergencyStop = findViewById(R.id.emergencyStop)

        adapter = ConversationAdapter()
        conversationList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        conversationList.adapter = adapter

        tts = TtsHelper(this)
        tts.onSpeakingChanged = { speaking ->
            runOnUiThread { waveform.visibility = if (speaking || listening) View.VISIBLE else View.INVISIBLE }
        }
        phone = PhoneController(this)
        routines = RoutineRepository(this)

        sendButton.setOnClickListener { submitText(inputField.text.toString()) }
        inputField.setOnEditorActionListener { _, _, _ ->
            submitText(inputField.text.toString()); true
        }
        micButton.setOnClickListener { toggleListen() }
        emergencyStop.setOnClickListener {
            OrbitAccessibilityService.instance?.cancelReplay()
            OrbitAccessibilityService.instance?.stopRecording()
            tts.stop()
            speechHelper?.stop()
            appendSystem("Emergency stop activated.")
            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
        appendSystem("ORBIT AI online. Tap the microphone or type a command. Say \"help\" for examples.")
        requestCorePermissions()
        handleWakeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
    }

    /** Called when VoiceService detects "Hey Orbit". */
    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.orbitai.assistant.voice.VoiceService.EXTRA_WAKE, false) != true) {
            return
        }
        intent.removeExtra(com.orbitai.assistant.voice.VoiceService.EXTRA_WAKE)
        appendSystem("Wake word detected. Listening…")
        tts.speak("Yes?")
        // Small delay so TTS does not collide with recognition
        micButton.postDelayed({ toggleListen() }, 700)
    }

    private fun requestCorePermissions() {
        val needed = mutableListOf<String>()
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needed.add(it)
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun toggleListen() {
        if (listening) {
            speechHelper?.stop()
            listening = false
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        speechHelper = SpeechHelper(
            this,
            onResult = { text -> runOnUiThread { submitText(text) } },
            onError = { err -> runOnUiThread { appendSystem(err) } },
            onListening = { isOn ->
                runOnUiThread {
                    listening = isOn
                    waveform.visibility = if (isOn) View.VISIBLE else View.INVISIBLE
                    micButton.alpha = if (isOn) 0.6f else 1f
                }
            }
        )
        speechHelper?.startListening()
    }

    private fun submitText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        inputField.setText("")
        appendUser(text)
        processCommand(text)
    }

    private fun processCommand(text: String) {
        val offline = CommandParser.parseOffline(text)
        when (offline.type) {
            CommandParser.Type.TIME -> {
                val msg = phone.speakTime()
                reply(msg)
            }
            CommandParser.Type.FLASHLIGHT -> {
                reply(phone.toggleFlashlight(offline.payload))
            }
            CommandParser.Type.OPEN_APP -> {
                reply(phone.openApp(offline.payload))
            }
            CommandParser.Type.OPEN_SETTINGS -> {
                reply(phone.openSettings(offline.payload))
            }
            CommandParser.Type.OPEN_URL -> {
                reply(phone.openUrl(offline.payload))
            }
            CommandParser.Type.SEARCH -> {
                reply(phone.searchWeb(offline.payload))
            }
            CommandParser.Type.CALL -> {
                confirmSensitive("Call $offline.payload?") {
                    reply(phone.startCall(offline.payload))
                }
            }
            CommandParser.Type.MESSAGE -> {
                reply(phone.prepareMessage(offline.payload))
            }
            CommandParser.Type.ROUTINE_RECORD_START -> {
                val svc = OrbitAccessibilityService.instance
                if (svc == null) {
                    reply("Enable Orbit Accessibility Service in Settings → Accessibility to record steps.")
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    svc.startRecording()
                    reply("Recording your steps. Perform the actions, then say \"stop recording\".")
                }
            }
            CommandParser.Type.ROUTINE_RECORD_STOP -> {
                val svc = OrbitAccessibilityService.instance
                if (svc == null || !svc.isRecording()) {
                    reply("Not currently recording.")
                } else {
                    val actions = svc.stopRecording()
                    if (actions.isEmpty()) {
                        reply("No actions captured. Try interacting with apps while recording.")
                    } else {
                        val name = "Routine ${System.currentTimeMillis() % 10000}"
                        routines.create(name, actions)
                        reply("Saved ${actions.size} steps as \"$name\". Say \"repeat $name\" to replay.")
                    }
                }
            }
            CommandParser.Type.ROUTINE_PLAY -> {
                val r = routines.findByName(offline.payload)
                if (r == null) {
                    reply("Routine not found. Say \"list routines\".")
                } else {
                    val svc = OrbitAccessibilityService.instance
                    if (svc == null) {
                        reply("Enable Accessibility Service to replay routines.")
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else {
                        reply("Replaying \"${r.name}\"…")
                        svc.replay(r,
                            onProgress = { p -> runOnUiThread { appendSystem(p) } },
                            onFinished = { ok, msg -> runOnUiThread { reply(msg) } }
                        )
                        r.lastRunAt = System.currentTimeMillis()
                        routines.update(r)
                    }
                }
            }
            CommandParser.Type.ROUTINE_LIST -> {
                val list = routines.loadAll()
                if (list.isEmpty()) reply("No saved routines yet.")
                else reply(list.joinToString("\n") { "• ${it.name} (${it.actions.size} steps)" })
            }
            CommandParser.Type.SMART_HOME -> {
                reply(
                    "Smart-home commands need configuration. Open Settings and set Home Assistant URL + token, " +
                            "or add devices manually under Devices. Command received: ${offline.payload}"
                )
            }
            CommandParser.Type.HELP -> {
                reply(HELP_TEXT)
            }
            CommandParser.Type.CONVERSATION, CommandParser.Type.UNKNOWN, CommandParser.Type.WEB_SEARCH -> {
                if (!isOnline() || Prefs.offlineModePreferred || Prefs.apiKey.isBlank()) {
                    reply(
                        if (Prefs.apiKey.isBlank())
                            "Offline / no API key. I can open apps, settings, flashlight, time, and local routines. Configure AI in Settings for full chat."
                        else
                            "No internet. Local commands only."
                    )
                } else {
                    lifecycleScope.launch {
                        appendSystem("Thinking…")
                        val result = ai.chat(text, history)
                        if (result.success) {
                            history.add("user" to text)
                            history.add("assistant" to result.text)
                            // Try action parsing
                            val action = CommandParser.parseAiAction(result.text)
                            if (action != null && action.type != CommandParser.Type.UNKNOWN &&
                                action.type != CommandParser.Type.CONVERSATION
                            ) {
                                processCommand(action.raw) // reuse offline path where possible
                            } else {
                                reply(result.text)
                            }
                        } else {
                            reply(result.error ?: "AI unavailable")
                        }
                    }
                }
            }
            else -> reply("I didn't understand that. Say \"help\".")
        }
        updateStatus()
    }

    private fun confirmSensitive(message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun reply(msg: String) {
        appendAssistant(msg)
        tts.speak(msg)
    }

    private fun appendUser(msg: String) {
        adapter.add(ConversationAdapter.Item(ConversationAdapter.Item.Type.USER, msg))
        conversationList.scrollToPosition(adapter.itemCount - 1)
    }

    private fun appendAssistant(msg: String) {
        adapter.add(ConversationAdapter.Item(ConversationAdapter.Item.Type.ASSISTANT, msg))
        conversationList.scrollToPosition(adapter.itemCount - 1)
    }

    private fun appendSystem(msg: String) {
        adapter.add(ConversationAdapter.Item(ConversationAdapter.Item.Type.SYSTEM, msg))
        conversationList.scrollToPosition(adapter.itemCount - 1)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateStatus() {
        val online = isOnline()
        val aiReady = Prefs.apiKey.isNotBlank()
        statusText.text = buildString {
            append(if (online) "● ONLINE" else "● OFFLINE")
            append("  |  AI: ")
            append(if (aiReady) Prefs.aiProvider else "not configured")
            append("  |  Acc: ")
            append(if (OrbitAccessibilityService.instance != null) "on" else "off")
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.action_routines -> startActivity(Intent(this, RoutinesActivity::class.java))
            R.id.action_devices -> startActivity(Intent(this, DevicesActivity::class.java))
            R.id.action_backup -> startActivity(Intent(this, BackupActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        speechHelper?.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private val HELP_TEXT = """
            Examples:
            • "Open YouTube" / "Open Settings"
            • "Turn on flashlight"
            • "What time is it?"
            • "Search quantum computing"
            • "Call Mom" (needs confirmation)
            • "Record my steps" → do actions → "Stop recording"
            • "Repeat <routine name>"
            • "List routines"
            Configure AI API key in Settings for full conversation.
            Enable Accessibility Service for step recording & replay.
        """.trimIndent()
    }
}
