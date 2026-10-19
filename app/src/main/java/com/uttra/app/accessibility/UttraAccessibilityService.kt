package com.uttra.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
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
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build

@SuppressLint("AccessibilityPolicy")
class UttraAccessibilityService: AccessibilityService(), OverlayRecordingListener {
    private lateinit var overlayController: UttraOverlayController
    private val handler = Handler(Looper.getMainLooper())
    private var activeSessionId: String? = null
    private var pendingTranscriptionSessionId: String? = null

    private data class EditorTarget(
        val node: AccessibilityNodeInfo,
        val packageName: String,
        val windowId: Int,
        val viewId: String?,
        val originalText: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private var editorTarget: EditorTarget? = null

    private fun captureEditorTarget(): EditorTarget? {
        val node = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return null

        if (!node.isEditable || !node.isEnabled || node.isPassword) {
            return null
        }

        val packageName = node.packageName?.toString()
            ?: return null

        return EditorTarget(
            node = node,
            packageName = packageName,
            windowId = node.windowId,
            viewId = node.viewIdResourceName,
          //  originalText = node.text?.toString().orEmpty()
            originalText = getActualEditorText(node),
            selectionStart = node.textSelectionStart,
            selectionEnd = node.textSelectionEnd
        )
    }

    private fun isOriginalEditorStillFocused(): Boolean {
        if (editorTargetInvalidated) return false
        val target = editorTarget ?: return false

        val current = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false

        return current.isEditable &&
                current.isEnabled &&
                !current.isPassword &&
                current == target.node &&
                current.packageName?.toString() == target.packageName &&
                current.windowId == target.windowId &&
                current.viewIdResourceName == target.viewId &&
              //  current.text?.toString().orEmpty() == target.originalText
                getActualEditorText(current) == target.originalText
    }

    private var editorTargetInvalidated = false


    private data class EmptyInputSnapshot(
        val packageName: String,
        val fieldId: Int,
        val inputType: Int
    )

    private var emptyInputSnapshot: EmptyInputSnapshot? = null

    private fun captureEmptyInputSnapshot(): EmptyInputSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null
        }

        val inputMethod = getInputMethod() ?: return null

        if (!inputMethod.currentInputStarted) return null

        val info = inputMethod.currentInputEditorInfo ?: return null
        val connection = inputMethod.currentInputConnection ?: return null

        val surrounding = connection.getSurroundingText(
            1024, 1024, 0
        ) ?: return null

        if (
            surrounding.text.isNotEmpty() ||
            surrounding.selectionStart != 0 ||
            surrounding.selectionEnd != 0
        ) {
            return null
        }

