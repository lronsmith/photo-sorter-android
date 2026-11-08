package io.github.lronsmith.photosorter

import android.content.Context
import android.os.Build
import io.github.lronsmith.photosorter.decisions.DecisionStore
import io.github.lronsmith.photosorter.decisions.PhotoSorterDb
import io.github.lronsmith.photosorter.decisions.RoomDecisionStore
import io.github.lronsmith.photosorter.media.AccessLevel
import io.github.lronsmith.photosorter.media.MediaAccess
import io.github.lronsmith.photosorter.media.MediaSource
import io.github.lronsmith.photosorter.media.MediaStoreSource
import io.github.lronsmith.photosorter.prefs.DataStoreSettingsStore
import io.github.lronsmith.photosorter.prefs.SettingsStore
import io.github.lronsmith.photosorter.prefs.settingsDataStore
import io.github.lronsmith.photosorter.trash.MediaStoreTrasher
import io.github.lronsmith.photosorter.trash.Trasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Everything the screens need; tests supply fakes. */
interface AppDeps {
    val media: MediaSource
    val decisions: DecisionStore
    val settings: SettingsStore
    val trasher: Trasher
    fun accessLevel(): AccessLevel
    val sdkInt: Int
    val versionLabel: String

    /** Outlives every screen, so decisions queued on the swipe screen are still written after it closes. */
    val appScope: CoroutineScope
}

/** The production dependencies, created once per process by [PhotoSorterApplication]. */
class AppContainer(context: Context) : AppDeps {
    private val appContext = context.applicationContext

    override val media: MediaSource = MediaStoreSource(appContext.contentResolver)
    override val decisions: DecisionStore = RoomDecisionStore(PhotoSorterDb.create(appContext))
    override val settings: SettingsStore = DataStoreSettingsStore(appContext.settingsDataStore())
    override val trasher: Trasher = MediaStoreTrasher.create(appContext)
    override fun accessLevel(): AccessLevel = MediaAccess.level(appContext)
    override val sdkInt: Int = Build.VERSION.SDK_INT
    override val versionLabel: String = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
