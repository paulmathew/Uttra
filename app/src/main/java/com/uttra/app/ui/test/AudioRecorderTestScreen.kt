
package com.uttra.app.ui.test

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AudioRecorderTestScreen(
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onCancelService: () -> Unit // New

) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRecording)
                "Recording..."
            else
                "Ready to record"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onStart()
                },
                enabled = !isRecording
            ) {
                Text("Start")
            }

            Button(
                onClick = {
                    onStop()
                },
                enabled = isRecording
            ) {
                Text("Stop")
            }

            OutlinedButton(
                onClick = {
                    onCancel()

                },
                enabled = isRecording
            ) {
                Text("Cancel")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Foreground Service Test")

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onStartService) {
                Text("Start Service")
            }

            OutlinedButton(onClick = onStopService) {
                Text("Stop Service")
            }
            OutlinedButton(onClick = onCancelService) {
                Text("Cancel Service")
            }
        }
    }
}
