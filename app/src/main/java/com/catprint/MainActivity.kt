package com.catprint

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.catprint.ui.nav.CatPrintNav
import com.catprint.ui.screens.AppViewModel
import com.catprint.ui.theme.CatPrintTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.catprint.core.printer.AppFonts.init(this)
        requestBlePermissions()
        handleIntent(intent)

        setContent {
            CatPrintTheme(mode = vm.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val logLines by vm.log.lines.collectAsState()
                    CatPrintNav(vm = vm, logLines = logLines)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Открытие .catdoc из файлового менеджера + приём картинок из «Поделиться». */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // Картинка из системного «Поделиться» -> в документ для настройки и печати
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: ""
            if (type.startsWith("image/")) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    vm.importAndAdd(uri)
                    return
                }
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    ?: emptyList()
            }
            if (uris.isNotEmpty()) {
                uris.forEach { vm.importAndAdd(it) }
                return
            }
        }
        val uri: Uri = intent.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val text = stream.bufferedReader().readText()
                val blocks = com.catprint.core.doc.DocSerializer.load(text)
                vm.blocks.clear()
                vm.blocks.addAll(blocks)
                vm.docName = uri.lastPathSegment ?: "Документ"
                vm.docPath = null
                vm.log.log("Открыто: ${vm.docName}")
                vm.refreshPreview()
            }
        } catch (e: Exception) {
            vm.log.log("Ошибка открытия: ${e.message}")
        }
    }

    private fun requestBlePermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
