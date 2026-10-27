package com.example.sampleapp

import android.Manifest
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var textView: TextView
    private lateinit var volumeText: TextView
    private var isCapturing = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                val vol = AudioCaptureService.instance?.currentVolume ?: 0
                volumeText.text = "Mic Volume: $vol% [${"|".repeat(vol / 5)}]"
                handler.postDelayed(this, 200)
            }
        }
    }

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
                    textView.text = "No Bluetooth mics found."
                } else {
                    val names = btDevices.joinToString("\n") { "Name: ${it.productName}\nType: ${it.type}" }
                    textView.text = "Found Inputs:\n$names"
                }
            }
        }

        volumeText = TextView(this).apply {
            text = "Mic Volume: 0%"
            textSize = 18f
            setPadding(0, 20, 0, 20)
        }

        val btnStart = Button(this).apply {
            text = "3. Start / Stop Listening"
            setOnClickListener {
                val intent = Intent(this@MainActivity, AudioCaptureService::class.java)
                if (!isCapturing) {
                    startForegroundService(intent)
                    Toast.makeText(this@MainActivity, "Started. Speak into buds!", Toast.LENGTH_SHORT).show()
                    text = "3. Stop Listening"
                    isCapturing = true
                    handler.post(updateRunnable)
                } else {
                    stopService(intent)
                    Toast.makeText(this@MainActivity, "Stopped", Toast.LENGTH_SHORT).show()
                    text = "3. Start / Stop Listening"
                    isCapturing = false
                }
            }
        }

        textView = TextView(this).apply {
            text = "Waiting..."
            setPadding(0, 30, 0, 30)
        }

        layout.addView(btnPermissions)
        layout.addView(btnScan)
        layout.addView(textView)
        layout.addView(volumeText)
        layout.addView(btnStart)
        
        setContentView(layout)
    }
}
