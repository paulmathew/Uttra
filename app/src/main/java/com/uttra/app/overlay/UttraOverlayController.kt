package com.uttra.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.uttra.app.core.logging.logd
import com.uttra.app.core.logging.loge

class UttraOverlayController(
    private val context: Context,
    private val recordingListener: OverlayRecordingListener
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var overlayView: android.view.View? = null

    private var currentState: OverlayState = OverlayState.Hidden
        private set

    private var overlayContainer: FrameLayout? = null


    private fun Int.dp(): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private var editorAvailable = false
    private var startPending = false
    private var completionPending = false
    private val handler = Handler(Looper.getMainLooper())
    private val processingCompletion = Runnable {
        onProcessingCompleted()
    }
    fun show() {

        if (overlayView != null) return
        val container = FrameLayout(context)

        val size = (56 * context.resources.displayMetrics.density).toInt()
        val bubble = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(112, 56, 170))
            }

            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)

            setOnClickListener {
                onMicrophoneClicked()
            }
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        try {
//                windowManager.addView(bubble, params)
//                overlayView = bubble
            windowManager.addView(container, params)

            overlayContainer = container
            overlayView = container
            updateState(OverlayState.Ready)
            logd { "Uttra bubble displayed" }
        } catch (e: Exception) {
            loge(e) { "Failed to display Uttra bubble" }
        }

    }


    fun hide() {
        val view = overlayView ?: return

        try {
            handler.removeCallbacks(processingCompletion)
            windowManager.removeView(view)
            logd { "Uttra bubble removed" }
        } catch (e: IllegalArgumentException) {
            loge(e) { "Overlay was already detached" }
        } finally {
            overlayView = null
            overlayContainer = null
            startPending = false
            updateState(OverlayState.Hidden)

        }
    }

    private fun updateState(newState: OverlayState) {
        if (currentState == newState) return

        logd { "Overlay: $currentState -> $newState" }

        currentState = newState
        renderState()
    }

    fun onMicrophoneClicked() {
        if (currentState != OverlayState.Ready || startPending) {
            return
        }

        startPending = true

//        updateState(OverlayState.Recording)
        recordingListener.onStartRequested()
    }

    fun onConfirmClicked() {
        if (currentState != OverlayState.Recording || completionPending) return

        completionPending = true

        //updateState(OverlayState.Processing)
        recordingListener.onStopRequested()


//        handler.postDelayed({
//            onProcessingCompleted()
//        }, 1500L)
    }

    fun onCancelClicked() {
        if (currentState != OverlayState.Recording || completionPending) return

        completionPending = true

       // updateState(OverlayState.Ready)
        recordingListener.onCancelRequested()

    }

    private fun renderState() {
        val container = overlayContainer ?: return

        container.removeAllViews()

        when (currentState) {
            OverlayState.Ready -> renderReadyState(container)
            OverlayState.Recording -> renderRecordingState(container)
            OverlayState.Processing -> renderProcessingState(container)
            OverlayState.Hidden -> Unit
        }
    }


    private fun renderReadyState(container: FrameLayout) {
        resizeOverlay(56)

        val microphone = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setOnClickListener {
                onMicrophoneClicked()
            }
        }

        container.addView(
            microphone,
            FrameLayout.LayoutParams(
                56.dp(),
                56.dp()
            )
        )
    }


    private fun renderRecordingState(container: FrameLayout) {
        resizeOverlay(180)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancel = Button(context).apply {
            text = "×"
            setOnClickListener { onCancelClicked() }
        }

        val waveform = TextView(context).apply {
            text = "〰"
            gravity = Gravity.CENTER
        }

        val confirm = Button(context).apply {
            text = "✓"
            setOnClickListener { onConfirmClicked() }
        }

        row.addView(cancel, LinearLayout.LayoutParams(0, 56.dp(), 1f))
        row.addView(waveform, LinearLayout.LayoutParams(0, 56.dp(), 1f))
        row.addView(confirm, LinearLayout.LayoutParams(0, 56.dp(), 1f))

        container.addView(
            row,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
        )
    }


    private fun renderProcessingState(container: FrameLayout) {
        resizeOverlay(100)

        val label = TextView(context).apply {
            text = "Processing..."
            gravity = Gravity.CENTER
            textSize = 12f
        }

        container.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                56.dp()
            )
        )
    }


    fun onProcessingCompleted() {
        if (currentState != OverlayState.Processing) return

        updateState(OverlayState.Ready)
        onEditorAvailabilityChanged(editorAvailable)

    }


    private fun resizeOverlay(widthDp: Int) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams
            ?: return

        params.width = widthDp.dp()
        params.height = 56.dp()

        windowManager.updateViewLayout(view, params)
    }

    fun onEditorAvailabilityChanged(available: Boolean) {
        editorAvailable = available
//        logd {
//            "Editor availability: available=$available, state=$currentState"
//        }
        when (currentState) {
            OverlayState.Hidden -> {
                if (available) show()
            }

            OverlayState.Ready -> {
                if (!available) hide()
            }

            OverlayState.Recording,
            OverlayState.Processing -> {
                // Keep the controls visible for now.
                // Track editor availability separately.
            }
        }
    }

    fun onRecordingStarted() {
        startPending = false
        if (currentState != OverlayState.Ready) return

        updateState(OverlayState.Recording)
    }

    fun onRecordingStopped() {
        completionPending = false

        if (currentState != OverlayState.Recording) return

        updateState(OverlayState.Processing)

        handler.removeCallbacks(processingCompletion)
        handler.postDelayed(processingCompletion, 1500L)
    }

    fun onRecordingCancelled() {
        logd {
            "Cancel acknowledged: state=$currentState, " +
                    "editorAvailable=$editorAvailable"
        }

        completionPending = false

        if (currentState != OverlayState.Recording) return

        updateState(OverlayState.Ready)
        onEditorAvailabilityChanged(editorAvailable)
    }

    fun onRecordingFailed(message: String) {
        loge { "Recording failed: $message" }

        startPending = false
        completionPending = false

        if (currentState == OverlayState.Ready) return

        updateState(OverlayState.Ready)
        onEditorAvailabilityChanged(editorAvailable)
    }
}