
package com.uttra.app.ui.home


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uttra.app.R
import com.uttra.app.ui.theme.UttraTheme

private val PageBackground = Color(0xFFF5F8F7)
private val Ink = Color(0xFF14262B)
private val Muted = Color(0xFF617379)
private val Mint = Color(0xFF74E8BE)

@Composable
fun UttraHomeScreen() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 22.dp,
                vertical = 22.dp
            ),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {

        // Branding

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UttraBrandMark()

                Column {
                    Text(
                        text = "uttra",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )

                    Text(
                        text = "VOICE, EVERYWHERE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = Muted
                    )
                }
            }

            UttraDemoMenu()
        }


        // Main hero card
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Row(
                modifier = Modifier
                    .background(
                        Color(0xFF275B4F),
                        RoundedCornerShape(50)
                    )
                    .padding(
                        horizontal = 13.dp,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            Mint,
                            CircleShape
                        )
                )

                Text(
                    text = "VOICE INPUT READY",
                    color = Mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "Your voice.\nYour words.\nAnywhere.",
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Speak naturally and turn your words " +
                        "into text, right where you're typing.",
                color = Color(0xFFD0E0DC),
                fontSize = 15.sp,
                lineHeight = 23.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                LanguageChip("English")
                LanguageChip("മലയാളം")
            }

            Text(
                text = "Automatic language detection · Beta",
                color = Color(0xFFB0CFC6),
                fontSize = 12.sp
            )
        }

        // Instructions
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "How it works",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(23.dp)
            ) {
                UttraStep(
                    number = "01",
                    title = "Open any app",
                    description = "Open WhatsApp, Notes, or any " +
                            "app where you want to type."
                )

                UttraStep(
                    number = "02",
                    title = "Tap the microphone",
                    description = "Select a text field and tap " +
                            "Uttra's floating microphone."
                )

                UttraStep(
                    number = "03",
                    title = "Speak and finish",
                    description = "Speak in English or Malayalam. " +
                            "Tap Done to transcribe and insert your text."
                )
            }
        }

        // Privacy information
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFFE6F2ED),
                    RoundedCornerShape(20.dp)
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Color(0xFFCEE9DA),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color(0xFF17684C),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "On-device transcription",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Ink
                )

                Text(
                    text = "Speech recognition runs locally " +
                            "on your device.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Muted
                )
            }
        }

        Text(
            text = "Automatic language detection is experimental " +
                    "and may occasionally select the wrong language.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Muted
        )
    }
}

@Composable
private fun UttraBrandMark() {
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

@Composable
private fun LanguageChip(label: String) {
    Box(
        modifier = Modifier
            .background(
                Color(0xFF31594F),
                RoundedCornerShape(50)
            )
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp
            )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UttraStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {

        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    Color(0xFFE7F3EE),
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = Color(0xFF24775A),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Text(
                text = description,
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}



private enum class DemoMenuPage(
    val title: String,
    val description: String
) {
    ABOUT(
        "About Uttra",
        "Uttra is an offline-first Android voice dictation " +
                "prototype. Speak in English or Malayalam and " +
                "insert the transcription into another app."
    ),
    TECHNOLOGY(
        "Built with",
        "Kotlin and Jetpack Compose\n\n" +
                "Whisper.cpp for English transcription\n\n" +
                "Sherpa-ONNX IndicConformer for Malayalam\n\n" +
                "Android Accessibility Service for text insertion"
    ),
    BETA(
        "Beta limitations",
        "Automatic English and Malayalam language detection " +
                "is experimental and may occasionally select " +
                "the wrong language.\n\n" +
                "Speech recognition runs on your device."
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UttraDemoMenu() {

    var selectedPage by remember {
        mutableStateOf<DemoMenuPage?>(null)
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Existing three-dot menu
    Box {
        IconButton(
            onClick = {
                selectedPage = DemoMenuPage.ABOUT
            }
        ) {
            Text(
                text = "⋮",
                fontSize = 28.sp,
                color = Ink
            )
        }


    }

    // Bottom sheet
    selectedPage?.let { currentPage ->

        ModalBottomSheet(
            onDismissRequest = {
                selectedPage = null
            },
            sheetState = sheetState,
            containerColor = PageBackground,
            scrimColor = Color.Black.copy(
                alpha = 0.55f
            ),
            shape = RoundedCornerShape(
                topStart = 30.dp,
                topEnd = 30.dp
            ),
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = Color(0xFFB8CCC4)
                )
            }
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 30.dp),
                verticalArrangement =
                    Arrangement.spacedBy(20.dp)
            ) {

                // Sheet header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISCOVER",
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium,
                            color = Muted
                        )

                        Text(
                            text = "Explore Uttra",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    }

                    IconButton(
                        onClick = {
                            selectedPage = null
                        }
                    ) {
                        Text(
                            text = "×",
                            fontSize = 30.sp,
                            color = Muted
                        )
                    }
                }

                // Navigation tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    DemoMenuPage.entries.forEach { page ->

                        val isSelected =
                            currentPage == page

                        val tabLabel = when (page) {
                            DemoMenuPage.ABOUT -> "About"
                            DemoMenuPage.TECHNOLOGY -> "Tech"
                            DemoMenuPage.BETA -> "Beta"
                        }

                        Surface(
                            onClick = {
                                selectedPage = page
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                Color(0xFF183F39)
                            } else {
                                Color(0xFFE1EBE6)
                            }
                        ) {
                            Text(
                                text = tabLabel,
                                modifier = Modifier.padding(
                                    vertical = 13.dp
                                ),
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) {
                                    Color.White
                                } else {
                                    Color(0xFF385A50)
                                }
                            )
                        }
                    }
                }

                // Selected page content

                // Selected page content
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        Text(
                            text = currentPage.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )

                        if (currentPage == DemoMenuPage.TECHNOLOGY) {

                            Column(
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                TechnologyItem(
                                    number = "01",
                                    title = "Kotlin + Jetpack Compose",
                                    description = "Native Android interface and animations"
                                )

                                TechnologyItem(
                                    number = "02",
                                    title = "Whisper.cpp + JNI",
                                    description = "Offline English speech recognition"
                                )

                                TechnologyItem(
                                    number = "03",
                                    title = "Sherpa-ONNX + IndicConformer",
                                    description = "Offline Malayalam speech recognition"
                                )

                                TechnologyItem(
                                    number = "04",
                                    title = "Accessibility Service",
                                    description = "Cross-app text insertion"
                                )
                            }

                        } else {

                            Text(
                                text = currentPage.description,
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                color = Muted
                            )
                        }
                    }
                }


                Text(
                    text = "Uttra Beta version",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun TechnologyItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    Color(0xFFE7F3EE),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF24775A)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Muted
            )
        }
    }
}




@Preview(
    name = "Uttra Home - Light",
    showBackground = true,
    showSystemUi = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
private fun UttraHomeScreenPreview() {
    UttraTheme {
        UttraHomeScreen()
    }
}