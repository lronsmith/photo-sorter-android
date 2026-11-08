package io.github.lronsmith.photosorter.ui.dupes

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.lronsmith.photosorter.dupes.DuplicateGroup
import io.github.lronsmith.photosorter.dupes.ScanProgress
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.contentUri
import io.github.lronsmith.photosorter.trash.rememberRequestLauncher
import io.github.lronsmith.photosorter.ui.plural
import io.github.lronsmith.photosorter.ui.theme.PsColors
import kotlinx.coroutines.launch

private val ThumbSize = 62.dp
private val ThumbShape = RoundedCornerShape(8.dp)

/** Finds byte-identical copies across the library (design B: one thumbnail strip per group). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(viewModel: DuplicatesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberRequestLauncher()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Scan on arrival; a ViewModel that already holds results (e.g. after recreation) keeps them.
    LaunchedEffect(viewModel) {
        if (viewModel.state.value == DuplicatesUiState.Idle) viewModel.scan()
    }

    val message = (state as? DuplicatesUiState.Results)?.message
    LaunchedEffect(message) {
        if (message != null) {
            // Shown from the screen scope: clearing the message restarts this effect.
            scope.launch { snackbar.showSnackbar(message) }
            viewModel.onMessageShown()
        }
    }

    val trashing = (state as? DuplicatesUiState.Results)?.trashing == true
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Duplicates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::scan,
                        enabled = state !is DuplicatesUiState.Scanning && !trashing,
                    ) { Text("↻ Rescan") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val results = state as? DuplicatesUiState.Results
            if (results != null && results.groups.isNotEmpty()) {
                Button(
                    onClick = { viewModel.trashAll(launcher) },
                    enabled = !results.trashing && results.copiesToTrash > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PsColors.Trash,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .height(52.dp),
                ) { Text("Trash ${plural(results.copiesToTrash, "copy", "copies")}", fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (val s = state) {
            DuplicatesUiState.Idle -> Centered(modifier) {
                Text("Scan stopped. Tap ↻ Rescan to start again.", color = PsColors.OnSurfaceDim)
            }
            is DuplicatesUiState.Scanning -> ScanningContent(s.progress, onCancel = viewModel::cancel, modifier = modifier)
            is DuplicatesUiState.Results -> ResultsContent(
                results = s,
                onKeep = viewModel::setKeeper,
                onShowLocation = { item ->
                    val where = listOf(item.albumName, item.relativePath).filter { it.isNotEmpty() }
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(where.joinToString(" · "))
                    }
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ScanningContent(progress: ScanProgress?, onCancel: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Centered(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (progress == null || progress.bytesTotal == 0L) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.bytesDone.toFloat() / progress.bytesTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (progress != null) {
                Text("${progress.filesDone} of ${plural(progress.filesTotal, "file", "files")}")
                Text(
                    "${Formatter.formatShortFileSize(context, progress.bytesDone)} of " +
                        Formatter.formatShortFileSize(context, progress.bytesTotal),
                    color = PsColors.OnSurfaceDim,
                    fontSize = 13.sp,
                )
            }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ResultsContent(
    results: DuplicatesUiState.Results,
    onKeep: (hash: String, itemId: Long) -> Unit,
    onShowLocation: (MediaItem) -> Unit,
    modifier: Modifier,
) {
    if (results.groups.isEmpty()) {
        Centered(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No duplicates found", fontSize = 18.sp)
                UnreadableNote(results.unreadableCount)
            }
        }
        return
    }
    val context = LocalContext.current
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item(key = "summary") {
            Column(Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Formatter.formatShortFileSize(context, results.bytesFreed),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${plural(results.copiesToTrash, "copy", "copies")} · ${plural(results.groups.size, "group", "groups")}",
                        color = PsColors.OnSurfaceDim,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Text("Tap a copy to keep it instead", color = PsColors.OnSurfaceDim, fontSize = 13.sp)
                UnreadableNote(results.unreadableCount)
            }
        }
        items(results.groups, key = { it.hash }) { group ->
            GroupStrip(
                group = group,
                enabled = !results.trashing,
                onKeep = { onKeep(group.hash, it) },
                onShowLocation = onShowLocation,
            )
        }
    }
}

@Composable
private fun UnreadableNote(count: Int) {
    if (count > 0) {
        Text("${plural(count, "file", "files")} couldn't be read", color = PsColors.Warn, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GroupStrip(
    group: DuplicateGroup,
    enabled: Boolean,
    onKeep: (itemId: Long) -> Unit,
    onShowLocation: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    val first = group.copies.first()
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            "${first.displayName} · ${Formatter.formatShortFileSize(context, first.sizeBytes)}",
            color = PsColors.OnSurfaceDim,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Room for the badges, which overhang the thumbnails.
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            items(group.copies, key = { it.id }) { item ->
                Thumbnail(
                    item = item,
                    isKeeper = item.id == group.keeperId,
                    enabled = enabled,
                    onClick = { onKeep(item.id) },
                    onLongClick = { onShowLocation(item) },
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(
    item: MediaItem,
    isKeeper: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .size(ThumbSize)
            .combinedClickable(
                enabled = enabled,
                onClickLabel = "Keep this copy",
                onLongClickLabel = "Show location",
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = item.contentUri(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ThumbSize)
                .alpha(if (isKeeper) 1f else 0.55f)
                .clip(ThumbShape)
                .background(PsColors.SurfaceHigh)
                .then(if (isKeeper) Modifier.border(2.dp, PsColors.Keep, ThumbShape) else Modifier),
        )
        if (isKeeper) {
            Text(
                "KEEP",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 5.dp)
                    .background(PsColors.Keep, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(PsColors.Trash, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
