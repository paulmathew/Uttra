package com.uttra.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uttra.app.audio.AndroidAudioRecorder
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import com.uttra.app.core.logging.logw
import com.uttra.app.onBoarding.PermissionChecker
import com.uttra.app.onBoarding.PermissionScreen
import com.uttra.app.onBoarding.PermissionViewModel
import com.uttra.app.recording.MicrophoneRecordingService
import com.uttra.app.ui.test.AudioRecorderTestScreen
import com.uttra.app.ui.theme.UttraTheme

class MainActivity : ComponentActivity() {

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                logd { "Microphone permission granted" }
                permissionViewModel.refreshPermissions()

            } else {
                logw { "Microphone permission denied" }
            }
        }
    private val audioRecorder by lazy {
        AndroidAudioRecorder(applicationContext)
    }
    private var isRecording by mutableStateOf(false)
    private var showTestScreen by mutableStateOf(false)


    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                logd { "Notification permission granted" }
            } else {
                logw { "Notification permission denied" }
            }

         //   startMicrophoneService()
            permissionViewModel.refreshPermissions()


        }
    private val permissionChecker by lazy {
        PermissionChecker(applicationContext)
    }

    private val permissionViewModel by viewModels<PermissionViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>
            ): T {
                return PermissionViewModel(
                    PermissionChecker(applicationContext)
                ) as T
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true
        setContent {
            UttraTheme {

                val status by permissionViewModel.permissionStatus
                    .collectAsStateWithLifecycle()
                if (!status.canUseVoiceInput || !showTestScreen) {

                    PermissionScreen(
                        status = status,
                        onEnableAccessibility = {
                            startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        },
                        onRequestMicrophone = {
                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        },
                        onContinue = {
                            if (status.canUseVoiceInput) {
                                showTestScreen = true
                            }
                            // Navigate to Uttra Home later
                        }
                    )
                }
                else {
                    AudioRecorderTestScreen(
                        isRecording = isRecording,
                        onStart = {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                audioRecorder.start()
                                    .onSuccess {
                                        isRecording = true
                                        logd { "Recording started" }
                                    }
                                    .onFailure {
                                        loge(it) { "Recording failed" }
                                    }
                            } else {
                                microphonePermissionLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        },
                        onStop = {
                            audioRecorder.stop()
                                .onSuccess { file ->
                                    isRecording = false
                                    logd {
                                        "Recording saved: ${file.absolutePath}, " +
                                                "size=${file.length()}"
                                    }
                                }
                                .onFailure {
                                    isRecording = false
                                    loge(it) { "Stop failed" }
                                }
                        },
                        onCancel = {
                            audioRecorder.cancel()
                            isRecording = false
                            logd { "Recording cancelled" }
                        },
                        onStartService = {
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                requestNotificationPermission()
                            } else {
                                startMicrophoneService()
                            }

                        },
                        onStopService = {
                            stopMicrophoneService()
                        },
                        onCancelService = {
                            cancelMicrophoneService()
                        },
                    )
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.refreshPermissions()

        val status = permissionChecker.checkPermissions()

        logd {
            "Permissions: " +
                    "Accessibility=${status.accessibilityEnabled}, " +
                    "Microphone=${status.microphoneGranted}, " +
                    "Notifications=${status.notificationsGranted}"
        }
    }
    override fun onDestroy() {
        audioRecorder.cancel()
        super.onDestroy()
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startMicrophoneService() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission()
            return
        }

        val intent = Intent(
            this,
            MicrophoneRecordingService::class.java
        ).apply {
            action = MicrophoneRecordingService.ACTION_START
        }

        try {
            ContextCompat.startForegroundService(this, intent)

            logd { "Foreground service start requested" }
        } catch (e: Exception) {
            loge(e) { "Failed to request foreground service" }
        }
    }

    private fun stopMicrophoneService() {
        val intent = Intent(
            this,
            MicrophoneRecordingService::class.java
        ).apply {
            action = MicrophoneRecordingService.ACTION_STOP
        }

        startService(intent)
        logd { "Foreground service stop requested" }
    }
    private fun cancelMicrophoneService() {
        val intent = Intent(
            this,
            MicrophoneRecordingService::class.java
        ).apply {
            action = MicrophoneRecordingService.ACTION_CANCEL
        }

        startService(intent)
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }


}