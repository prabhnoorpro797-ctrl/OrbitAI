package com.orbitai.assistant.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.orbitai.assistant.R
import com.orbitai.assistant.devices.DeviceManager

/**
 * Device dashboard. Discovery is modular; concrete plugins must be configured.
 * Does not break into devices or bypass authentication.
 */
class DevicesActivity : AppCompatActivity() {
    private lateinit var manager: DeviceManager
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_devices)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Devices"
        manager = DeviceManager(this)
        listView = findViewById(R.id.deviceList)
        findViewById<Button>(R.id.scanButton).setOnClickListener {
            Toast.makeText(this, "Scanning local network (plugin-based)…", Toast.LENGTH_SHORT).show()
            manager.scan { refresh() }
        }
        findViewById<Button>(R.id.addManualButton).setOnClickListener { showAddDialog() }
        refresh()
    }

    private fun refresh() {
        val items = manager.listDevices().map {
            "${it.name}  [${it.type}]  ${it.address}  (${it.status})"
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.ifEmpty {
            listOf("No devices yet. Scan or add manually. Configure Home Assistant in Settings.")
        })
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_device, null)
        val name = view.findViewById<EditText>(R.id.deviceName)
        val type = view.findViewById<EditText>(R.id.deviceType)
        val address = view.findViewById<EditText>(R.id.deviceAddress)
        AlertDialog.Builder(this)
            .setTitle("Add device")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                manager.addManual(
                    name.text.toString().ifBlank { "Device" },
                    type.text.toString().ifBlank { "generic" },
                    address.text.toString()
                )
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
