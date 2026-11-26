package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.example.ui.ConsoleApp
import com.example.viewmodel.ArrangerViewModel

class MainActivity : ComponentActivity() {

    private lateinit var vm: ArrangerViewModel

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            vm.addRoot(uri)
        }
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen, screen stays on while you play.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vm = ViewModelProvider(this)[ArrangerViewModel::class.java]
        vm.onPickFolder = { pickFolder.launch(null) }
        vm.onPickFiles = { pickFiles.launch(arrayOf("*/*")) }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD9B66B),
                    secondary = Color(0xFF2E9BFF),
                    background = Color(0xFF08090B),
                    surface = Color(0xFF14161A)
                )
            ) {
                ConsoleApp(vm)
            }
        }
    }

    override fun onDestroy() {
        if (::vm.isInitialized) { vm.onPickFolder = null; vm.onPickFiles = null }
        super.onDestroy()
    }

    override fun onStop() {
        // Stop sounds and the accompaniment when the app is not on screen.
        if (::vm.isInitialized) vm.onBackground()
        super.onStop()
    }
}
