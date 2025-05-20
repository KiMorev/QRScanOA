// ScanViewModel.kt
package com.example.qrscanoa

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

class ScanViewModel : ViewModel() {
    // Режим сканирования
    val isScanning = mutableStateOf(false)

    // Уникальные коды
    private val _codes = mutableStateListOf<String>()
    val codes: List<String> get() = _codes

    // Добавить код, если его нет
    fun addCode(code: String) {
        if (!_codes.contains(code)) {
            _codes.add(code)
        }
    }

    // Очистить весь список
    fun clearAll() {
        _codes.clear()
    }

    // Удалить конкретный
    fun remove(code: String) {
        _codes.remove(code)
    }

    // Переключить режим
    fun toggleScanning() {
        isScanning.value = !isScanning.value
    }
}
