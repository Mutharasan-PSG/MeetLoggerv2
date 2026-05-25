package com.example.meetloggerv2.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.content.Intent
import android.net.Uri

class DocumentExportManager(private val context: Context) {

    fun exportToPdf(content: String, outputStream: OutputStream) {
        val document = PdfDocument()
        val pageWidth = 500
        val pageHeight = 700
        val marginLeft = 40f
        val lineHeight = 20f
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        val boldPaint = Paint(paint).apply { typeface = Typeface.create("serif", Typeface.BOLD) }
        
        var currentY = 50f
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        
        val lines = content.split("\n")
        val maxLineWidth = pageWidth - 80f
        
        for (line in lines) {
            if (line.isBlank()) {
                currentY += lineHeight
                continue
            }
            
            val lineText = line.trim()
            if (lineText.startsWith("SUMMARY") || lineText.startsWith("TRANSCRIPTION")) {
                canvas.drawText(lineText, marginLeft, currentY, boldPaint)
                currentY += lineHeight * 1.5f
                continue
            }
            
            val words = lineText.split(" ")
            val wordBuffer = mutableListOf<String>()
            for (word in words) {
                val testLine = (wordBuffer + word).joinToString(" ")
                if (paint.measureText(testLine) > maxLineWidth) {
                    canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, paint)
                    currentY += lineHeight
                    wordBuffer.clear()
                }
                wordBuffer.add(word)
                if (currentY > pageHeight - 50f) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    currentY = 50f
                }
            }
            if (wordBuffer.isNotEmpty()) {
                canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, paint)
                currentY += lineHeight
            }
        }
        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    fun exportToDocx(content: String, outputStream: OutputStream) {
        val document = XWPFDocument()
        content.split("\n").forEach { line ->
            val para = document.createParagraph()
            para.createRun().setText(line)
        }
        document.write(outputStream)
        document.close()
    }

    fun getShareIntent(file: File, format: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
