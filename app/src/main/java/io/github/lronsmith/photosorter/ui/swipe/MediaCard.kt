package io.github.lronsmith.photosorter.ui.swipe

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.github.lronsmith.photosorter.model.MediaItem
import io.github.lronsmith.photosorter.model.MediaKind
import io.github.lronsmith.photosorter.model.contentUri
import io.github.lronsmith.photosorter.ui.theme.PsColors
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * The media inside a swipe card, fitted (letterboxed) on a dark background. Images load with
 * Coil; videos autoplay on a loop while the screen is started (paused in the background), muted
 * per [muted], and a tap calls [onToggleMute]. A failed load or playback calls [onLoadFailed].
 * When [undecodable] is set, a placeholder with the file name is shown instead.
 */
@Composable
fun MediaCard(
    item: MediaItem,
    undecodable: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onLoadFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PsColors.Background), contentAlignment = Alignment.Center) {
        when {
            undecodable -> Placeholder(item.displayName)
            item.kind == MediaKind.IMAGE -> AsyncImage(
                model = item.contentUri(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                onError = { onLoadFailed() },
                modifier = Modifier.fillMaxSize(),
            )
            else -> VideoPlayer(item, muted, onToggleMute, onLoadFailed)
        }
    }
}

// PlayerView's resize mode, shutter colour and Compose surface workaround are @UnstableApi.
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(item: MediaItem, muted: Boolean, onToggleMute: () -> Unit, onLoadFailed: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentMuted by rememberUpdatedState(muted)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Created in an effect (not in remember) so an abandoned composition can't leak a player.
    // No audio focus handling (the default): a muted autoplay must not pause the user's music.
    DisposableEffect(context, lifecycle, item.id, item.kind) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = currentOnLoadFailed()
        }
        val created = ExoPlayer.Builder(context).build().apply {
            volume = if (currentMuted) 0f else 1f
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(listener)
            setMediaItem(PlayerMediaItem.fromUri(item.contentUri()))
            prepare()
        }
        // Autoplay only while the screen is started: adding the observer to a started lifecycle
        // delivers ON_START at once, so playback begins now or when the app returns.
        val lifecycleObserver = playWhileStarted { created.playWhenReady = it }
        lifecycle.addObserver(lifecycleObserver)
        player = created
        onDispose {
            player = null
            lifecycle.removeObserver(lifecycleObserver)
            created.removeListener(listener)
            created.release()
        }
    }

    LaunchedEffect(player, muted) { player?.volume = if (muted) 0f else 1f }

    val background = PsColors.Background.toArgb()
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(background)
                    setBackgroundColor(background)
                    // Recommended by Media3 for PlayerView hosted in a Compose AndroidView.
                    setEnableComposeSurfaceSyncWorkaround(true)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        // Transparent overlay for the tap, so it works whatever PlayerView does with touches.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = if (muted) "Unmute" else "Mute",
                    onClick = onToggleMute,
                ),
        )
    }
}

/** Calls [setPlaying] with `true` on ON_START and `false` on ON_STOP (Home, app switch, screen off). */
internal fun playWhileStarted(setPlaying: (Boolean) -> Unit) = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_START -> setPlaying(true)
        Lifecycle.Event.ON_STOP -> setPlaying(false)
        else -> Unit
    }
}

@Composable
private fun Placeholder(displayName: String) {
    Column(
        modifier = Modifier.testTag("placeholder").padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = PsColors.OnSurfaceDim,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = displayName,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
