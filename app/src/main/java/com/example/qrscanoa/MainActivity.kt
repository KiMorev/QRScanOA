package com.example.qrscanoa

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.qrscanoa.ui.theme.QRScanOATheme

class MainActivity : ComponentActivity() {

    private val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private val REQUEST_PERMISSIONS = 1001

    // Получаем ViewModel через делегат
    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) Устанавливаем SplashScreen API и условие закрытия
        val splash: SplashScreen = installSplashScreen()

        splash.setKeepOnScreenCondition { false }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 2) Запрашиваем разрешение камеры
        if (ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION),
                REQUEST_PERMISSIONS
            )
        }

        // 3) Устанавливаем Compose-контент
        setContent {
            QRScanOATheme(
                            dynamicColor = false     // здесь отключаем dynamic color
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = scanViewModel)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val denied = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }
            if (denied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Нужно разрешение на камеру для сканирования",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
