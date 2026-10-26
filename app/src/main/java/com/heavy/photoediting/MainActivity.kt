package com.heavy.photoediting

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.heavy.photoediting.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Camera : Screen()
    object Gallery : Screen()
    data class Editor(val uri: Uri, val tab: EditorTab) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO before publishing to EEA/UK/Switzerland: show the Google UMP consent form BEFORE initializing ads.
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@MainActivity, InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()) {
                // SDK is ready: load the first interstitial (on the main thread).
                android.os.Handler(android.os.Looper.getMainLooper()).post { InterstitialManager.preload() }
            }
        }
        setContent { HeavyTheme { App() } }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var pendingTab by remember { mutableStateOf(EditorTab.ADJUST) }
    // System Photo Picker: no storage permission required, works offline.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) screen = Screen.Editor(uri, pendingTab)
    }
    fun pick(tab: EditorTab) {
        pendingTab = tab
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    BackHandler(screen !is Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onCamera = { screen = Screen.Camera }, onPick = { pick(it) }, onGallery = { screen = Screen.Gallery })
        Screen.Camera -> CameraScreen(
            onCaptured = { screen = Screen.Editor(it, EditorTab.BEAUTY) },
            onPickGallery = { pick(EditorTab.ADJUST) }, onClose = { screen = Screen.Home })
        Screen.Gallery -> GalleryScreen(
            onOpen = { screen = Screen.Editor(it, EditorTab.ADJUST) }, onClose = { screen = Screen.Home })
        is Screen.Editor -> key(s.uri) {
            EditorScreen(s.uri, s.tab, onClose = { screen = Screen.Home }, onOpenGallery = { screen = Screen.Gallery })
        }
    }
}
