package com.gpsstamp.camera.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private sealed interface Screen {
    data object Camera : Screen
    data object Gallery : Screen
    data class Viewer(val index: Int) : Screen
}

/** Camera → gallery grid → full-screen viewer; system back steps back one screen. */
@Composable
fun AppNavigation() {
    var screen by remember { mutableStateOf<Screen>(Screen.Camera) }
    val gallery: GalleryViewModel = viewModel()

    BackHandler(enabled = screen != Screen.Camera) {
        screen = if (screen is Screen.Viewer) Screen.Gallery else Screen.Camera
    }

    when (val current = screen) {
        Screen.Camera -> CameraScreen(onOpenGallery = { screen = Screen.Gallery })
        Screen.Gallery -> GalleryScreen(
            vm = gallery,
            onBack = { screen = Screen.Camera },
            onOpen = { screen = Screen.Viewer(it) },
        )
        is Screen.Viewer -> PhotoViewer(vm = gallery, startIndex = current.index, onBack = { screen = Screen.Gallery })
    }
}
