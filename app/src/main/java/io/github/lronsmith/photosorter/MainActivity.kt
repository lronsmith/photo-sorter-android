package io.github.lronsmith.photosorter

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lronsmith.photosorter.ui.PhotoSorterApp
import io.github.lronsmith.photosorter.ui.theme.PhotoSorterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system-bar icons stay light even under a light system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as PhotoSorterApplication
        setContent { PhotoSorterTheme { PhotoSorterApp(app.container) } }
    }
}
