package io.github.lronsmith.photosorter.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.ui.theme.PsColors

private const val SOURCE_URL = "https://github.com/lronsmith/photo-sorter-android"

/** Settings, design A: grouped Permissions / Library / Data / About sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenTrashed: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    // Permissions change in Android's settings, outside the app; re-read them on return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel("Permissions")
            Group {
                SettingRow(
                    title = "Photo & video access",
                    subtitle = when (state.access) {
                        AccessLevel.FULL -> "All photos"
                        AccessLevel.PARTIAL -> "Selected only"
                        AccessLevel.NONE -> "Not granted"
                    },
                    onClick = { context.openAppDetails() },
                ) {
                    when (state.access) {
                        AccessLevel.FULL -> Text("✓", color = PsColors.KeepText)
                        AccessLevel.PARTIAL -> Text("Change", color = PsColors.KeepText)
                        AccessLevel.NONE -> Text("Grant", color = PsColors.KeepText)
                    }
                }
                if (state.manageMediaSupported) {
                    RowDivider()
                    SettingRow(
                        title = "Media management",
                        subtitle = "Trash without asking each time",
                        onClick = { context.openManageMedia() },
                    ) {
                        Switch(
                            checked = state.instantTrash,
                            // The permission is only granted or revoked in Android's settings.
                            onCheckedChange = { context.openManageMedia() },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = PsColors.Keep,
                                uncheckedTrackColor = PsColors.Outline,
                                uncheckedBorderColor = PsColors.Outline,
                            ),
                        )
                    }
                }
            }

            SectionLabel("Library")
            Group {
                SettingRow(
                    title = "Trashed by this app",
                    subtitle = "Restore within ~30 days",
                    onClick = onOpenTrashed,
                ) {
                    Text("${state.trashedCount} ›", color = PsColors.OnSurfaceDim)
                }
            }

            SectionLabel("Data")
            Group {
                SettingRow(
                    title = "Reset decisions",
                    subtitle = "Show kept photos again",
                    titleColor = PsColors.TrashText,
                    onClick = { confirmReset = true },
                ) {
                    Text("›", color = PsColors.OnSurfaceDim)
                }
            }

            SectionLabel("About")
            Group {
                SettingRow(title = "Version") {
                    Text(state.versionLabel, color = PsColors.OnSurfaceDim)
                }
                RowDivider()
                SettingRow(title = "Source on GitHub", onClick = { context.openSource() }) {
                    Text("↗", color = PsColors.OnSurfaceDim)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset decisions?") },
            text = { Text("Kept photos will show up again. Trashed items stay in the trash.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.resetDecisions()
                }) { Text("Reset", color = PsColors.TrashText) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
            containerColor = PsColors.SurfaceHigh,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = PsColors.OnSurfaceDim,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = PsColors.SurfaceHigh)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = PsColors.OnSurfaceDim, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

private fun Context.packageUri(): Uri = Uri.fromParts("package", packageName, null)

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No app handles it (e.g. no browser); nothing useful to do.
    }
}

private fun Context.openAppDetails() =
    startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()))

private fun Context.openManageMedia() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        startSafely(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, packageUri()))
    }
}

/** The browser does the networking; the app itself has no INTERNET permission. */
private fun Context.openSource() = startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
