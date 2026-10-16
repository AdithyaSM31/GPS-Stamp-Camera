package com.gpsstamp.camera.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpsstamp.camera.R
import com.gpsstamp.camera.gallery.MapsLink
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val titleFormat = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy hh:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewer(vm: GalleryViewModel, startIndex: Int, onBack: () -> Unit) {
    val photos by vm.photos.collectAsStateWithLifecycle()
    val list = photos ?: return
    // Deleting the last photo leaves nothing to show.
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) onBack() }
    if (list.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, list.lastIndex)) { list.size }
    val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
    var zoomed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    // Android hides photo GPS without this permission; ask once here so existing installs are covered too.
    var permissionResults by remember { mutableIntStateOf(0) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionResults++ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }
    val location by produceState<Pair<Double, Double>?>(null, current.id, permissionResults) {
        value = vm.location(current)
    }

    val deleteFailed = stringResource(R.string.delete_failed)
    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(titleFormat.format(Instant.ofEpochMilli(current.dateTakenMs).atZone(ZoneId.systemDefault())), fontSize = 16.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ViewerAction(Icons.Filled.Share, stringResource(R.string.share)) { sharePhoto(context, current.uri) }
                location?.let { (lat, lon) ->
                    ViewerAction(Icons.Filled.Map, stringResource(R.string.open_in_maps)) { openInMaps(context, lat, lon) }
                }
                ViewerAction(Icons.Filled.Delete, stringResource(R.string.delete)) { confirmDelete = true }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            key = { list[it].id },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            ZoomableImage(
                uri = list[page].uri,
                isCurrent = page == pager.currentPage,
                onZoomedChange = { if (page == pager.currentPage) zoomed = it },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val photo = current
                    scope.launch { if (!vm.delete(photo)) snackbar.showSnackbar(deleteFailed) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Color.White) }
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

private fun sharePhoto(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

private fun openInMaps(context: Context, lat: Double, lon: Double) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MapsLink.geoUri(lat, lon))))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MapsLink.webUrl(lat, lon))))
        } catch (e: ActivityNotFoundException) {
            // No maps app and no browser: nothing to open.
        }
    }
}
