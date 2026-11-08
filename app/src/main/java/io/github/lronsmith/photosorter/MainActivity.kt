package io.github.lronsmith.photosorter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import io.github.lronsmith.photosorter.ui.theme.PhotoSorterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhotoSorterTheme { Text("Photo Sorter") } }
    }
}
