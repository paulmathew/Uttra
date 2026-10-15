package com.uttra.app.overlay

sealed interface OverlayState {
    data object  Hidden: OverlayState
    data object Ready : OverlayState
    data object Recording : OverlayState
    data object Processing : OverlayState
}