        return EmptyInputSnapshot(
            packageName = info.packageName ?: return null,
            fieldId = info.fieldId,
            inputType = info.inputType
        )
    }



    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayController = UttraOverlayController(this, recordingListener = this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            serviceInfo?.let { info ->
                info.flags = info.flags or
                        AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR

                setServiceInfo(info)

                logd {
                    "UTTRA INPUT: Accessibility input method enabled"
                }
            }
        }

        logd{ "Uttra Accessibility Service Connected" }

    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        monitorEditorFocusChange(event)
        monitorAppNavigation(event)
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
        pendingTranscriptionSessionId = null
        clearEditorSession()

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
        editorTargetInvalidated = false
        editorTarget = captureEditorTarget()
        emptyInputSnapshot = captureEmptyInputSnapshot()

        logd {
            "UTTRA INPUT SNAPSHOT: " +
                    "emptyEditor=${emptyInputSnapshot != null}"
        }
        logInputConnectionState("RECORDING_START")

        if (editorTarget == null) {
            logw {
                "UTTRA TARGET: No valid editable field"
            }

            overlayController.onRecordingFailed(
                "No editable field available"
            )
            return
        }

        logd {
            "UTTRA TARGET CAPTURED: " +
                    "package=${editorTarget?.packageName}"
        }

        val sessionId = UUID.randomUUID().toString()
        pendingTranscriptionSessionId = null
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

        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val transcriptionResult = resultCode ==
                        MicrophoneRecordingService.RESULT_TRANSCRIPTION_READY ||
                        resultCode == MicrophoneRecordingService.RESULT_TRANSCRIPTION_FAILED
                val expectedId = if (transcriptionResult) pendingTranscriptionSessionId
                                 else activeSessionId
                if (expectedId != sessionId) {
                    logw { "Ignoring stale result: code=$resultCode" }
                    return
                }
                when (resultCode) {
                    MicrophoneRecordingService.RESULT_STOPPED -> {
                        logd { "Overlay recording STOPPED" }
                        activeSessionId = null
                        // Original session ownership is established before receiving async results.
                        pendingTranscriptionSessionId = sessionId
                        overlayController.onRecordingStopped()
                    }
                    MicrophoneRecordingService.RESULT_FAILED -> {
                        activeSessionId = null
                        pendingTranscriptionSessionId = null
                        clearEditorSession()
                        overlayController.onRecordingFailed("Failed to stop recording")
                    }
                    MicrophoneRecordingService.RESULT_TRANSCRIPTION_READY -> {
                        val text = resultData?.getString(
                            MicrophoneRecordingService.EXTRA_TRANSCRIPTION_TEXT
                        )
                        handleTranscriptionReady(sessionId, text)
                    }
                    MicrophoneRecordingService.RESULT_TRANSCRIPTION_FAILED -> {
                        handleTranscriptionFailure(sessionId)
                    }
                }
            }
        }
        sendRecordingCommand(MicrophoneRecordingService.ACTION_STOP, receiver, sessionId)
    }

    private fun handleTranscriptionReady(sessionId: String, text: String?) {
        if (pendingTranscriptionSessionId != sessionId) return
        pendingTranscriptionSessionId = null

        if (text.isNullOrBlank()) {
            logw { "UTTRA CALLBACK: Empty transcription; offer fresh recording" }
            clearEditorSession()
            overlayController.onTranscriptionFailedRecordAgain()
            return
        }

        if (!text.isNullOrBlank()) {
            logd {
                "UTTRA CALLBACK RECEIVED: " +
                        "session=$sessionId, text='$text'"
            }
        } else {
            logw {
                "UTTRA CALLBACK: Empty transcription"
            }
        }

        val capturedTarget = editorTarget

        val currentEditor = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        logd {
            "UTTRA CURSOR: " +
                    "capturedStart=${capturedTarget?.selectionStart}, " +
                    "capturedEnd=${capturedTarget?.selectionEnd}, " +
                    "currentStart=${currentEditor?.textSelectionStart}, " +
                    "currentEnd=${currentEditor?.textSelectionEnd}"
        }

        val targetMatches = isOriginalEditorStillFocused()

        logd {
            "UTTRA TARGET VALIDATION: " +
                    if (targetMatches) "MATCH" else "MISMATCH"
        }


        if (targetMatches && !text.isNullOrBlank()) {
            logInputConnectionState("TRANSCRIPTION_READY")
            //val inserted = insertTranscriptionIntoFocusedEditor(text)

            val inserted = if (emptyInputSnapshot != null) {
                insertIntoEmptyEditorViaInputConnection(text)
            } else {
                insertTranscriptionIntoFocusedEditor(text)
            }

            if (inserted) {
                logd {
                    "UTTRA INSERTION PASSED"
                }
            } else {
                logw {
                    "UTTRA INSERTION FAILED OR BLOCKED"
                }
            }

        } else {
            logw {
                "UTTRA INSERTION SKIPPED: Invalid target or empty text"
            }
        }


        editorTarget = null
        editorTargetInvalidated = false
        emptyInputSnapshot = null
        clearEditorSession()
        overlayController.onProcessingCompleted()
    }

    private fun handleTranscriptionFailure(sessionId: String) {
        if (pendingTranscriptionSessionId != sessionId) return
        logw { "UTTRA CALLBACK: Transcription failed; offer fresh recording" }
        pendingTranscriptionSessionId = null
        // The old editor snapshot must not be used for another recording.
        // onStartRequested() will capture the current editor afresh.
        clearEditorSession()
        overlayController.onTranscriptionFailedRecordAgain()
    }

    private fun monitorEditorFocusChange(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            return
        }

        val target = editorTarget ?: return

        if (editorTargetInvalidated) return

        val focusedNode = event.source ?: return

        // Ignore focus events from non-editable controls.
        if (!focusedNode.isEditable) return

        val sameEditor =
            focusedNode == target.node &&
                    focusedNode.packageName?.toString() == target.packageName &&
                    focusedNode.windowId == target.windowId

        if (!sameEditor) {
            editorTargetInvalidated = true

            logw {
                "UTTRA TARGET INVALIDATED: Focus moved to another editor"
            }
        }
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
                        pendingTranscriptionSessionId = null
                        clearEditorSession()
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



    private fun insertTranscriptionIntoFocusedEditor(
        transcription: String
    ): Boolean {

        val target = editorTarget ?: return false

        if (!isOriginalEditorStillFocused()) {
            logw { "UTTRA INSERT: Original editor changed" }
            return false
        }

        val focusedNode = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false

        if (!focusedNode.isEditable ||
            !focusedNode.isEnabled ||
            focusedNode.isPassword
        ) {
            return false
        }

        val existingText = getActualEditorText(focusedNode)

        if (existingText != target.originalText) {
            logw { "UTTRA INSERT: Existing text changed" }
            return false
        }


        val cursorStart = focusedNode.textSelectionStart
        val cursorEnd = focusedNode.textSelectionEnd


        logd {
            "UTTRA INSERT DEBUG: " +
                    "originalLength=${target.originalText.length}, " +
                    "currentLength=${existingText.length}, " +
                    "captured=${target.selectionStart}/${target.selectionEnd}, " +
                    "current=$cursorStart/$cursorEnd, " +
                    "showingHint=${focusedNode.isShowingHintText}"
        }

        val rawText = focusedNode.text?.toString().orEmpty()
        val hintText = focusedNode.hintText?.toString().orEmpty()

        logd {
            "UTTRA WHATSAPP FIELD DEBUG: " +
                    "rawLength=${rawText.length}, " +
                    "hintLength=${hintText.length}, " +
                    "textMatchesHint=${hintText.isNotEmpty() && rawText == hintText}, " +
                    "textIsMessage=${rawText == "Message"}, " +
                    "showingHint=${focusedNode.isShowingHintText}"
        }

        val hasStableCursor =
            cursorStart >= 0 &&
                    cursorStart == cursorEnd &&
                    cursorStart == target.selectionStart &&
                    cursorEnd == target.selectionEnd &&
                    cursorStart <= existingText.length

        val hasUnavailableCursorInEmptyField =
            existingText.isEmpty() &&
                    target.originalText.isEmpty() &&
                    target.selectionStart == -1 &&
                    target.selectionEnd == -1 &&
                    cursorStart == -1 &&
                    cursorEnd == -1

        if (!hasStableCursor && !hasUnavailableCursorInEmptyField) {
            logw {
                "UTTRA INSERT: Cursor changed or selection unsupported"
            }
            return false
        }

        val insertionPosition =
            if (hasUnavailableCursorInEmptyField) {
                logd {
                    "UTTRA INSERT: Empty editor with unavailable cursor, using position 0"
                }
                0
            } else {
                cursorStart
            }


        val spokenText = transcription.trim()
        if (spokenText.isEmpty()) return false

        val before = existingText.substring(0, insertionPosition)
        val after = existingText.substring(insertionPosition)

        val leadingSpace =
            if (before.isNotEmpty() && !before.last().isWhitespace()) {
                " "
            } else {
                ""
            }

        val trailingSpace =
            if (after.isNotEmpty() && !after.first().isWhitespace()) {
                " "
            } else {
                ""
            }

        val insertedText = leadingSpace + spokenText + trailingSpace
        val updatedText = before + insertedText + after

        val newCursorPosition = before.length + insertedText.length

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                updatedText
            )
        }

        val inserted = focusedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            arguments
        )

        if (inserted) {
            val selectionArguments = Bundle().apply {
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                    newCursorPosition
                )
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                    newCursorPosition
                )
            }

            val selectionUpdated = focusedNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                selectionArguments
            )

            if (!selectionUpdated) {
                logw {
                    "UTTRA INSERT: Text inserted, cursor placement unavailable"
                }
            }

            logd {
                "UTTRA CURSOR INSERTION PASSED: position=$insertionPosition"
            }
        }

        return inserted
    }



    private fun getActualEditorText(
        node: AccessibilityNodeInfo
    ): String {
        return if (node.isShowingHintText) {
            ""
        } else {
            node.text?.toString().orEmpty()
        }
    }


    private fun monitorAppNavigation(
        event: AccessibilityEvent
    ) {
        val target = editorTarget ?: return

        if (
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        if (
            event.packageName?.toString() !=
            target.packageName
        ) {
            return
        }

        logd {
            "UTTRA NAVIGATION EVENT: " +
                    "windowId=${event.windowId}, " +
                    "originalWindowId=${target.windowId}, " +
                    "class=${event.className}"
        }
    }


    private fun logInputConnectionState(stage: String) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            logd {
                "UTTRA INPUT: stage=$stage, API unsupported"
            }
            return
        }

        val inputMethod = getInputMethod()
        val connection = inputMethod?.getCurrentInputConnection()
        val editorInfo = inputMethod?.getCurrentInputEditorInfo()

        val surroundingText = connection?.getSurroundingText(
            100,
            100,
            0
        )

        logd {
            "UTTRA INPUT: " +
                    "stage=$stage, " +
                    "started=${inputMethod?.getCurrentInputStarted()}, " +
                    "connectionAvailable=${connection != null}, " +
                    "package=${editorInfo?.packageName}, " +
                    "textLength=${surroundingText?.text?.length}, " +
                    "selectionStart=${surroundingText?.selectionStart}, " +
                    "selectionEnd=${surroundingText?.selectionEnd}"
        }
    }


    private fun insertIntoEmptyEditorViaInputConnection(
        transcription: String
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        val snapshot = emptyInputSnapshot ?: return false
        val target = editorTarget ?: return false

        if (!isOriginalEditorStillFocused()) {
            logw { "UTTRA INPUT: Original editor changed" }
            return false
        }

        val inputMethod = getInputMethod() ?: return false

        if (!inputMethod.currentInputStarted) return false

        val info = inputMethod.currentInputEditorInfo ?: return false
        val connection = inputMethod.currentInputConnection ?: return false

        if (
            info.packageName != snapshot.packageName ||
            info.packageName != target.packageName ||
            info.fieldId != snapshot.fieldId ||
            info.inputType != snapshot.inputType
        ) {
            logw { "UTTRA INPUT: Editor identity changed" }
            return false
        }

        val surrounding = connection.getSurroundingText(
            1024, 1024, 0
        ) ?: return false

        if (
            surrounding.text.isNotEmpty() ||
            surrounding.selectionStart != 0 ||
            surrounding.selectionEnd != 0
        ) {
            logw { "UTTRA INPUT: Editor content or cursor changed" }
            return false
        }

        val spokenText = transcription.trim()
        if (spokenText.isEmpty()) return false

        connection.commitText(spokenText, 1, null)

        logd {
            "UTTRA INPUT: Native commitText requested"
        }

        return true
    }


    private fun clearEditorSession() {
        editorTarget = null
        editorTargetInvalidated = false
        emptyInputSnapshot = null

        logd {
            "UTTRA SESSION: Editor state cleared"
        }
    }



}