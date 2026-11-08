package io.github.lronsmith.photosorter.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.lronsmith.photosorter.media.TrashedItem
import io.github.lronsmith.photosorter.model.MediaKind
import io.github.lronsmith.photosorter.model.contentUri
import io.github.lronsmith.photosorter.trash.rememberRequestLauncher
import io.github.lronsmith.photosorter.ui.theme.PsColors

/** "Trashed by this app": a 3-column grid with days until purge, multi-select and Restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashedScreen(viewModel: TrashedViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberRequestLauncher()
    val snackbar = remember { SnackbarHostState() }

    // Items can be purged or restored elsewhere (e.g. the gallery's recycle bin) while away.
    LifecycleResumeEffect(viewModel) {
        viewModel.load()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Trashed by this app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                val count = state.selected.size
                Button(
                    onClick = {
                        if (count > 0) viewModel.restoreSelected(launcher) else viewModel.restoreAll(launcher)
                    },
                    enabled = !state.restoring,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PsColors.Keep),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        if (count > 0) "Restore $count selected" else "Restore all",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = PsColors.Keep)
                state.items.isEmpty() ->
                    Text(
                        "Nothing in the trash",
                        color = PsColors.OnSurfaceDim,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                else -> TrashedGrid(
                    items = state.items,
                    selected = state.selected,
                    onToggle = viewModel::toggle,
                    onSelectAll = viewModel::selectAll,
                )
            }
        }
    }
}

@Composable
private fun TrashedGrid(
    items: List<TrashedItem>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
) {
    val now = remember(items) { System.currentTimeMillis() }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Tap to select · auto-deleted after ~30 days",
                color = PsColors.OnSurfaceDim,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAll) {
                Text("Select all", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items, key = { it.item.id }) { trashed ->
                TrashedTile(
                    trashed = trashed,
                    daysLabel = daysLeftLabel(daysLeft(trashed.expiresAt, now)),
                    isSelected = trashed.item.id in selected,
                    onClick = { onToggle(trashed.item.id) },
                )
            }
        }
    }
}

@Composable
private fun TrashedTile(trashed: TrashedItem, daysLabel: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scrim = PsColors.Background.copy(alpha = 0.6f)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Checkbox, onClickLabel = if (isSelected) "Deselect" else "Select", onClick = onClick),
    ) {
        AsyncImage(
            model = trashed.item.contentUri(),
            contentDescription = trashed.item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) {
            // Drawn above the image so the outline isn't hidden by it.
            Box(Modifier.fillMaxSize().border(2.dp, onSurface, shape))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PsColors.Background, modifier = Modifier.size(14.dp))
            }
        }
        if (trashed.item.kind == MediaKind.VIDEO) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(scrim),
            )
        }
        Text(
            daysLabel,
            color = onSurface,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scrim)
                .padding(horizontal = 4.dp),
        )
    }
}
