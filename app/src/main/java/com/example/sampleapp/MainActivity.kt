package com.example.sampleapp

import android.Manifest
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private var isCapturing = false
    private val handler = Handler(Looper.getMainLooper())

    // The secret layout
    private lateinit var calculatorLayout: LinearLayout
    private lateinit var stealthLayout: LinearLayout
    private lateinit var calcDisplay: TextView
    private var calcText = ""

    companion object {
        @Volatile var currentVolume = 0
        @Volatile var currentGain = 10.0f 
        @Volatile var activeChannel = 0 
        @Volatile var isRecordingToFile = false
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isCapturing) {
                val vol = currentVolume
                // In stealth mode, we just show the volume bar
                calcDisplay.text = "Vol: $vol% [${"|".repeat(vol / 5)}]"
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // The root container
        val rootLayout = FrameLayout(this)
        
        // 1. Build the Calculator UI
        calculatorLayout = buildCalculatorUI()
        
        // 2. Build the Hidden Stealth UI
        stealthLayout = buildStealthUI()
        stealthLayout.visibility = View.GONE // Hide it by default

        rootLayout.addView(calculatorLayout)
        rootLayout.addView(stealthLayout)
        
        setContentView(rootLayout)
    }

    // ==========================================
    // THE CALCULATOR (DISGUISE)
    // ==========================================
    private fun buildCalculatorUI(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        calcDisplay = TextView(this).apply {
            text = ""
            textSize = 50f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(30, 100, 30, 50)
        }
        layout.addView(calcDisplay, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val buttons = arrayOf(
            arrayOf("7", "8", "9", "/"),
            arrayOf("4", "5", "6", "*"),
            arrayOf("1", "2", "3", "-"),
            arrayOf("C", "0", "=", "+")
        )

        for (row in buttons) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (btnText in row) {
                val btn = Button(this).apply {
                    text = btnText
                    textSize = 30f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    setOnClickListener { onCalcButtonPressed(btnText) }
                }
                rowLayout.addView(btn)
            }
            layout.addView(rowLayout)
        }
        return layout
    }

    private fun onCalcButtonPressed(btn: String) {
        // If the app is currently listening, pressing a button brings back the UI
        if (isCapturing) {
             calculatorLayout.visibility = View.VISIBLE
             stealthLayout.visibility = View.GONE
             calcDisplay.text = "Secret Mode Active"
             return
        }

        if (btn == "C") {
            calcText = ""
            calcDisplay.text = ""
        } else if (btn == "=") {
            if (calcText == "123") {
                // SECRET CODE ENTERED: Reveal the Stealth UI!
                calculatorLayout.visibility = View.GONE
                stealthLayout.visibility = View.VISIBLE
                calcText = ""
            } else {
                // Normal calculator math
                try {
                    calcDisplay.text = calcText 
                } catch (e: Exception) {
                    calcDisplay.text = "Error"
                }
                calcText = ""
            }
        } else {
            calcText += btn
            calcDisplay.text = calcText
        }
    }

    // ==========================================
    // THE HIDDEN CONTROLS (STEALTH UI)
    // ==========================================
    private fun buildStealthUI(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(android.graphics.Color.WHITE)
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
                Toast.makeText(this@MainActivity, if (btDevices.isEmpty()) "No mics found" else "Mic Found: ${btDevices.first().productName}", Toast.LENGTH_SHORT).show()
            }
        }

        val gainText = TextView(this).apply { text = "Sensitivity (Gain): ${currentGain.toInt()}x" }
        val seekBar = SeekBar(this).apply {
            max = 20
            progress = 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentGain = (if (progress < 1) 1 else progress).toFloat()
                    gainText.text = "Sensitivity (Gain): ${currentGain.toInt()}x"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val checkRecord = CheckBox(this).apply {
            text = "Save Audio to File"
            setOnCheckedChangeListener { _, isChecked -> isRecordingToFile = isChecked }
        }

        val btnStart = Button(this).apply {
            text = "START LISTENING"
            setOnClickListener {
                val intent = Intent(this@MainActivity, AudioCaptureService::class.java)
                if (!isCapturing) {
                    startForegroundService(intent)
                    isCapturing = true
                    handler.post(updateRunnable)
                    // INSTANTLY disguise the app as a calculator
                    stealthLayout.visibility = View.GONE
                    calculatorLayout.visibility = View.VISIBLE
                } else {
                    stopService(intent)
                    isCapturing = false
                    btnStart.text = "START LISTENING"
                }
            }
        }

        val btnBack = Button(this).apply {
            text = "Back to Calculator"
            setOnClickListener {
                stealthLayout.visibility = View.GONE
                calculatorLayout.visibility = View.VISIBLE
            }
        }

        layout.addView(btnPermissions)
        layout.addView(btnScan)
        layout.addView(gainText)
        layout.addView(seekBar)
        layout.addView(checkRecord)
        layout.addView(btnStart)
        layout.addView(btnBack)
        
        return layout
    }
}
