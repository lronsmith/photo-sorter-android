package io.github.lronsmith.photosorter.ui.start

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.media.MediaAccess
import io.github.lronsmith.photosorter.model.SortOrder
import io.github.lronsmith.photosorter.model.contentUri
import io.github.lronsmith.photosorter.trash.rememberRequestLauncher
import io.github.lronsmith.photosorter.ui.theme.PsColors

private val TileShape = RoundedCornerShape(12.dp)

/** Album grid: pick albums and order, then Start or Find duplicates. Refreshes on every resume. */
@Composable
fun StartScreen(
    viewModel: StartViewModel,
    onStart: () -> Unit,
    onFindDuplicates: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val requestLauncher = rememberRequestLauncher()
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val permissions = remember { MediaAccess.permissionsToRequest(Build.VERSION.SDK_INT) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        val deniedAll = result.isNotEmpty() && result.values.none { it } // empty = request interrupted
        // Before any request the rationale flag is also false, so it means "don't ask again" only here.
        permanentlyDenied = deniedAll && activity != null &&
            permissions.none { activity.shouldShowRequestPermissionRationale(it) }
        viewModel.refresh()
    }
    val requestAccess = { permissionLauncher.launch(permissions) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = PsColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { Header(state, onOpenSettings) },
        bottomBar = {
            BottomBar(
                state = state,
                onOrder = viewModel::setOrder,
                onStart = onStart,
                onFindDuplicates = onFindDuplicates,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.access == AccessLevel.NONE && !state.loading -> NoAccess(
                    permanentlyDenied = permanentlyDenied,
                    onGrant = requestAccess,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
                state.loading && state.albums.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                }
                else -> {
                    if (state.access == AccessLevel.PARTIAL) {
                        Banner("Only photos you selected are visible · Change", onClick = requestAccess)
                    }
                    if (state.pendingCount > 0) {
                        Banner(
                            "${state.pendingCount} waiting to trash · Trash now",
                            onClick = { viewModel.onTrashPending(requestLauncher) },
                            color = PsColors.TrashText,
                        )
                    }
                    if (state.albums.isEmpty()) {
                        CenteredText("No photos or videos found.")
                    } else {
                        AlbumGrid(state.albums, onToggle = viewModel::toggleAlbum)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: StartUiState, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Choose albums",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.access != AccessLevel.NONE && (!state.loading || state.albums.isNotEmpty())) {
                Text(
                    "${state.selectedAlbums} of ${state.albums.size} selected · ${state.queueCount} photos & videos",
                    color = PsColors.OnSurfaceDim,
                    fontSize = 13.sp,
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(TileShape)
            .background(PsColors.SurfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun AlbumGrid(albums: List<AlbumRow>, onToggle: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.album.id }) { row -> AlbumTile(row, onToggle = { onToggle(row.album.id) }) }
    }
}

@Composable
private fun AlbumTile(row: AlbumRow, onToggle: () -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .aspectRatio(1f)
            .alpha(if (row.included) 1f else 0.45f)
            .clip(TileShape)
            .then(if (row.included) Modifier.border(2.dp, outline, TileShape) else Modifier)
            .background(PsColors.Surface)
            .toggleable(value = row.included, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        AsyncImage(
            model = row.album.cover.contentUri(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim so the white label stays readable on bright covers.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(Brush.verticalGradient(listOf(PsColors.Background.copy(alpha = 0f), PsColors.Background.copy(alpha = 0.85f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                row.album.name,
                color = outline,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${row.album.count}", color = outline, fontSize = 12.sp)
        }
        if (row.included) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = PsColors.Background,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(outline, CircleShape)
                    .padding(3.dp),
            )
        }
    }
}

@Composable
private fun BottomBar(
    state: StartUiState,
    onOrder: (SortOrder) -> Unit,
    onStart: () -> Unit,
    onFindDuplicates: () -> Unit,
) {
    Surface(color = PsColors.Surface) {
        Column {
            HorizontalDivider(color = PsColors.Outline)
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrderToggle(state.order, onOrder, Modifier.weight(1f))
                    Button(
                        onClick = onStart,
                        enabled = state.canStart,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = PsColors.Background,
                            disabledContainerColor = PsColors.SurfaceHigh,
                            disabledContentColor = PsColors.OnSurfaceDim,
                        ),
                        modifier = Modifier.weight(1.3f).height(48.dp),
                    ) {
                        Text("Start ›", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                OutlinedButton(
                    onClick = onFindDuplicates,
                    enabled = state.access != AccessLevel.NONE,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PsColors.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = PsColors.OnSurfaceDim,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Find duplicates", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun OrderToggle(order: SortOrder, onOrder: (SortOrder) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PsColors.SurfaceHigh)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(SortOrder.OLDEST_FIRST to "Oldest", SortOrder.NEWEST_FIRST to "Newest").forEach { (value, label) ->
            val selected = order == value
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onSurface) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onOrder(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) PsColors.Background else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun NoAccess(permanentlyDenied: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Photo Sorter needs access to your photos and videos to show your albums. " +
                "Nothing leaves your phone.",
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
        )
        if (permanentlyDenied) {
            Text(
                "Access was denied. Allow \"Photos and videos\" in the app's settings.",
                color = PsColors.OnSurfaceDim,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = PsColors.Background,
            ),
            modifier = Modifier.padding(top = 24.dp).height(48.dp),
        ) {
            Text(if (permanentlyDenied) "Open settings" else "Grant access", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(text, color = PsColors.OnSurfaceDim, textAlign = TextAlign.Center)
    }
}
