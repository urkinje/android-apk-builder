package com.example.sampleapp

import android.app.*
import android.content.Intent
import android.media.*
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class AudioCaptureService : Service() {
    private lateinit var audioManager: AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var isRecording = false
    private var thread: Thread? = null
    private var fileOutputStream: FileOutputStream? = null
    private var wakeLock: PowerManager.WakeLock? = null

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

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DualBud::AudioWakeLock")
        wakeLock?.acquire(10*60*1000L)

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true

        val bufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val trackBufSize = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            audioRecord = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(trackBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            equalizer = Equalizer(0, audioTrack!!.audioSessionId).apply {
                enabled = true
                val bands = numberOfBands.toInt()
                for (i in 0 until bands) {
                    val centerFreq = getCenterFreq(i.toShort()) / 1000
                    if (centerFreq < 100) { 
                        setBandLevel(i.toShort(), -500) 
                    } else {
                        setBandLevel(i.toShort(), 300) 
                    }
                }
            }

            val wiredDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wiredDevices.isNotEmpty()) {
                audioTrack?.preferredDevice = wiredDevices.first()
            }

            audioRecord?.startRecording()
            audioTrack?.play()
            isRecording = true

            thread = Thread {
                val shortBuffer = ShortArray(bufSize / 2)
                val stereoBuffer = ShortArray(bufSize) 
                
                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        var maxAmp = 0
                        
                        // Read the active mode and gain from MainActivity
                        val isWhisperMode = (MainActivity.currentMode == 1)
                        var gain = MainActivity.currentGain

                        for (i in 0 until read) {
                            val originalSample = shortBuffer[i].toInt()
                            
                            val finalGain = if (isWhisperMode) {
                                // WHISPER MODE: Extreme flat boost
                                gain * 3.0f
                            } else {
                                // NORMAL MODE: Dynamic Clear Voice
                                if (abs(originalSample) < 1000) gain * 2.0f else gain
                            }

                            var sample = (originalSample * finalGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            
                            stereoBuffer[i * 2] = sample.toShort()
                            stereoBuffer[i * 2 + 1] = sample.toShort()

                            val amp = abs(sample)
                            if (amp > maxAmp) maxAmp = amp
                        }
                        
                        MainActivity.currentVolume = ((maxAmp / 32767.0) * 100).toInt()
                        
                        if (MainActivity.isRecordingToFile) {
                            if (fileOutputStream == null) {
                                val file = File(getExternalFilesDir(null), "DualBud_Record.pcm")
                                fileOutputStream = FileOutputStream(file)
                            }
                            val byteBuffer = java.nio.ByteBuffer.allocate(read * 4) 
                            byteBuffer.asShortBuffer().put(stereoBuffer, 0, read * 2)
                            fileOutputStream?.write(byteBuffer.array())
                        }
                        
                        audioTrack?.write(stereoBuffer, 0, read * 2)
                    }
                }
            }
            thread?.start()
        } catch (e: Exception) {
            MainActivity.errorMessage = e.toString()
            Log.e("DualBud", "Service Error", e)
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
        equalizer?.release()
        fileOutputStream?.close()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
