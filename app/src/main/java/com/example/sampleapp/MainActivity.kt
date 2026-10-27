package com.example.sampleapp

import android.Manifest
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var textView: TextView
    private lateinit var volumeText: TextView
    private var isCapturing = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile var currentVolume = 0
        @Volatile var currentGain = 4.0f // Default Gain
        @Volatile var activeChannel = 0 // 0=Both, 1=Left, 2=Right
        @Volatile var isRecordingToFile = false
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                val vol = currentVolume
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

        // Permissions & Scan
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
                textView.text = if (btDevices.isEmpty()) "No mics found." else "Found:\n" + btDevices.joinToString("\n") { it.productName }
            }
        }

        textView = TextView(this).apply { text = "Waiting..."; setPadding(0, 20, 0, 20) }
        volumeText = TextView(this).apply { text = "Mic Volume: 0%"; textSize = 18f; setPadding(0, 10, 0, 20) }

        // FEATURE 2: Gain Slider
        val gainText = TextView(this).apply { text = "Sensitivity (Gain): 4x" }
        val seekBar = SeekBar(this).apply {
            max = 10
            progress = 4
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentGain = (if (progress < 1) 1 else progress).toFloat()
                    gainText.text = "Sensitivity (Gain): ${currentGain.toInt()}x"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // FEATURE 1: Bud Isolation Buttons
        val budLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnBoth = Button(this).apply { text = "Both"; setOnClickListener { activeChannel = 0 } }
        val btnLeft = Button(this).apply { text = "Bud A"; setOnClickListener { activeChannel = 1 } }
        val btnRight = Button(this).apply { text = "Bud B"; setOnClickListener { activeChannel = 2 } }
        budLayout.addView(btnBoth, LinearLayout.LayoutParams(0, -2, 1f))
        budLayout.addView(btnLeft, LinearLayout.LayoutParams(0, -2, 1f))
        budLayout.addView(btnRight, LinearLayout.LayoutParams(0, -2, 1f))

        // FEATURE 4: Record Checkbox
        val checkRecord = CheckBox(this).apply {
            text = "Save Audio to File"
            setOnCheckedChangeListener { _, isChecked -> isRecordingToFile = isChecked }
        }

        // Start / Stop Button
        val btnStart = Button(this).apply {
            text = "3. START LISTENING"
            setOnClickListener {
                val intent = Intent(this@MainActivity, AudioCaptureService::class.java)
                if (!isCapturing) {
                    startForegroundService(intent)
                    text = "3. STOP LISTENING"
                    isCapturing = true
                    handler.post(updateRunnable)
                } else {
                    stopService(intent)
                    text = "3. START LISTENING"
                    isCapturing = false
                }
            }
        }

        layout.addView(btnPermissions)
        layout.addView(btnScan)
        layout.addView(textView)
        layout.addView(volumeText)
        layout.addView(gainText)
        layout.addView(seekBar)
        layout.addView(budLayout)
        layout.addView(checkRecord)
        layout.addView(btnStart)
        
        setContentView(layout)
    }
}
