package com.example.sampleapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val btnPermissions = Button(this).apply {
            text = "1. Grant Permissions"
            setOnClickListener {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                ActivityCompat.requestPermissions(this@MainActivity, perms.toTypedArray(), 1)
            }
        }

        val btnScan = Button(this).apply {
            text = "2. Scan Earbuds"
            setOnClickListener {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btDevices = devices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                
                if (btDevices.isEmpty()) {
                    textView.text = "No Bluetooth mics found. Connect earbuds first."
                } else {
                    val names = btDevices.joinToString("\n") { "Name: ${it.productName}\nType: ${it.type}" }
                    textView.text = "Found Inputs:\n$names"
                }
            }
        }

        val btnStart = Button(this).apply {
            text = "3. Start Capture"
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, AudioCaptureService::class.java))
                Toast.makeText(this@MainActivity, "Started", Toast.LENGTH_SHORT).show()
            }
        }

        textView = TextView(this).apply {
            text = "Waiting..."
            setPadding(0, 30, 0, 30)
        }

        layout.addView(btnPermissions)
        layout.addView(btnScan)
        layout.addView(textView)
        layout.addView(btnStart)
        setContentView(layout)
    }
}
