package com.example.sampleapp

import android.app.*
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import android.util.Log

class AudioCaptureService : Service() {
    private lateinit var audioManager: AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var thread: Thread? = null

    // 0 = Both, 1 = Left, 2 = Right
    @Volatile var activeChannel = 0

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mic", "Mic", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "mic").setContentTitle("Listening...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("Listening...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        }
        startForeground(1, notif)

        // Force Bluetooth Mic routing
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true

        val bufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val trackBufSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(trackBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // 🔥 THIS IS THE MAGIC LINE: Route playback to wired headphones instead of Bluetooth
            val wiredDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wiredDevices.isNotEmpty()) {
                audioTrack?.preferredDevice = wiredDevices.first()
                Log.d("DualBud", "Routing audio to wired headphones!")
            } else {
                Log.d("DualBud", "No wired headphones found. Playing through phone speaker.")
            }

            audioRecord?.startRecording()
            audioTrack?.play()
            isRecording = true

            thread = Thread {
                val shortBuffer = ShortArray(bufSize / 2)
                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read step 2) {
                            val left = shortBuffer[i]
                            val right = if (i + 1 < read) shortBuffer[i + 1] else 0

                            when (activeChannel) {
                                1 -> { // Left only
                                    shortBuffer[i] = left
                                    if (i + 1 < read) shortBuffer[i + 1] = left
                                }
                                2 -> { // Right only
                                    shortBuffer[i] = right
                                    if (i + 1 < read) shortBuffer[i + 1] = right
                                }
                            }
                        }
                        audioTrack?.write(shortBuffer, 0, read)
                    }
                }
            }
            thread?.start()
        } catch (e: Exception) {
            Log.e("DualBud", "Error", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRecording = false
        thread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
