package com.uttra.app.overlay

interface OverlayRecordingListener {
    fun onStartRequested()
    fun onStopRequested()
    fun onCancelRequested()
}