package com.uttra.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
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
    // Record Again is a visual substate of Processing; existing OverlayState stays unchanged.
    private var showingRecordAgain = false
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


            y = (-80).dp()
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
        if (newState != OverlayState.Processing) showingRecordAgain = false
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
            OverlayState.Processing -> {
                if (showingRecordAgain) renderRecordAgainState(container)
                else renderProcessingState(container)
            }
            OverlayState.Hidden -> Unit
        }
    }



    private fun renderReadyState(container: FrameLayout) {
        resizeOverlay(56)

        val logoButton = ImageView(context).apply {

            setImageResource(
                com.uttra.app.R.drawable.ic_uttra_mark
            )

            scaleType = ImageView.ScaleType.CENTER_INSIDE

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(18, 59, 53))
            }

            val inset = 7.dp()
            setPadding(inset, inset, inset, inset)

            contentDescription = "Start Uttra voice dictation"

            setOnClickListener {
                onMicrophoneClicked()
            }
        }

        container.addView(
            logoButton,
            FrameLayout.LayoutParams(
                56.dp(),
                56.dp(),
                Gravity.CENTER
            )
        )
    }



    private fun renderRecordingState(container: FrameLayout) {

        resizeOverlay(264)


        container.addView(
            UttraRecordingWaveView(context),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val cancel = TextView(context).apply {
            text = "\u00D7"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(205, 33, 80, 72))
            }
            contentDescription = "Cancel recording"
            setOnClickListener { onCancelClicked() }
        }

        val confirm = TextView(context).apply {
            text = "\u2713"
            textSize = 25f
            setTextColor(Color.rgb(18, 59, 53))
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(116, 232, 190))
            }
            contentDescription = "Finish recording and transcribe"
            setOnClickListener { onConfirmClicked() }
        }

        container.addView(
            cancel,
            FrameLayout.LayoutParams(
                44.dp(),
                44.dp(),
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply { leftMargin = 6.dp() }
        )
        container.addView(
            confirm,
            FrameLayout.LayoutParams(
                44.dp(),
                44.dp(),
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { rightMargin = 6.dp() }
        )
    }


    private fun renderProcessingState(container: FrameLayout) {
        // Visual change only. The service still owns transcription state and timing.
        resizeOverlay(216)

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp(), 0, 16.dp(), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28.dp().toFloat()
                setColor(Color.rgb(18, 59, 53))
            }
            contentDescription = "Uttra is transcribing"
        }

        val dots = UttraProcessingDotsView(context)
        pill.addView(
            dots,
            LinearLayout.LayoutParams(44.dp(), 28.dp())
        )

        val label = TextView(context).apply {
            text = "Transcribing\u2026"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setPadding(8.dp(), 0, 0, 0)
        }
        pill.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }


    /** Failed recognition: offer a fresh recording, never reprocess old audio. */
    fun onTranscriptionFailedRecordAgain() {
        if (currentState != OverlayState.Processing) return
        if (!editorAvailable) {
            onProcessingCompleted()
            return
        }
        showingRecordAgain = true
        renderState()
    }

    private fun onRecordAgainTapped() {
        if (currentState != OverlayState.Processing || !showingRecordAgain) return
        showingRecordAgain = false
        updateState(OverlayState.Ready)
        // Calls the existing safe start path, which captures a fresh editor target
        // and asks MicrophoneRecordingService to record into a new file/session.
        if (editorAvailable) {
            onMicrophoneClicked()
        } else {
            onEditorAvailabilityChanged(false)
        }
    }

    private fun onDismissRecordAgainTapped() {
        if (currentState != OverlayState.Processing || !showingRecordAgain) return
        showingRecordAgain = false
        onProcessingCompleted()
    }

    private fun renderRecordAgainState(container: FrameLayout) {
        resizeOverlay(336)

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 0, 6.dp(), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28.dp().toFloat()
                setColor(Color.rgb(18, 59, 53))
            }
        }

        val title = TextView(context).apply {
            text = "Couldn't transcribe"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 1
        }
        pill.addView(title, LinearLayout.LayoutParams(0, 56.dp(), 1f))

        val recordAgain = TextView(context).apply {
            text = "↻ Record again"
            textSize = 13f
            setTextColor(Color.rgb(18, 59, 53))
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20.dp().toFloat()
                setColor(Color.rgb(116, 232, 190))
            }
            contentDescription = "Discard failed recording and record a new one"
            setOnClickListener { onRecordAgainTapped() }
        }
        pill.addView(recordAgain, LinearLayout.LayoutParams(116.dp(), 42.dp()).apply {
            marginStart = 6.dp()
        })

        val close = TextView(context).apply {
            text = "×"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "Dismiss transcription failure"
            setOnClickListener { onDismissRecordAgainTapped() }
        }
        pill.addView(close, LinearLayout.LayoutParams(44.dp(), 44.dp()))

        container.addView(pill, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    fun onProcessingCompleted() {
        if (currentState != OverlayState.Processing) return

        showingRecordAgain = false
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
                // A fresh recording needs a visible editor; close the prompt otherwise.
                if (!available && showingRecordAgain) onDismissRecordAgainTapped()
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

//        handler.removeCallbacks(processingCompletion)
//        handler.postDelayed(processingCompletion, 1500L)
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