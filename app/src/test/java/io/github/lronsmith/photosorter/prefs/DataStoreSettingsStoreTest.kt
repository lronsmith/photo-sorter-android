package io.github.lronsmith.photosorter.prefs

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.lronsmith.photosorter.model.SortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsStoreTest {
    @get:Rule val tmpFolder = TemporaryFolder()

    // Okio storage rather than create { file }: the JVM file storage replaces the file with
    // File.renameTo, which fails on Windows once the file exists, so a second write would throw.
    // (Android's rename replaces the file; the app's preferencesDataStore is unaffected.)
    private fun TestScope.store() = DataStoreSettingsStore(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tmpFolder.root.resolve("s.preferences_pb").toOkioPath()
            },
            scope = backgroundScope,
        ),
    )

    @Test fun defaults() = runTest {
        val s = store()
        assertEquals(emptySet<String>(), s.excludedAlbumIds.first()); assertEquals(SortOrder.OLDEST_FIRST, s.order.first())
    }

    @Test fun excludeAndInclude() = runTest {
        val s = store()
        s.setAlbumIncluded("wa", false); s.setAlbumIncluded("dl", false); s.setAlbumIncluded("wa", true)
        assertEquals(setOf("dl"), s.excludedAlbumIds.first())
    }

    @Test fun orderPersists() = runTest {
        val s = store()
        s.setOrder(SortOrder.NEWEST_FIRST)
        assertEquals(SortOrder.NEWEST_FIRST, s.order.first())
    }
}
