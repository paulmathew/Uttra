package com.uttra.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.uttra.app.MainActivity
import com.uttra.app.audio.AndroidAudioRecorder
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import com.uttra.app.core.logging.logw
import com.uttra.app.transcription.WhisperTestRunner
import com.uttra.app.transcription.UttraSpeechTranscriber
import com.uttra.app.transcription.TranscriptionLanguage
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.os.Bundle
import com.uttra.app.BuildConfig


class MicrophoneRecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "uttra_recording"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.uttra.app.action.START_RECORDING"
        const val ACTION_STOP = "com.uttra.app.action.STOP_RECORDING"
        const val ACTION_CANCEL = "com.uttra.app.action.CANCEL_RECORDING"

        const val EXTRA_RESULT_RECEIVER = "recording_result_receiver"

        const val RESULT_STARTED = 1
        const val RESULT_FAILED = 2

        const val RESULT_STOPPED = 3
        const val RESULT_CANCELLED = 4
        const val EXTRA_SESSION_ID = "com.uttra.app.extra.SESSION_ID"

        private val transcriptionScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

        const val RESULT_TRANSCRIPTION_READY = 5
        const val RESULT_TRANSCRIPTION_FAILED = 6

        const val EXTRA_TRANSCRIPTION_TEXT =
            "com.uttra.app.extra.TRANSCRIPTION_TEXT"

    }

    private lateinit var recordingController: RecordingSessionController


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Clean up orphaned app recordings after process death or an interrupted session.
        val cutoffMs = System.currentTimeMillis() - 15 * 60 * 1000L
        cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("uttra_") &&
                    it.name.endsWith(".m4a") && it.lastModified() < cutoffMs
        }?.forEach { it.delete() }

        recordingController = RecordingSessionController(AndroidAudioRecorder(applicationContext))
        logd { "MicrophoneRecordingService onCreated" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra<ResultReceiver>(
            EXTRA_RESULT_RECEIVER
        )
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        when (intent?.action) {
            ACTION_START -> {
                logd { "1. ACTION_START received" }
                if (recordingController.state == RecordingState.RECORDING) {
                    logw { "Duplicate START rejected: recording already active" }

                    receiver?.send(RESULT_FAILED, null)

                    return START_NOT_STICKY
                }

                if (sessionId.isNullOrBlank()) {
                    logw { "START rejected: missing session ID" }
                    receiver?.send(RESULT_FAILED, null)
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                try {
                    promoteToForeground()

                    logd { "2. Foreground promotion completed" }

                    logd { "3. Calling recordingController.startRecording()" }

                    recordingController.startRecording(sessionId = sessionId)
                        .onSuccess {
                            receiver?.send(RESULT_STARTED, null)
                            logd { "4. SUCCESS: Audio recording started" }
                        }
                        .onFailure { error ->
                            receiver?.send(RESULT_FAILED, null)
                            loge(error) {
                                "4. FAILED: Audio recording could not start"
                            }

                            recordingController.cancelRecording()
                            stopSelfResult(startId)
                        }

                } catch (e: Exception) {
                    receiver?.send(RESULT_FAILED, null)
                    loge(e) { "Foreground service startup failed" }

                    recordingController.cancelRecording()
                    stopSelfResult(startId)
                }
            }

            ACTION_STOP -> {
                logd { "ACTION_STOP received" }
                if (
                    sessionId.isNullOrBlank() ||
                    recordingController.activeSessionId != sessionId
                ) {
                    logw { "STOP rejected: session mismatch" }
                    receiver?.send(RESULT_FAILED, null)
                    return START_NOT_STICKY
                }
                recordingController.stopRecording(sessionId)
                    .onSuccess { file ->
                        // Stop is acknowledged before starting asynchronous transcription.
                        val selectedLanguage = if (BuildConfig.DEBUG) {
                            TranscriptionLanguage.AUTO
                        } else {
                            TranscriptionLanguage.MALAYALAM
                        }

                        receiver?.send(RESULT_STOPPED, null)
                        logd {
                            "Recording saved: ${file.absolutePath}, " +
                                    "size=${file.length()} bytes"
                        }

                        val appContext = applicationContext

                        transcriptionScope.launch {
                            try {
                                logd {
                                    "UTTRA LIVE: Starting $selectedLanguage transcription"
                                }

                                val result = UttraSpeechTranscriber(appContext)
                                    .transcribe(
                                        audioFile = file,
                                        language = selectedLanguage
                                    )

                                result
                                    .onSuccess { text ->
                                        logd {
                                            "UTTRA LIVE TRANSCRIPTION PASSED: text='$text'"
                                        }
                                        receiver?.send(
                                            RESULT_TRANSCRIPTION_READY,
                                            Bundle().apply {
                                                putString(EXTRA_TRANSCRIPTION_TEXT, text)
                                            }
                                        )
                                    }
                                    .onFailure { error ->
                                        loge(error) {
                                            "UTTRA LIVE TRANSCRIPTION FAILED"
                                        }
                                        receiver?.send(RESULT_TRANSCRIPTION_FAILED, null)
                                    }

                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                loge(e) {
                                    "UTTRA LIVE TRANSCRIPTION EXCEPTION"
                                }
                                receiver?.send(RESULT_TRANSCRIPTION_FAILED, null)
                            } finally {
                                // Audio is never retained for retries: every new attempt
                                // records its own .m4a, even after recognition failure.
                                if (file.exists() && !file.delete()) {
                                    logw { "Could not delete completed recording: ${file.name}" }
                                }
                            }
                        }



                    }
                    .onFailure { error ->
                        loge(error) { "Failed to stop recording" }
                        receiver?.send(RESULT_FAILED, null)

                        recordingController.cancelRecording()
                    }

                stopSelfResult(startId)
            }

            ACTION_CANCEL -> {
                logd { "ACTION_CANCEL received" }

                if (
                    sessionId.isNullOrBlank() ||
                    recordingController.activeSessionId != sessionId
                ) {
                    logw { "Cancel rejected: session mismatch" }
                    receiver?.send(RESULT_FAILED, null)
                    return START_NOT_STICKY
                }
                recordingController.cancelRecording(sessionId)
                    .onSuccess {
                        receiver?.send(RESULT_CANCELLED, null)
                    }
                    .onFailure { error ->
                        loge(error) { "Failed to cancel recording" }
                        receiver?.send(RESULT_FAILED, null)
                    }

                stopSelfResult(startId)

            }

            else -> {
                logw { "Unknown or null recording action" }
                stopSelfResult(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::recordingController.isInitialized) {
            recordingController.cancelRecording()
        }

        logd { "MicrophoneRecordingService Destroyed" }
        super.onDestroy()
    }


    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Uttra Recording", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for active voice recording"
        }
        getSystemService((NotificationManager::class.java)).createNotificationChannel(channel)
    }


    private fun createRecordingNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Uttra")
            .setContentText("Uttra microphone service is running").setContentIntent(pendingIntent)
            .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            ).build()
    }

    private fun promoteToForeground() {
        val notification = createRecordingNotification()

        val channel = getSystemService(
            NotificationManager::class.java
        ).getNotificationChannel(CHANNEL_ID)

        logd {
            "Channel exists=${channel != null}, " + "ChannelId=${notification.channelId}, " + "SmallIcon=${notification.smallIcon}"
        }

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        logd { "Uttra foreground promotion returned successfully" }

        Handler(Looper.getMainLooper()).postDelayed({
            logNotificationStatus()
        }, 1000L)

    }

    private fun logNotificationStatus() {
        val manager = getSystemService(
            NotificationManager::class.java
        )

        val channel = manager.getNotificationChannel(CHANNEL_ID)

        logd {
            """
        Notification diagnostics:
        Notifications enabled = ${manager.areNotificationsEnabled()}
        Channel importance = ${channel?.importance}
        Active notification IDs = ${
                manager.activeNotifications.map { it.id }
            }
        """.trimIndent()
        }
    }
}