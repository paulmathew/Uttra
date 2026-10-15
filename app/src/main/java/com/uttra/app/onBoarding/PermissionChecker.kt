package com.uttra.app.onBoarding

import android.content.ComponentName
import android.content.Context
import com.uttra.app.accessibility.UttraAccessibilityService
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PermissionChecker(private val context: Context)  {

    fun checkPermissions(): PermissionStatus{
        return PermissionStatus(
            accessibilityEnabled = isAccessibilityEnabled(),
            microphoneGranted = isMicrophoneGranted(),
            notificationsGranted = isNotificationGranted()
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val targetService = ComponentName(
            context,
            UttraAccessibilityService::class.java
        )

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == targetService }
    }

    private fun isMicrophoneGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat
                .from(context)
                .areNotificationsEnabled()
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat
                    .from(context)
                    .areNotificationsEnabled()
    }

}