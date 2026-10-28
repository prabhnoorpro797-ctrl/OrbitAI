package com.orbitai.assistant.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.orbitai.assistant.R
import com.orbitai.assistant.routines.RoutineRepository

class RoutinesActivity : AppCompatActivity() {
    private lateinit var repo: RoutineRepository
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Saved Routines"
        repo = RoutineRepository(this)
        listView = findViewById(R.id.simpleList)
        refresh()
        listView.setOnItemClickListener { _, _, pos, _ ->
            val routines = repo.loadAll()
            if (pos >= routines.size) return@setOnItemClickListener
            val r = routines[pos]
            val details = r.actions.joinToString("\n") { a ->
                "${a.type}: ${a.text ?: a.contentDescription ?: a.packageName ?: ""}"
            }
            AlertDialog.Builder(this)
                .setTitle(r.name)
                .setMessage(if (details.isBlank()) "(no actions)" else details)
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete") { _, _ ->
                    repo.delete(r.id)
                    refresh()
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Rename") { _, _ ->
                    val input = android.widget.EditText(this).apply { setText(r.name) }
                    AlertDialog.Builder(this)
                        .setTitle("Rename")
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            r.name = input.text.toString().ifBlank { r.name }
                            repo.update(r)
                            refresh()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                .show()
        }
    }

    private fun refresh() {
        val names = repo.loadAll().map { "${it.name}  (${it.actions.size} steps)" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
