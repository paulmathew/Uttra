package com.uttra.app.onBoarding

import android.R

data class PermissionStatus(
    val accessibilityEnabled: Boolean = false,
    val microphoneGranted: Boolean = false,
    val notificationsGranted: Boolean = false
){

    val canUseVoiceInput: Boolean
        get() = accessibilityEnabled && microphoneGranted
}
