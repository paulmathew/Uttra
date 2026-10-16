package com.uttra.app

import android.Manifest
import android.content.Intent
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import com.uttra.app.core.logging.logw
import com.uttra.app.onBoarding.PermissionChecker
import com.uttra.app.onBoarding.PermissionScreen
import com.uttra.app.onBoarding.PermissionViewModel
import com.uttra.app.transcription.WhisperTestRunner
import com.uttra.app.ui.home.UttraHomeScreen
import com.uttra.app.ui.theme.UttraTheme
import com.uttra.app.transcription.TranscriptionLanguage
import com.uttra.app.transcription.IndicMalayalamSmokeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.uttra.app.transcription.IndicMalayalamTranscriber
import java.io.File
import com.uttra.app.transcription.UttraSpeechTranscriber

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

    private var showHomeScreen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

// TEMPORARY: Replay our saved Malayalam recording.


//        if (BuildConfig.DEBUG) {
//            lifecycleScope.launch(Dispatchers.IO) {
//
//                val benchmarkFile = File(
//                    applicationContext.filesDir,
//                    "speech_benchmarks/english_01.m4a"
//                )
//
//
//                val result = UttraSpeechTranscriber(
//                    applicationContext
//                ).transcribe(
//                    audioFile = benchmarkFile,
//                    language = TranscriptionLanguage.ENGLISH
//                )
//
//
//                result
//                    .onSuccess { text ->
//                        logd {
//                            "INDIC INTEGRATION PASSED: text='$text'"
//                        }
//                    }
//                    .onFailure { error ->
//                        loge(error) {
//                            "INDIC INTEGRATION FAILED"
//                        }
//                    }
//            }
//        }
//
//        WhisperTestRunner.replayBenchmark(
//            context = applicationContext,
//            fileName = "mixed_01.m4a",
//            language = TranscriptionLanguage.AUTO
//        )




        enableEdgeToEdge()
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true
        setContent {
            UttraTheme {

                val status by permissionViewModel.permissionStatus
                    .collectAsStateWithLifecycle()
                if (!status.canUseVoiceInput || !showHomeScreen) {
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
                                showHomeScreen = true
                            }
                            // Navigate to Uttra Home later
                        }
                    )
                } else {
                    UttraHomeScreen()

                }

            }
        }

    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.refreshPermissions()

    }

    override fun onDestroy() {
        super.onDestroy()
    }


}