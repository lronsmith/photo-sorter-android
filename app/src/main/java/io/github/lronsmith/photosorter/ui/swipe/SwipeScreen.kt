package io.github.lronsmith.photosorter.ui.swipe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind
import io.github.lronsmith.photosorter.model.contentUri
import io.github.lronsmith.photosorter.trash.RequestLauncher
import io.github.lronsmith.photosorter.trash.rememberRequestLauncher
import io.github.lronsmith.photosorter.ui.theme.PsColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One full-bleed photo or video at a time: right keeps, left trashes, up defers. Undo and the
 * pending-trash chip float at the bottom; the end of the queue shows the session summary.
 */
@Composable
fun SwipeScreen(viewModel: SwipeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberRequestLauncher()
    val snackbar = remember { SnackbarHostState() }

    BackHandler(onBack = onBack)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.onMessageShown()
    }

    Box(Modifier.fillMaxSize().background(PsColors.Background)) {
        val current = state.current
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.done -> DonePanel(
                state = state,
                onTrashPending = { viewModel.onTrashPending(launcher) },
                onUndo = { viewModel.onUndo(launcher) },
                onBack = onBack,
            )
            current != null -> Sorting(state, current, viewModel, launcher)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 110.dp),
        )
    }
}

@Composable
private fun BoxScope.Sorting(
    state: SwipeUiState,
    item: MediaItem,
    viewModel: SwipeViewModel,
    launcher: RequestLauncher,
) {
    var progress by remember { mutableStateOf(SwipeProgress(0f, 0f)) }
    var muted by rememberSaveable { mutableStateOf(true) }
    // Bumped on every swipe so the card always resets, even when the next item is the same one
    // (e.g. "later" on the last item) or the ViewModel ignored the swipe.
    var swipes by remember { mutableIntStateOf(0) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Preload(state.upcoming, cardSize)

    SwipeCard(
        key = item.id to swipes,
        onSwiped = { direction ->
            swipes++
            viewModel.onSwipe(direction, launcher)
        },
        onDrag = { progress = it },
        modifier = Modifier.fillMaxSize().onSizeChanged { cardSize = it },
    ) {
        MediaCard(
            item = item,
            undecodable = item.id in state.undecodable,
            muted = muted,
            onToggleMute = { muted = !muted },
            onLoadFailed = { viewModel.onLoadFailed(item) },
        )
    }
    EdgeGlows(progress)
    TopBar(state.decided, state.total)
    BottomOverlay(
        item = item,
        state = state,
        muted = muted,
        onToggleMute = { muted = !muted },
        onUndo = { viewModel.onUndo(launcher) },
        onTrashPending = { viewModel.onTrashPending(launcher) },
    )
}

/** Warms Coil's memory cache for the next images at the card's size. Videos play through ExoPlayer, so they are skipped. */
@Composable
private fun Preload(items: List<MediaItem>, size: IntSize) {
    val context = LocalContext.current
    LaunchedEffect(items, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        items.filter { it.kind == MediaKind.IMAGE }.forEach {
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(it.contentUri())
                    .size(size.width, size.height)
                    .scale(Scale.FIT)
                    .build(),
            )
        }
    }
}

@Composable
private fun BoxScope.TopBar(decided: Int, total: Int) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(onSurface.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (total > 0) decided.toFloat() / total else 0f)
                    .background(onSurface),
            )
        }
        Text(
            text = "↑ LATER",
            color = onSurface.copy(alpha = 0.8f),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun BoxScope.BottomOverlay(
    item: MediaItem,
    state: SwipeUiState,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onUndo: () -> Unit,
    onTrashPending: () -> Unit,
) {
    val shade = PsColors.Background
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(shade.copy(alpha = 0f), shade.copy(alpha = 0.75f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatDateTaken(item.dateTaken),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${item.albumName} · ${item.displayName}",
                    color = PsColors.OnSurfaceDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.kind == MediaKind.VIDEO) MuteChip(muted, onToggleMute)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayChip(text = "↶ Undo", enabled = state.canUndo, onClick = onUndo)
            if (state.pendingCount > 0) {
                OverlayChip(
                    text = "🗑 ${state.pendingCount}",
                    onClick = onTrashPending,
                    color = PsColors.Trash.copy(alpha = 0.85f),
                    description = "Trash ${state.pendingCount} waiting items",
                )
            }
        }
    }
}

@Composable
private fun OverlayChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = PsColors.Background.copy(alpha = 0.45f),
    description: String? = null,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 13.sp,
        )
    }
}

/** Shows whether the video is muted; tapping it (or the video) toggles sound. */
@Composable
private fun MuteChip(muted: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(PsColors.Background.copy(alpha = 0.45f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "Sunday 4 Aug 2019" in the device locale and time zone. */
@Composable
private fun formatDateTaken(epochMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMM yyyy", locale) }
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
}

@Composable
private fun BoxScope.DonePanel(
    state: SwipeUiState,
    onTrashPending: () -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "All done",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            SessionCount(state.sessionKept, "kept", PsColors.KeepText)
            SessionCount(state.sessionTrashed, "trashed", PsColors.TrashText)
            SessionCount(state.pendingCount, "pending", PsColors.Warn)
        }
        if (state.pendingCount > 0) {
            Button(
                onClick = onTrashPending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PsColors.Trash,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Trash ${state.pendingCount} now") }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to albums", color = MaterialTheme.colorScheme.onSurface)
        }
        if (state.canUndo) {
            TextButton(onClick = onUndo) { Text("↶ Undo", color = PsColors.OnSurfaceDim) }
        }
    }
}

@Composable
private fun SessionCount(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = PsColors.OnSurfaceDim, fontSize = 13.sp)
    }
}
