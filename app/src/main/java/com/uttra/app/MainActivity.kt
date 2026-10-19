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

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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

    private var hasCompletedOnboarding by mutableStateOf(false)

    private val onboardingPreferences by lazy {
        getPreferences(MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)


        hasCompletedOnboarding = onboardingPreferences.getBoolean(
            "onboarding_completed",
            false
        )


        // TEMPORARY: Debug-only automatic language detection benchmark.
        if (
            BuildConfig.DEBUG &&
            intent?.action == "com.uttra.app.action.DEBUG_LANGUAGE_DETECTION"
        ) {
            val benchmarkName =
                intent?.getStringExtra("benchmark_file")

            when (benchmarkName) {
                "english_01.m4a",
                "english_02.m4a",
                "english_03.m4a",
                "malayalam_01.m4a",
                "malayalam_02.m4a",
                "malayalam_03.m4a",
                "quiet_malayalam_01.m4a",
                "quiet_english_01.m4a",
                "live_ml_failed_03.m4a",
                "live_auto_rejected_04.m4a",
                "silence_01.m4a" -> {
                    logd {
                        "LANGUAGE DETECTION TRIGGER: $benchmarkName"
                    }

                    WhisperTestRunner.detectBenchmark(
                        context = applicationContext,
                        fileName = benchmarkName
                    )
                }

                else -> {
                    logw {
                        "LANGUAGE DETECTION: Invalid benchmark: $benchmarkName"
                    }
                }
            }
        }





        enableEdgeToEdge()
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true
        setContent {
            UttraTheme {

                val status by permissionViewModel.permissionStatus
                    .collectAsStateWithLifecycle()
                if (!status.canUseVoiceInput || !hasCompletedOnboarding) {
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
                                onboardingPreferences.edit()
                                    .putBoolean("onboarding_completed", true)
                                    .apply()

                                hasCompletedOnboarding = true
                            }
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