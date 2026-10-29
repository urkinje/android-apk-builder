package com.example.sampleapp

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private var isCapturing = false

    private lateinit var gameLayout: LinearLayout
    private lateinit var settingsLayout: LinearLayout
    private lateinit var stealthLayout: LinearLayout
    
    private lateinit var coinImage: TextView
    private lateinit var streakText: TextView
    private lateinit var errorText: TextView 
    private var currentStreak = 0

    companion object {
        @Volatile var currentVolume = 0
        
        // Mode 0: Normal, Mode 1: Whisper
        @Volatile var currentMode = 0 
        
        // Separate memory for each slider!
        @Volatile var normalGain = 15.0f 
        @Volatile var whisperGain = 25.0f 
        @Volatile var currentGain = 15.0f 
        
        @Volatile var isRecordingToFile = false
        @Volatile var errorMessage = "" 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        supportActionBar?.hide()

        val rootLayout = FrameLayout(this)
        
        gameLayout = buildGameUI()
        settingsLayout = buildSettingsUI()
        stealthLayout = buildStealthUI()
        
        settingsLayout.visibility = View.GONE 
        stealthLayout.visibility = View.GONE 

        rootLayout.addView(gameLayout)
        rootLayout.addView(settingsLayout)
        rootLayout.addView(stealthLayout)
        
        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        if (errorMessage.isNotEmpty()) {
            errorText.text = "SERVICE CRASHED:\n$errorMessage"
            errorText.visibility = View.VISIBLE
            errorMessage = "" 
        }
    }

    private fun buildGameUI(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(20, 20, 20, 0)
        }
        val btnSettings = TextView(this).apply {
            text = "⚙️"
            textSize = 30f
            setOnClickListener {
                gameLayout.visibility = View.GONE
                settingsLayout.visibility = View.VISIBLE
            }
        }
        topBar.addView(btnSettings)

        val title = TextView(this).apply {
            text = "Coin Flip Challenge"
            textSize = 28f
            setTextColor(android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        coinImage = TextView(this).apply {
            text = "🪙"
            textSize = 100f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        streakText = TextView(this).apply {
            text = "Heads in a row: 0"
            textSize = 18f
            setTextColor(android.graphics.Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        errorText = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(android.graphics.Color.RED)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            visibility = View.GONE
        }

        val btnFlip = Button(this).apply {
            text = "FLIP COIN"
            textSize = 20f
            setOnClickListener { flipCoin() }
        }

        layout.addView(topBar)
        layout.addView(title)
        layout.addView(coinImage)
        layout.addView(streakText)
        layout.addView(errorText)
        layout.addView(btnFlip)
        
        return layout
    }

    private fun flipCoin() {
        gameLayout.isEnabled = false
        val animator = ObjectAnimator.ofFloat(coinImage, "rotationY", 0f, 1440f).apply {
            duration = 1000
            interpolator = LinearInterpolator()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                gameLayout.isEnabled = true
                if (Random.nextBoolean()) {
                    coinImage.text = "👑"
                    currentStreak++
                    streakText.text = "Heads in a row: $currentStreak"
                } else {
                    coinImage.text = "🦅"
                    currentStreak = 0
                    streakText.text = "Heads in a row: 0"
                }
            }
        })
        animator.start()
    }

    private fun buildSettingsUI(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(40, 100, 40, 40)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 32f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, 30)
        }

        val searchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#EEEEEE"))
            setPadding(20, 10, 20, 10)
        }
        
        val searchInput = EditText(this).apply {
            hint = "Enter PIN..."
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val searchBtn = TextView(this).apply {
            text = "🔍"
            textSize = 20f
            setPadding(15, 0, 15, 0)
            setOnClickListener {
                if (searchInput.text.toString() == "2026") {
                    settingsLayout.visibility = View.GONE
                    stealthLayout.visibility = View.VISIBLE
                    searchInput.setText("") 
                } else {
                    Toast.makeText(this@MainActivity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                }
            }
        }

        searchLayout.addView(searchInput)
        searchLayout.addView(searchBtn)

        val btnBack = Button(this).apply {
            text = "Back to Game"
            setOnClickListener {
                settingsLayout.visibility = View.GONE
                gameLayout.visibility = View.VISIBLE
            }
        }

        layout.addView(title)
        layout.addView(searchLayout)
        layout.addView(btnBack)
        
        return layout
    }

    private fun buildStealthUI(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        val btnPermissions = Button(this).apply {
            text = "1. Grant Permissions"
            setOnClickListener {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                ActivityCompat.requestPermissions(this@MainActivity, perms.toTypedArray(), 1)
            }
        }
        
        val btnScan = Button(this).apply {
            text = "2. Scan Earbuds"
            setOnClickListener {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btDevices = devices.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                Toast.makeText(this@MainActivity, if (btDevices.isEmpty()) "No mics found" else "Mic: ${btDevices.first().productName}", Toast.LENGTH_SHORT).show()
            }
        }

        // MODE SELECTOR BUTTON
        val modeBtn = Button(this).apply {
            text = if (currentMode == 0) "MODE: Normal Voice" else "MODE: Whisper"
            setOnClickListener {
                if (currentMode == 0) {
                    currentMode = 1
                    currentGain = whisperGain
                    text = "MODE: Whisper"
                    Toast.makeText(this@MainActivity, "Switched to Whisper Mode", Toast.LENGTH_SHORT).show()
                } else {
                    currentMode = 0
                    currentGain = normalGain
                    text = "MODE: Normal Voice"
                    Toast.makeText(this@MainActivity, "Switched to Normal Mode", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val gainText = TextView(this).apply { text = "Sensitivity: ${currentGain.toInt()}x" }
        val seekBar = SeekBar(this).apply {
            max = 30
            
            // Load the saved slider for the current mode
            progress = if (currentMode == 0) normalGain.toInt() else whisperGain.toInt()
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentGain = (if (progress < 1) 1 else progress).toFloat()
                    
                    // Save the slider to the correct mode variable
                    if (currentMode == 0) normalGain = currentGain else whisperGain = currentGain
                    
                    gainText.text = "Sensitivity: ${currentGain.toInt()}x"
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
                    stealthLayout.visibility = View.GONE
                    gameLayout.visibility = View.VISIBLE
                } else {
                    stopService(intent)
                    isCapturing = false
                    this.text = "START LISTENING"
                }
            }
        }

        val btnBack = Button(this).apply {
            text = "Hide Controls"
            setOnClickListener {
                stealthLayout.visibility = View.GONE
                gameLayout.visibility = View.VISIBLE
            }
        }

        layout.addView(btnPermissions)
        layout.addView(btnScan)
        layout.addView(modeBtn)
        layout.addView(gainText)
        layout.addView(seekBar)
        layout.addView(checkRecord)
        layout.addView(btnStart)
        layout.addView(btnBack)
        
        return layout
    }
}
