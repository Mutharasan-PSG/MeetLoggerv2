package com.example.meetloggerv2.core.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

class PdfDocumentExporter : DocumentExporter {

    override val mimeType: String = "application/pdf"
    override val fileExtension: String = ".pdf"

    override fun export(content: String, outputStream: OutputStream) {
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
}
