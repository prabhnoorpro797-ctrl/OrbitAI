package com.orbitai.assistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.orbitai.assistant.R
import com.orbitai.assistant.routines.RoutineRepository
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupActivity : AppCompatActivity() {
    private lateinit var repo: RoutineRepository
    private lateinit var status: TextView

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repo.exportJson().toByteArray())
            }
            status.text = "Exported successfully (routines only; no API keys)."
            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = "Export failed: ${e.message}"
        }
    }

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).readText()
            } ?: ""
            val result = repo.importJson(json)
            result.onSuccess {
                status.text = "Imported $it new routine(s)."
            }.onFailure {
                status.text = "Import failed: ${it.message}"
            }
        } catch (e: Exception) {
            status.text = "Import error: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Backup & Restore"
        repo = RoutineRepository(this)
        status = findViewById(R.id.backupStatus)
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            createDoc.launch("orbit_routines_backup.json")
        }
        findViewById<Button>(R.id.importButton).setOnClickListener {
            openDoc.launch(arrayOf("application/json", "text/*"))
        }
        status.text = "Backups contain routines only. API keys and tokens are never exported."
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
