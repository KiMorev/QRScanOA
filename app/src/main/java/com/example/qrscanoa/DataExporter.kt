package com.example.qrscanoa

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object DataExporter {
    /**
     * Создаёт Excel-файл с кодами и возвращает его Uri
     */
    fun exportToExcel(context: Context, codes: List<String>): Uri? {
        return try {
            // 1. Создание книги и листа
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Отчёты")

            // 2. Заполнение данными
            codes.forEachIndexed { index, code ->
                val row = sheet.createRow(index)
                row.createCell(0).setCellValue(code)
            }

            // 3. Сохранение во временный файл (cacheDir)
            val fileName = "reports_${System.currentTimeMillis()}.xlsx"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { output ->
                workbook.write(output)
                workbook.close()
            }

            // 4. Получение Uri через FileProvider
            val authority = context.packageName + ".fileprovider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Создаёт Intent для отправки Excel-файла по почте
     * и гарантирует передачу прав доступа к файлу
     */
    fun createEmailIntent(context: Context, fileUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_SUBJECT, "Список отчётов агента")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            // Грантим доступ к Uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            // ClipData для почтовых клиентов (в т.ч. Gmail)
            clipData = ClipData.newRawUri("Отчёты", fileUri)
        }
    }
}
