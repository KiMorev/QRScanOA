package com.example.qrscanoa

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background                // <- для .background()
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrscanoa.DataExporter
import com.example.qrscanoa.ScanViewModel
import com.example.qrscanoa.ui.components.QrItemCard
import com.example.qrscanoa.ui.theme.QRScanOATheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(viewModel: ScanViewModel) {
    QRScanOATheme {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // 1) Готовим провайдер и PreviewView
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
        val previewView = remember { PreviewView(context) }

        LaunchedEffect(cameraProviderFuture) {
            cameraProvider = cameraProviderFuture.get()
        }

        // 2) UI-стейты
        val isScanning by viewModel.isScanning
        var torchEnabled by remember { mutableStateOf(false) }
        var camera by remember { mutableStateOf<Camera?>(null) }

        // 3) (Re)bind use-cases при старте/стопе сканирования
        LaunchedEffect(isScanning, cameraProvider) {
            cameraProvider?.let { provider ->
                provider.unbindAll()
                if (isScanning) {
                    // Preview
                    val previewUseCase = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    // ML Kit
                    val scanner: BarcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )
                    // Анализ кадров
                    val analysisUseCase = ImageAnalysis.Builder().build().also { analysis ->
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy: ImageProxy ->
                            processImageProxy(scanner, imageProxy, viewModel)
                        }
                    }
                    // Привязываем к lifecycle
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        analysisUseCase
                    )
                }
            }
        }

        // 4) Основной Scaffold с bottomBar
        Scaffold(
            bottomBar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // фонарик
                    IconButton(onClick = {
                        torchEnabled = !torchEnabled
                        camera?.cameraControl?.enableTorch(torchEnabled)
                    }) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (torchEnabled) "Выключить фонарик" else "Включить фонарик"
                        )
                    }

                    // Старт/Стоп
                    Button(onClick = { viewModel.toggleScanning() }) {
                        Text(if (isScanning) "Стоп" else "Старт")
                    }
                    // Очистить
                    Button(onClick = { viewModel.clearAll() }) {
                        Text("Очистить")
                    }
                    // Экспорт
                    val codes = viewModel.codes
                    var isExporting by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    Button(
                        onClick = {
                            if (!isExporting) {
                                isExporting = true
                                scope.launch {
                                    val fileUri = withContext(Dispatchers.IO) {
                                        DataExporter.exportToExcel(context, codes)
                                    }
                                    if (fileUri != null) {
                                        val sendIntent = DataExporter.createEmailIntent(context, fileUri)
                                        val chooser = Intent.createChooser(sendIntent, "Отправить отчёты")
                                        chooser.flags =
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(chooser)
                                    } else {
                                        Toast.makeText(context, "Не удалось сгенерировать файл", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                    isExporting = false
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Экспорт…")
                        } else {
                            Text("Экспорт")
                        }
                    }
                }
            }
        ) { paddingValues ->
            // 5) Контент поверх padding от bottomBar
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize()) {
                    // 5a) Превью камеры или пустой фон
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (isScanning) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }
                        if (!isScanning) {
                            Text(
                                "Сканирование остановлено",
                                Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // 5b) Счётчик
                    Text(
                        "Всего отчётов: ${viewModel.codes.size}",
                        Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // 5c) Список QR
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        state = listState
                    ) {
                        items(viewModel.codes, key = { it }) { code ->
                            QrItemCard(code = code, onDelete = { viewModel.remove(code) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: BarcodeScanner,
    imageProxy: ImageProxy,
    viewModel: ScanViewModel
) {
    imageProxy.image?.let { mediaImage ->
        val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { it.rawValue?.let(viewModel::addCode) }
            }
            .addOnCompleteListener { imageProxy.close() }
    } ?: imageProxy.close()
}
