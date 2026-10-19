
package com.uttra.app.onBoarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uttra.app.ui.theme.UttraTheme
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.uttra.app.R

private val PageBackground = Color(0xFFF5F8F7)
private val Ink = Color(0xFF14262B)
private val Muted = Color(0xFF617379)
private val Mint = Color(0xFF74E8BE)
private val SoftMint = Color(0xFFE7F3EE)

@Composable
fun PermissionScreen(
    status: PermissionStatus,
    onEnableAccessibility: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    val ready = status.canUseVoiceInput

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Uttra branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionBrandMark()

                Column {
                    Text(
                        text = "Uttra",
                        color = Ink,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "VOICE, EVERYWHERE",
                        color = Muted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Welcome hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF10252B),
                                Color(0xFF184941)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Text(
                    text = "GETTING STARTED",
                    color = Mint,
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Your voice is\nalmost ready.",
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "A couple of permissions, and you " +
                            "can start dictating wherever you type.",
                    color = Color(0xFFD0E0DC),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }

            // Section heading
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Set up Uttra",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )

                Text(
                    text = "2 required",
                    fontSize = 12.sp,
                    color = Muted
                )
            }

            // Permission cards
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                PermissionItem(
                    number = "01",
                    title = "Accessibility Service",
                    description = "Insert dictated text into other apps.",
                    granted = status.accessibilityEnabled,
                    optional = false,
                    onClick = onEnableAccessibility
                )

                PermissionItem(
                    number = "02",
                    title = "Microphone",
                    description = "Record speech for on-device transcription.",
                    granted = status.microphoneGranted,
                    optional = false,
                    onClick = onRequestMicrophone
                )

                PermissionItem(
                    number = "03",
                    title = "Notifications",
                    description = "Optional recording notifications.",
                    granted = status.notificationsGranted,
                    optional = true,
                    onClick = onRequestNotifications
                )
            }

            // Privacy message
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SoftMint,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = "✓",
                    color = Color(0xFF24775A),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Speech recognition runs locally " +
                            "on your device.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Ink
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Continue remains visible while content scrolls
        Button(
            onClick = onContinue,
            enabled = ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF183F39),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFD2DDD7),
                disabledContentColor = Muted
            )
        ) {
            Text(
                text = if (ready) {
                    "Continue to Uttra  →"
                } else {
                    "Enable required permissions"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(9.dp))

        Text(
            text = if (ready) {
                "All required permissions enabled"
            } else {
                "Accessibility and microphone access are required"
            },
            modifier = Modifier.fillMaxWidth(),
            color = Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PermissionItem(
    number: String,
    title: String,
    description: String,
    granted: Boolean,
    optional: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White,
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {

        Box(
            modifier = Modifier
                .size(39.dp)
                .background(
                    SoftMint,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color(0xFF24775A),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Muted
            )

            if (optional) {
                Text(
                    text = "OPTIONAL",
                    color = Color(0xFF24775A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (granted) {
            Text(
                text = "✓",
                color = Color(0xFF24775A),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        } else {
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 0.dp
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Ink
                ),
                border = BorderStroke(
                    1.dp,
                    Color(0xFFB8D6C8)
                )
            ) {
                Text(
                    text = "Enable",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PermissionBrandMark() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                Color(0xFF183F39),
                RoundedCornerShape(15.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(
                id = R.drawable.ic_uttra_mark
            ),
            contentDescription = null,
            modifier = Modifier.size(42.dp)
        )
    }
}

@Preview(
    name = "Uttra Setup - First Launch",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
private fun PermissionScreenFirstLaunchPreview() {
    UttraTheme {
        PermissionScreen(
            status = PermissionStatus(
                accessibilityEnabled = false,
                microphoneGranted = false,
                notificationsGranted = false
            ),
            onEnableAccessibility = {},
            onRequestMicrophone = {},
            onRequestNotifications = {},
            onContinue = {}
        )
    }
}

@Preview(
    name = "Uttra Setup - Ready",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
private fun PermissionScreenReadyPreview() {
    UttraTheme {
        PermissionScreen(
            status = PermissionStatus(
                accessibilityEnabled = true,
                microphoneGranted = true,
                notificationsGranted = false
            ),
            onEnableAccessibility = {},
            onRequestMicrophone = {},
            onRequestNotifications = {},
            onContinue = {}
        )
    }
}
