package com.gpsstamp.camera.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpsstamp.camera.R
import com.gpsstamp.camera.capture.CaptureStatus
import com.gpsstamp.camera.stamp.StampData
import com.gpsstamp.camera.stamp.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFFFC107)
private val Scrim = Color(0x99000000)
private val Danger = Color(0xE6C62828)

@Composable
fun CameraScreen(onOpenGallery: () -> Unit, vm: CameraViewModel = viewModel()) {
    // GPS runs only while the screen is visible: saves battery and forces a fresh fix after returning.
    LifecycleStartEffect(vm) {
        vm.onStart()
        onStopOrDispose { vm.onStop() }
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val zoomRange by vm.camera.zoomRange.collectAsStateWithLifecycle()
    val canSwitchLens by vm.camera.canSwitchLens.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val snackbar = remember { SnackbarHostState() }
    var editingNote by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ui.lensFacing) { vm.camera.bind(lifecycleOwner, previewView, ui.lensFacing) }
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }
    // Leaving for the gallery must release the camera, not keep it running behind another screen.
    DisposableEffect(Unit) { onDispose { vm.camera.unbind() } }

    // The UI is locked to portrait; track how the phone is really held so landscape shots save upright
    // and the preview stamp and icons turn to match.
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                val next = OrientationTracker.next(degrees, rotation)
                if (next != rotation) {
                    rotation = next
                    vm.camera.setTargetRotation(next)
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    var iconTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rotation) {
        iconTarget = OverlayRotation.nearestEquivalent(OverlayRotation.degreesFor(rotation), iconTarget)
    }
    val iconAngle by animateFloatAsState(iconTarget, tween(200), label = "iconAngle")

    Scaffold(containerColor = Color.Black, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                flashMode = ui.flashMode,
                showSwitch = canSwitchLens,
                iconAngle = iconAngle,
                onFlash = vm::cycleFlash,
                onSwitch = vm::toggleLens,
                onNote = { editingNote = true },
            )
            // Takes all spare height; the 3:4 preview (same shape as the photo) is centred inside it.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.aspectRatio(3f / 4f)) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    // An upright frame the shape of the photo as the user sees it, rotated to face them: the stamp
                    // sits on its bottom edge (the edge facing the ground) at the saved photo's proportions.
                    val landscape = OverlayRotation.isLandscape(rotation)
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .requiredSize(
                                width = if (landscape) maxHeight else maxWidth,
                                height = if (landscape) maxWidth else maxHeight,
                            )
                            .graphicsLayer { rotationZ = OverlayRotation.degreesFor(rotation) }
                    ) {
                        StampOverlay(ui.preview, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                        StatusBanner(ui.status, context, Modifier.align(Alignment.TopCenter).padding(12.dp))
                    }
                }
            }
            ZoomChips(
                zoom = ui.zoom,
                showWide = zoomRange.start <= 0.6f,
                showTele = zoomRange.endInclusive >= 2f,
                iconAngle = iconAngle,
                onZoom = vm::setZoom,
            )
            BottomBar(
                lastPhotoUri = ui.lastPhotoUri,
                enabled = ui.canCapture,
                capturing = ui.capturing,
                iconAngle = iconAngle,
                onShutter = vm::capture,
                onOpenGallery = onOpenGallery,
                context = context,
            )
        }
    }

    if (editingNote) {
        NoteDialog(
            initial = ui.note,
            onSave = {
                vm.setNote(it)
                editingNote = false
            },
            onDismiss = { editingNote = false },
        )
    }
}

@Composable
private fun TopBar(
    flashMode: Int,
    showSwitch: Boolean,
    iconAngle: Float,
    onFlash: () -> Unit,
    onSwitch: () -> Unit,
    onNote: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val flashIcon = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
            else -> Icons.Filled.FlashOff
        }
        val iconModifier = Modifier.rotate(iconAngle)
        IconButton(onClick = onFlash) { Icon(flashIcon, stringResource(R.string.cd_flash), iconModifier, tint = Color.White) }
        if (showSwitch) {
            IconButton(onClick = onSwitch) {
                Icon(Icons.Filled.Cameraswitch, stringResource(R.string.cd_switch_camera), iconModifier, tint = Color.White)
            }
        }
        IconButton(onClick = onNote) {
            Icon(Icons.Filled.EditNote, stringResource(R.string.cd_edit_note), iconModifier, tint = Color.White)
        }
    }
}

/** Same renderer as the saved photo, drawn at the preview's width. */
@Composable
private fun StampOverlay(data: StampData?, modifier: Modifier) {
    if (data == null) return
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val image = remember(data, widthPx) { StampRenderer.render(data, widthPx).asImageBitmap() }
        Image(image, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

@Composable
private fun StatusBanner(status: CaptureStatus, context: Context, modifier: Modifier) {
    val (text, background) = when (status) {
        CaptureStatus.Ready -> return
        is CaptureStatus.Waiting -> (status.accuracyM?.let { stringResource(R.string.waiting_for_gps_accuracy, it) }
            ?: stringResource(R.string.waiting_for_gps)) to Scrim
        CaptureStatus.MockDetected -> stringResource(R.string.mock_detected) to Danger
        CaptureStatus.LocationOff -> stringResource(R.string.location_off) to Danger
    }
    Row(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(background).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp)
        if (status == CaptureStatus.LocationOff) {
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text(stringResource(R.string.open_settings), color = Accent)
            }
        }
    }
}

@Composable
private fun ZoomChips(zoom: Float, showWide: Boolean, showTele: Boolean, iconAngle: Float, onZoom: (Float) -> Unit) {
    val options = listOfNotNull(0.6f.takeIf { showWide }, 1f, 2f.takeIf { showTele })
    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (option in options) {
            val selected = zoom == option
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) Accent else Scrim)
                    .clickable(role = Role.Button) { onZoom(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (option == 1f) "1x" else if (option == 2f) "2x" else "0.6x",
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.rotate(iconAngle),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    lastPhotoUri: Uri?,
    enabled: Boolean,
    capturing: Boolean,
    iconAngle: Float,
    onShutter: () -> Unit,
    onOpenGallery: () -> Unit,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LastPhoto(lastPhotoUri, context, iconAngle, onOpenGallery)
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.Gray)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.cd_shutter)) { onShutter() },
            contentAlignment = Alignment.Center,
        ) {
            if (capturing) CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
        }
        Spacer(Modifier.width(56.dp))
    }
}

@Composable
private fun LastPhoto(uri: Uri?, context: Context, iconAngle: Float, onOpenGallery: () -> Unit) {
    val thumbnail by produceState<ImageBitmap?>(null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { runCatching { loadThumbnail(context, it) }.getOrNull() } }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .rotate(iconAngle)
            .clip(RoundedCornerShape(10.dp))
            .background(Scrim)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_last_photo)) { onOpenGallery() },
    ) {
        thumbnail?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, Size(192, 192), null).asImageBitmap()
    } else {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 16 })?.asImageBitmap()
        }
    }
