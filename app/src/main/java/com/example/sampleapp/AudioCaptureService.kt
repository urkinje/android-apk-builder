package com.example.sampleapp

import android.app.*
import android.content.Intent
import android.media.*
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder
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

        val bufSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val trackBufSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            // 🔥 USING UNPROCESSED SOURCE to bypass Android's Mono Mixing
            var source = MediaRecorder.AudioSource.UNPROCESSED
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                source = MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            audioRecord = AudioRecord(source, 44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(trackBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // High-Pass Filter
            equalizer = Equalizer(0, audioTrack!!.audioSessionId).apply {
                enabled = true
                val bands = numberOfBands.toInt()
                for (i in 0 until bands) {
                    val centerFreq = getCenterFreq(i.toShort()) / 1000
                    if (centerFreq < 300) {
                        setBandLevel(i.toShort(), -1500) // Drop low frequencies
                    } else {
                        setBandLevel(i.toShort(), 500) // Boost voices
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
                while (isRecording) {
                    val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (read > 0) {
                        var maxAmp = 0
                        val gain = MainActivity.currentGain
                        val mode = MainActivity.activeChannel

                        for (i in 0 until read step 2) {
                            var left = shortBuffer[i].toInt()
                            var right = if (i + 1 < read) shortBuffer[i + 1].toInt() else 0

                            // FEATURE 1: True Bud Isolation
                            when (mode) {
                                1 -> { 
                                    // Bud A: Send Left mic to BOTH ears of wired earphones
                                    right = left 
                                }
                                2 -> { 
                                    // Bud B: Send Right mic to BOTH ears of wired earphones
                                    left = right 
                                }
                            }

                            // Apply Gain
                            left = (left * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            right = (right * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                            shortBuffer[i] = left.toShort()
                            if (i + 1 < read) shortBuffer[i + 1] = right.toShort()

                            val amp = maxOf(abs(left), abs(right))
                            if (amp > maxAmp) maxAmp = amp
                        }
                        
                        MainActivity.currentVolume = ((maxAmp / 32767.0) * 100).toInt()
                        
                        // File saving logic...
                        if (MainActivity.isRecordingToFile) {
                            if (fileOutputStream == null) {
                                val file = File(getExternalFilesDir(null), "DualBud_Record.pcm")
                                fileOutputStream = FileOutputStream(file)
                            }
                            val byteBuffer = java.nio.ByteBuffer.allocate(read * 2)
                            byteBuffer.asShortBuffer().put(shortBuffer, 0, read)
                            fileOutputStream?.write(byteBuffer.array())
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
        equalizer?.release()
        fileOutputStream?.close()
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
