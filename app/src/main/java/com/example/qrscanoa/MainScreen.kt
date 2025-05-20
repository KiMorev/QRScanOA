package com.example.qrscanoa

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrscanoa.DataExporter
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.qrscanoa.ui.theme.QRScanOATheme
import com.example.qrscanoa.ui.components.QrItemCard
import androidx.compose.foundation.lazy.items

@Composable
fun MainScreen(viewModel: ScanViewModel) {
    QRScanOATheme {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning
    val codes = viewModel.codes
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 1) Камера-превью
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isScanning) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            val camProviderFut = ProcessCameraProvider.getInstance(ctx)
                            camProviderFut.addListener({
                                val camProvider = camProviderFut.get()
                                val previewUseCase = Preview.Builder().build().also {
                                    it.setSurfaceProvider(surfaceProvider)
                                }
                                val scanner = BarcodeScanning.getClient(
                                    BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                )
                                val analysisUseCase = ImageAnalysis.Builder().build().also { analysis ->
                                    analysis.setAnalyzer(
                                        ContextCompat.getMainExecutor(ctx)
                                    ) { imageProxy: ImageProxy ->
                                        processImageProxy(scanner, imageProxy, viewModel)
                                    }
                                }
                                camProvider.unbindAll()
                                camProvider.bindToLifecycle(
                                    ctx as ComponentActivity,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    previewUseCase,
                                    analysisUseCase
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Сканирование остановлено",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // 2) Информационное сообщение
        Text(
            text = "Всего отчётов: ${codes.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
        )

        // 3) Список с нумерацией и Scrollbar
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 56.dp) // чтобы футер не перекрывался
            ) {
                items(
                    items = codes,
                    key = { code -> code }         // <-- здесь важно
                ) { code ->
                    QrItemCard(
                        code = code,
                        onDelete = { viewModel.remove(code) }
                    )
                }
            }
        }

        // 4) Панель кнопок
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { viewModel.toggleScanning() }) {
                Text(if (isScanning) "Стоп" else "Старт")
            }
            Button(onClick = { viewModel.clearAll() }) {
                Text("Очистить")
            }
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
                                chooser.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(chooser)
                            } else {
                                Toast.makeText(context, "Не удалось сгенерировать файл", Toast.LENGTH_SHORT).show()
                            }
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier
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
   }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    viewModel: ScanViewModel
) {
    imageProxy.image?.let { mediaImage ->
        val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { bc -> bc.rawValue?.let(viewModel::addCode) }
            }
            .addOnCompleteListener { imageProxy.close() }
    } ?: imageProxy.close()
}
