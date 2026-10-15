
package com.uttra.app.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSessionControllerTest {

    @Test
    fun startRecording_setsStateToRecording() {
        val fakeRecorder = FakeAudioRecorder()

        val controller = RecordingSessionController(fakeRecorder)

        val result = controller.startRecording("session-A")

        assertTrue(result.isSuccess)

        assertEquals(
            RecordingState.RECORDING,
            controller.state
        )

        assertEquals(
            "session-A",
            controller.activeSessionId
        )

        assertEquals(1, fakeRecorder.startCount)
    }


    @Test
    fun duplicateStart_doesNotInterruptActiveRecording() {
        val fakeRecorder = FakeAudioRecorder()
        val controller = RecordingSessionController(fakeRecorder)

        val firstResult = controller.startRecording("session-A")
        val secondResult = controller.startRecording("session-B")

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isFailure)

        assertEquals(
            RecordingState.RECORDING,
            controller.state
        )

        assertEquals("session-A", controller.activeSessionId)
        assertEquals(1, fakeRecorder.startCount)
    }


    @Test
    fun mismatchedStop_doesNotStopActiveRecording() {
        val fakeRecorder = FakeAudioRecorder()
        val controller = RecordingSessionController(fakeRecorder)

        controller.startRecording("session-A")

        val result = controller.stopRecording("session-B")

        assertTrue(result.isFailure)
        assertEquals(RecordingState.RECORDING, controller.state)
        assertEquals("session-A", controller.activeSessionId)
        assertEquals(0, fakeRecorder.stopCount)
        assertTrue(fakeRecorder.isRecording)
    }


    @Test
    fun validCancel_releasesRecordingAndResetsState() {
        val fakeRecorder = FakeAudioRecorder()
        val controller = RecordingSessionController(fakeRecorder)

        controller.startRecording("session-A")

        val result = controller.cancelRecording("session-A")

        assertTrue(result.isSuccess)
        assertEquals(RecordingState.IDLE, controller.state)
        assertEquals(null, controller.activeSessionId)
        assertEquals(1, fakeRecorder.cancelCount)
        assertTrue(!fakeRecorder.isRecording)
    }


    @Test
    fun mismatchedCancel_doesNotInterruptActiveRecording() {
        val fakeRecorder = FakeAudioRecorder()
        val controller = RecordingSessionController(fakeRecorder)

        controller.startRecording("session-A")

        val result = controller.cancelRecording("session-B")

        assertTrue(result.isFailure)
        assertEquals(RecordingState.RECORDING, controller.state)
        assertEquals("session-A", controller.activeSessionId)
        assertEquals(0, fakeRecorder.cancelCount)
        assertTrue(fakeRecorder.isRecording)
    }

    @Test
    fun validStop_returnsFileAndResetsState() {
        val fakeRecorder = FakeAudioRecorder()
        val controller = RecordingSessionController(fakeRecorder)

        controller.startRecording("session-A")

        val result = controller.stopRecording("session-A")

        assertTrue(result.isSuccess)
        assertEquals("fake_recording.m4a", result.getOrThrow().name)
        assertEquals(RecordingState.IDLE, controller.state)
        assertNull(controller.activeSessionId)
        assertEquals(1, fakeRecorder.stopCount)
        assertFalse(fakeRecorder.isRecording)
    }


    @Test
    fun failedStart_doesNotCreateActiveSession() {
        val fakeRecorder = FakeAudioRecorder().apply {
            failOnStart = true
        }

        val controller = RecordingSessionController(fakeRecorder)

        val result = controller.startRecording("session-A")

        assertTrue(result.isFailure)
        assertEquals(RecordingState.IDLE, controller.state)
        assertNull(controller.activeSessionId)
        assertFalse(fakeRecorder.isRecording)
    }


    @Test
    fun failedStop_cleansUpRecording() {
        val fakeRecorder = FakeAudioRecorder().apply {
            failOnStop = true
        }

        val controller = RecordingSessionController(fakeRecorder)

        controller.startRecording("session-A")

        val result = controller.stopRecording("session-A")

        assertTrue(result.isFailure)
        assertEquals(RecordingState.IDLE, controller.state)
        assertNull(controller.activeSessionId)
        assertEquals(1, fakeRecorder.cancelCount)
        assertFalse(fakeRecorder.isRecording)
    }



}
