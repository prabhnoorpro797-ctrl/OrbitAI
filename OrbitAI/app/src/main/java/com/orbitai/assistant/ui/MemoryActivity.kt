package com.orbitai.assistant.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.orbitai.assistant.R
import com.orbitai.assistant.memory.MemoryRepository
import com.orbitai.assistant.util.Prefs
import kotlinx.coroutines.launch

class MemoryActivity : AppCompatActivity() {
    private lateinit var repo: MemoryRepository
    private lateinit var list: ListView
    private var ids = listOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Memory"
        repo = MemoryRepository(this)
        list = findViewById(R.id.memoryList)

        findViewById<Button>(R.id.addMemoryButton).setOnClickListener {
            val key = findViewById<EditText>(R.id.memoryKey).text.toString().trim()
            val content = findViewById<EditText>(R.id.memoryContent).text.toString().trim()
            if (key.isBlank() || content.isBlank()) {
                Toast.makeText(this, "Key and content required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    repo.remember(key, content)
                    Toast.makeText(this@MemoryActivity, "Saved", Toast.LENGTH_SHORT).show()
                    refresh()
                } catch (e: Exception) {
                    Toast.makeText(this@MemoryActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        findViewById<Button>(R.id.clearChatButton).setOnClickListener {
            lifecycleScope.launch {
                repo.clearChat()
                Toast.makeText(this@MemoryActivity, "Chat history cleared", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.clearMemoriesButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all memories?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        repo.clearMemories()
                        refresh()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        list.setOnItemClickListener { _, _, pos, _ ->
            if (pos >= ids.size) return@setOnItemClickListener
            val id = ids[pos]
            AlertDialog.Builder(this)
                .setTitle("Delete memory?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        repo.delete(id)
                        refresh()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val all = repo.all()
            ids = all.map { it.id }
            val lines = all.map { "${it.key}: ${it.content}" }
            list.adapter = ArrayAdapter(
                this@MemoryActivity,
                android.R.layout.simple_list_item_1,
                if (lines.isEmpty()) listOf("(no memories yet — say “Remember that …”)") else lines
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
