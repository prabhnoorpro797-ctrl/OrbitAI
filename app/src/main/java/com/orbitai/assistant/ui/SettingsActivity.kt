package com.orbitai.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.orbitai.assistant.R
import com.orbitai.assistant.util.Prefs
import com.orbitai.assistant.voice.VoiceService

class SettingsActivity : AppCompatActivity() {

    private lateinit var wakeWordSwitch: Switch

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true
        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            results[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true
        if (micOk && notifOk) {
            startWakeService()
            wakeWordSwitch.isChecked = true
        } else {
            wakeWordSwitch.isChecked = false
            Prefs.wakeWordEnabled = false
            Toast.makeText(
                this,
                "Microphone (and Notifications on Android 13+) required for Hey Orbit",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "ORBIT AI Settings"

        val apiKey = findViewById<EditText>(R.id.apiKeyInput)
        val endpoint = findViewById<EditText>(R.id.endpointInput)
        val model = findViewById<EditText>(R.id.modelInput)
        val provider = findViewById<EditText>(R.id.providerInput)
        val search = findViewById<EditText>(R.id.searchInput)
        val haUrl = findViewById<EditText>(R.id.haUrlInput)
        val haToken = findViewById<EditText>(R.id.haTokenInput)
        val ttsSwitch = findViewById<Switch>(R.id.ttsSwitch)
        val offlineSwitch = findViewById<Switch>(R.id.offlineSwitch)
        wakeWordSwitch = findViewById(R.id.wakeWordSwitch)
        val saveBtn = findViewById<Button>(R.id.saveButton)
        val clearKeyBtn = findViewById<Button>(R.id.clearKeyButton)

        apiKey.hint = if (Prefs.apiKey.isNotBlank()) "•••••••• (saved)" else "Enter API key"
        endpoint.setText(Prefs.aiEndpoint)
        model.setText(Prefs.aiModel)
        provider.setText(Prefs.aiProvider)
        search.setText(Prefs.searchEngine)
        haUrl.setText(Prefs.haUrl)
        haToken.hint = if (Prefs.haToken.isNotBlank()) "•••••••• (saved)" else "HA long-lived token"
        ttsSwitch.isChecked = Prefs.ttsEnabled
        offlineSwitch.isChecked = Prefs.offlineModePreferred
        wakeWordSwitch.isChecked = Prefs.wakeWordEnabled

        wakeWordSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                ensureWakePermissionsAndStart()
            } else {
                VoiceService.stop(this)
                Prefs.wakeWordEnabled = false
                Toast.makeText(this, "Hey Orbit listening stopped", Toast.LENGTH_SHORT).show()
            }
        }

        saveBtn.setOnClickListener {
            val key = apiKey.text.toString().trim()
            if (key.isNotEmpty()) Prefs.apiKey = key
            Prefs.aiEndpoint = endpoint.text.toString().trim()
            Prefs.aiModel = model.text.toString().trim()
            Prefs.aiProvider = provider.text.toString().trim()
            Prefs.searchEngine = search.text.toString().trim()
            Prefs.haUrl = haUrl.text.toString().trim()
            val tok = haToken.text.toString().trim()
            if (tok.isNotEmpty()) Prefs.haToken = tok
            Prefs.ttsEnabled = ttsSwitch.isChecked
            Prefs.offlineModePreferred = offlineSwitch.isChecked
            // wake word is applied live via the switch
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        clearKeyBtn.setOnClickListener {
            Prefs.clearApiKey()
            apiKey.setText("")
            apiKey.hint = "Enter API key"
            Toast.makeText(this, "API key cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureWakePermissionsAndStart() {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) {
            micPermissionLauncher.launch(need.toTypedArray())
            return
        }
        startWakeService()
    }

    private fun startWakeService() {
        Prefs.wakeWordEnabled = true
        VoiceService.start(this)
        Toast.makeText(
            this,
            "Hey Orbit is on. Keep the notification visible. Say “Hey Orbit”.",
            Toast.LENGTH_LONG
        ).show()
        // Guide user to battery exemption on Samsung / Android
        AlertDialog.Builder(this)
            .setTitle("Battery tip (Samsung)")
            .setMessage(
                "For more reliable listening:\n\n" +
                        "1. Settings → Apps → ORBIT AI → Battery → Unrestricted\n" +
                        "2. Settings → Battery → Background usage limits → never sleeping apps → add ORBIT AI\n\n" +
                        "You can open app settings now."
            )
            .setPositiveButton("Open app settings") { _, _ ->
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(i)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
