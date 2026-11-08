package io.github.lronsmith.photosorter.media

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** How much of the library the app can see. PARTIAL = Android 14+ "selected photos only". */
enum class AccessLevel { FULL, PARTIAL, NONE }

/** Which media permissions to request per Android version, and what the granted set means. */
object MediaAccess {
    private const val TIRAMISU = 33
    private const val UPSIDE_DOWN_CAKE = 34

    fun permissionsToRequest(sdkInt: Int): Array<String> = when {
        sdkInt >= UPSIDE_DOWN_CAKE -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        sdkInt >= TIRAMISU -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else -> arrayOf(READ_EXTERNAL_STORAGE)
    }

    fun level(sdkInt: Int, granted: (String) -> Boolean): AccessLevel = when {
        sdkInt >= TIRAMISU && granted(READ_MEDIA_IMAGES) && granted(READ_MEDIA_VIDEO) -> AccessLevel.FULL
        sdkInt >= UPSIDE_DOWN_CAKE && granted(READ_MEDIA_VISUAL_USER_SELECTED) -> AccessLevel.PARTIAL
        sdkInt < TIRAMISU && granted(READ_EXTERNAL_STORAGE) -> AccessLevel.FULL
        else -> AccessLevel.NONE
    }

    fun level(context: Context): AccessLevel = level(Build.VERSION.SDK_INT) {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
}
