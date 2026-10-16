package com.uttra.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge
import com.uttra.app.core.logging.logw
import com.uttra.app.overlay.OverlayRecordingListener
import com.uttra.app.overlay.UttraOverlayController
import com.uttra.app.recording.MicrophoneRecordingService
import java.util.UUID

@SuppressLint("AccessibilityPolicy")
class UttraAccessibilityService: AccessibilityService(), OverlayRecordingListener {
    private lateinit var overlayController: UttraOverlayController
    private val handler = Handler(Looper.getMainLooper())
    private var activeSessionId: String? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = UttraOverlayController(this, recordingListener = this)
        logd{ "Uttra Accessibility Service Connected" }

    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                updateOverlayVisibility()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                scheduleOverlayVisibilityUpdate()
            }
        }
    }

    override fun onInterrupt() {
        logd{"Accessibility Service interrupted"}
    }

    companion object{
        private const val TAG = "UttraAccessibility"
    }

    override fun onDestroy() {
        handler.removeCallbacks(visibilityUpdate)

        if (::overlayController.isInitialized) {
            overlayController.hide()
        }
        super.onDestroy()
    }

    private fun updateOverlayVisibility() {
        val focusedNode = rootInActiveWindow?.findFocus(
            AccessibilityNodeInfo.FOCUS_INPUT
        )

        val isEditable = focusedNode?.let { node ->
            node.isEditable &&
                    node.isEnabled &&
                    !node.isPassword
        } == true

        val keyboardVisible = isKeyboardVisible()

        val shouldShow = isEditable && keyboardVisible

//        logd {
//            "Editor focused: $isEditable, " +
//                    "Keyboard visible: $keyboardVisible, " +
//                    "Show overlay: $shouldShow"
//        }

        overlayController.onEditorAvailabilityChanged(shouldShow)

//        if (shouldShow) {
//            overlayController.show()
//        } else {
//            overlayController.hide()
//        }
    }
    private fun isKeyboardVisible(): Boolean {
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    }


    private val visibilityUpdate = Runnable {
        updateOverlayVisibility()
    }

    private fun scheduleOverlayVisibilityUpdate() {
        handler.removeCallbacks(visibilityUpdate)
        handler.postDelayed(visibilityUpdate, 150L)
    }


    override fun onStartRequested() {
        logd { "Overlay requested START recording" }
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        logd { "START requested for session=$sessionId" }

        val receiver = object : ResultReceiver(
            Handler(Looper.getMainLooper())
        ) {
            override fun onReceiveResult(
                resultCode: Int,
                resultData: Bundle?
            ) {
                if (activeSessionId != sessionId) {
                    logw { "Ignoring stale recording result" }
                    return
                }
                when (resultCode) {
                    MicrophoneRecordingService.RESULT_STARTED -> {
                        logd { "Overlay recording STARTED" }
                        overlayController.onRecordingStarted()
                    }

                    MicrophoneRecordingService.RESULT_FAILED -> {
                        logw { "Overlay recording START failed" }
                        activeSessionId = null
                        overlayController.onRecordingFailed(
                            "Microphone service failed to start"
                        )
                    }
                }
            }
        }

        val intent = Intent(
            this,
            MicrophoneRecordingService::class.java
        ).apply {
            action = MicrophoneRecordingService.ACTION_START
            putExtra(
                MicrophoneRecordingService.EXTRA_RESULT_RECEIVER,
                receiver
            )
            putExtra(
                MicrophoneRecordingService.EXTRA_SESSION_ID,
                sessionId
            )
        }

        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            loge(e) { "Unable to request microphone service" }

            if (activeSessionId == sessionId) {
                activeSessionId = null

                overlayController.onRecordingFailed(
                    e.javaClass.simpleName
                )
            }
        }
    }



    override fun onStopRequested() {
        logd { "Overlay requested STOP recording" }
        val sessionId = activeSessionId ?: run {
            overlayController.onRecordingFailed("No active recording session")
            return
        }

        val receiver = object : ResultReceiver(
            Handler(Looper.getMainLooper())
        ) {
            override fun onReceiveResult(
                resultCode: Int,
                resultData: Bundle?
            ) {
                if (activeSessionId != sessionId) {
                    logw { "Ignoring stale STOP result" }
                    return
                }
                when (resultCode) {
                    MicrophoneRecordingService.RESULT_STOPPED -> {
                        logd { "Overlay recording STOPPED" }
                        activeSessionId = null
                        overlayController.onRecordingStopped()
                    }

                    MicrophoneRecordingService.RESULT_FAILED -> {
                        activeSessionId = null
                        overlayController.onRecordingFailed(
                            "Failed to stop recording"
                        )
                    }
                }
            }
        }

        sendRecordingCommand(
            MicrophoneRecordingService.ACTION_STOP,
            receiver,
            sessionId = sessionId
        )
    }



    override fun onCancelRequested() {
        logd { "Overlay requested CANCEL recording" }
        val sessionId = activeSessionId ?: run {
            overlayController.onRecordingFailed(
                "No active recording session"
            )
            return
        }

        val receiver = object : ResultReceiver(
            Handler(Looper.getMainLooper())
        ) {
            override fun onReceiveResult(
                resultCode: Int,
                resultData: Bundle?
            ) {
                if (activeSessionId != sessionId) {
                    logw { "Ignoring stale CANCEL result" }
                    return
                }
                when (resultCode) {
                    MicrophoneRecordingService.RESULT_CANCELLED -> {
                        logd { "Overlay recording CANCELLED" }
                        activeSessionId = null
                        overlayController.onRecordingCancelled()
                    }

                    MicrophoneRecordingService.RESULT_FAILED -> {
                        activeSessionId = null
                        overlayController.onRecordingFailed(
                            "Failed to cancel recording"
                        )
                    }
                }
            }
        }

        sendRecordingCommand(
            MicrophoneRecordingService.ACTION_CANCEL,
            receiver,
            sessionId = sessionId
        )
    }



    private fun sendRecordingCommand(
        action: String,
        receiver: ResultReceiver,
        sessionId: String,

        ) {
        val intent = Intent(
            this,
            MicrophoneRecordingService::class.java
        ).apply {
            this.action = action

            putExtra(
                MicrophoneRecordingService.EXTRA_RESULT_RECEIVER,
                receiver
            )
            putExtra(
                MicrophoneRecordingService.EXTRA_SESSION_ID,
                sessionId
            )
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            loge(e) { "Failed to send recording command: $action" }
            activeSessionId = null
            overlayController.onRecordingFailed(
                e.javaClass.simpleName
            )
        }
    }



}