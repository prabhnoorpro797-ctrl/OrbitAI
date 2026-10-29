package com.orbitai.assistant.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbitai.assistant.R
import com.orbitai.assistant.ai.AiOrchestrator
import com.orbitai.assistant.ai.providers.ProviderId
import com.orbitai.assistant.util.Prefs
import kotlinx.coroutines.launch

class ExpertPanelActivity : AppCompatActivity() {
    private val orchestrator = AiOrchestrator()
    private lateinit var results: LinearLayout
    private val checks = mutableMapOf<ProviderId, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expert_panel)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Expert Panel"

        val box = findViewById<LinearLayout>(R.id.providerChecks)
        ProviderId.entries.forEach { pid ->
            val cb = CheckBox(this).apply {
                text = pid.displayName
                setTextColor(getColor(R.color.orbit_text))
                isChecked = Prefs.expertProviders.contains(pid.id) &&
                        (Prefs.keyFor(pid).isNotBlank() || pid == ProviderId.OLLAMA || pid == ProviderId.CUSTOM)
            }
            checks[pid] = cb
            box.addView(cb)
        }

        results = findViewById(R.id.resultsContainer)
        val input = findViewById<EditText>(R.id.questionInput)
        findViewById<Button>(R.id.askButton).setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return@setOnClickListener
            val selected = checks.filter { it.value.isChecked }.keys.toList()
            if (selected.isEmpty()) {
                appendResult("System", "Select at least one provider.")
                return@setOnClickListener
            }
            Prefs.expertProviders = selected.joinToString(",") { it.id }
            results.removeAllViews()
            appendResult("System", "Asking ${selected.size} provider(s)…")
            lifecycleScope.launch {
                val replies = orchestrator.expertPanel(q, selected)
                results.removeAllViews()
                replies.forEach { r ->
                    val header = "${r.providerId} · ${if (r.success) "${r.latencyMs} ms" else "ERROR"}"
                    val body = if (r.success) r.text else (r.error ?: "failed")
                    appendResult(header, body)
                }
                val ok = replies.filter { it.success }
                if (ok.size >= 2) {
                    appendResult(
                        "Notes",
                        "Got ${ok.size} successful answers. Compare them above. " +
                                "Summary synthesis can be run by asking your default model in the main chat."
                    )
                }
            }
        }
    }

    private fun appendResult(title: String, body: String) {
        val tv = TextView(this).apply {
            setTextColor(getColor(R.color.orbit_text))
            textSize = 14f
            setPadding(0, 16, 0, 8)
            text = "▸ $title\n$body"
        }
        results.addView(tv)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
