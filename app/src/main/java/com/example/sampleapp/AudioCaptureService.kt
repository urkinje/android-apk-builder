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

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true

        val bufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val trackBufSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(trackBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val wiredDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wiredDevices.isNotEmpty()) {
                audioTrack?.preferredDevice = wiredDevices.first()
            }

            audioTrack?.setVolume(1.0f)

            audioRecord?.startRecording()
            audioTrack?.play()
            isRecording = true

            thread = Thread {
                val shortBuffer = ShortArray(bufSize / 2)
                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        var maxAmp = 0
                        for (i in 0 until read) {
                            val amp = Math.abs(shortBuffer[i].toInt())
                            if (amp > maxAmp) maxAmp = amp
                        }
                        // Send volume to MainActivity
                        MainActivity.currentVolume = ((maxAmp / 32767.0) * 100).toInt()
                        
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
