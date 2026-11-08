package io.github.lronsmith.photosorter

import android.app.Application

/** Holds the process-wide [AppContainer], created on first use. */
class PhotoSorterApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
