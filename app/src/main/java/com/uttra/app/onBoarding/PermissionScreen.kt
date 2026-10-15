package com.uttra.app.onBoarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Composable
fun PermissionScreen(
    status: PermissionStatus,
    onEnableAccessibility: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to Uttra",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Enable permissions to use voice dictation.")

        PermissionItem(
            title = "Accessibility Service",
            granted = status.accessibilityEnabled,
            onClick = onEnableAccessibility
        )

        PermissionItem(
            title = "Microphone",
            granted = status.microphoneGranted,
            onClick = onRequestMicrophone
        )

        PermissionItem(
            title = "Notifications (Optional)",
            granted = status.notificationsGranted,
            onClick = onRequestNotifications
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onContinue,
            enabled = status.canUseVoiceInput,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                text = if (granted) "Enabled" else "Not enabled",
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedButton(
            onClick = onClick,
            enabled = !granted
        ) {
            Text(if (granted) "Enabled" else "Enable")
        }
    }
}

@Preview
@Composable
fun PermissionScreenPreview() {
    PermissionScreen(
        status= PermissionStatus(accessibilityEnabled = false, microphoneGranted = false,notificationsGranted = false),
        onEnableAccessibility={},
        onRequestMicrophone={},
        onRequestNotifications={},
        onContinue={}
    )
}
