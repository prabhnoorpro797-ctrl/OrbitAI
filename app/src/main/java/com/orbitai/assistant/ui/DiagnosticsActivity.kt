package com.orbitai.assistant.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbitai.assistant.R
import com.orbitai.assistant.diagnostics.DiagnosticsEngine
import kotlinx.coroutines.launch

class DiagnosticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Diagnostics"

        val out = findViewById<TextView>(R.id.diagnosticsText)
        val engine = DiagnosticsEngine(this)

        fun run() {
            out.text = "Running checks…"
            lifecycleScope.launch {
                val items = engine.run()
                out.text = items.joinToString("\n\n") { item ->
                    val mark = if (item.ok) "✓" else "✗"
                    buildString {
                        append("$mark ${item.name}\n${item.detail}")
                        if (!item.ok && item.fixHint.isNotBlank()) append("\n→ ${item.fixHint}")
                    }
                }
            }
        }

        findViewById<Button>(R.id.runDiagnosticsButton).setOnClickListener { run() }
        findViewById<Button>(R.id.repairButton).setOnClickListener {
            val changes = engine.restoreSafeDefaults()
            out.text = if (changes.isEmpty()) "No automatic repairs needed."
            else "Applied:\n" + changes.joinToString("\n") { "• $it" }
            run()
        }
        run()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
