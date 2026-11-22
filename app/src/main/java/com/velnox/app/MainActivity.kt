package com.velnox.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.velnox.app.navigation.VelnoxApp

class MainActivity : ComponentActivity() {

    private var imagePickerCallback: ((Uri?) -> Unit)? = null
    private var videoPickerCallback: ((Uri?) -> Unit)? = null

    // Register ActivityResult launcher strictly during Activity initialization before STARTED state.
    // Completely decouples image picking from Compose lifecycle and LocalActivityResultRegistryOwner.
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val cb = imagePickerCallback
        imagePickerCallback = null
        cb?.invoke(uri)
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val cb = videoPickerCallback
        videoPickerCallback = null
        cb?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VelnoxApp(
                onPickImage = { callback ->
                    imagePickerCallback = callback
                    try {
                        pickImageLauncher.launch("image/*")
                    } catch (_: Exception) {
                        imagePickerCallback = null
                    }
                },
                onPickVideo = { callback ->
                    videoPickerCallback = callback
                    try {
                        pickVideoLauncher.launch("video/*")
                    } catch (_: Exception) {
                        videoPickerCallback = null
                    }
                },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Clear focus so switching windows or returning to the app never automatically pops up the keyboard
        currentFocus?.clearFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            currentFocus?.clearFocus()
        }
    }
}
