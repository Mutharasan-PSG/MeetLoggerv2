package com.example.meetloggerv2.core.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

class PdfDocumentExporter : DocumentExporter {

    override val mimeType: String = "application/pdf"
    override val fileExtension: String = ".pdf"

    override fun export(content: String, outputStream: OutputStream) {
        val document = PdfDocument()
        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points
        val marginLeft = 54f // 0.75 in margin
        val marginRight = 54f
        val marginTop = 54f
        val marginBottom = 54f
        val maxLineWidth = pageWidth - marginLeft - marginRight // 487f
        val lineHeight = 22f

        val primaryColor = Color.parseColor("#4361EE")
        val highlightBgColor = Color.argb((255 * 0.08f).toInt(), 0x43, 0x61, 0xEE)
        val textColorVal = Color.parseColor("#1E293B") // Slate 800

        // Paint definitions
        val paint = Paint().apply {
            color = textColorVal
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            isAntiAlias = true
        }
        val boldPaint = Paint(paint).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val primaryPaint = Paint(paint).apply {
            color = primaryColor
        }
        val boldPrimaryPaint = Paint(boldPaint).apply {
            color = primaryColor
        }
        val bgPaint = Paint().apply {
            color = highlightBgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var currentY = marginTop
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val lines = content.split("\n")
        var isDecisionsSection = false
        var isTranscriptionSection = false
        var consecutiveNewlines = 2

        for (line in lines) {
            val currentLine = line.trim()
            if (currentLine.isEmpty()) {
                if (consecutiveNewlines < 2) {
                    currentY += lineHeight
                    consecutiveNewlines++
                    // Page overflow check
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }
                continue
            }

            // Check for Main uppercase titles
            if ((currentLine == "SUMMARY OF THE CONTENT" || currentLine == "TRANSCRIPTION OF SPEAKERS") && !currentLine.contains("*")) {
                while (consecutiveNewlines < 2) {
                    currentY += lineHeight
                    consecutiveNewlines++
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }
                
                boldPrimaryPaint.textSize = 15f
                canvas.drawText(currentLine, marginLeft, currentY, boldPrimaryPaint)
                currentY += lineHeight * 1.5f
                boldPrimaryPaint.textSize = 11f
                
                consecutiveNewlines = 2
                isDecisionsSection = false
                isTranscriptionSection = currentLine == "TRANSCRIPTION OF SPEAKERS"
                continue
            }

            // Heading matching
            if (currentLine.startsWith("## ") || currentLine.startsWith("### ")) {
                val cleanHeader = currentLine.substringAfter("##").substringAfter("#").trim()
                val headerText = cleanHeader.replace(Regex("^[0-9]+\\.\\s*"), "").uppercase().trim()

                isDecisionsSection = headerText.contains("DECISIONS", ignoreCase = true)
                isTranscriptionSection = headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) || isTranscriptionSection

                while (consecutiveNewlines < 2) {
                    currentY += lineHeight
                    consecutiveNewlines++
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                val icon = when {
                    headerText.contains("SUMMARY", ignoreCase = true) -> "📝 "
                    headerText.contains("DECISIONS", ignoreCase = true) -> "🤝 "
                    headerText.contains("ACTIONS", ignoreCase = true) -> "⚡ "
                    headerText.contains("POINTS", ignoreCase = true) || headerText.contains("TAKEAWAYS", ignoreCase = true) -> "🎯 "
                    headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) -> "🗣️ "
                    else -> "💡 "
                }

                boldPrimaryPaint.textSize = 13f
                canvas.drawText(icon + headerText, marginLeft, currentY, boldPrimaryPaint)
                currentY += lineHeight * 1.3f
                boldPrimaryPaint.textSize = 11f

                consecutiveNewlines = 1
                continue
            }

            // List item matching
            val isBullet = currentLine.startsWith("* ") || currentLine.startsWith("- ")
            if (isBullet) {
                while (consecutiveNewlines < 1) {
                    currentY += lineHeight
                    consecutiveNewlines++
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                val bulletIcon = if (isDecisionsSection) "✓" else "•"
                val bulletIconText = "   $bulletIcon   "
                val bulletWidth = paint.measureText(bulletIconText)
                
                canvas.drawText(bulletIconText, marginLeft, currentY, boldPrimaryPaint)
                
                val cleanContent = currentLine.substring(2).trim()
                val remainingW = maxLineWidth - bulletWidth
                val words = cleanContent.split(" ").filter { it.isNotEmpty() }
                
                var currentLineWords = mutableListOf<String>()
                var isFirstLine = true
                
                for (word in words) {
                    // Check for inline brackets or bold words to strip or format inside test width
                    val cleanWord = word.replace("**", "").replace("[", "").replace("]", "")
                    val testLine = (currentLineWords + cleanWord).joinToString(" ")
                    val maxW = if (isFirstLine) remainingW else maxLineWidth
                    if (paint.measureText(testLine) > maxW) {
                        val x = if (isFirstLine) marginLeft + bulletWidth else marginLeft
                        drawJustifiedLine(canvas, currentLineWords, x, maxW, currentY, paint)
                        currentY += lineHeight
                        currentLineWords.clear()
                        isFirstLine = false
                        
                        if (currentY > pageHeight - marginBottom) {
                            document.finishPage(page)
                            pageNumber++
                            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                            canvas = page.canvas
                            currentY = marginTop
                        }
                    }
                    currentLineWords.add(cleanWord)
                }
                if (currentLineWords.isNotEmpty()) {
                    val x = if (isFirstLine) marginLeft + bulletWidth else marginLeft
                    canvas.drawText(currentLineWords.joinToString(" "), x, currentY, paint)
                    currentY += lineHeight
                }
                
                consecutiveNewlines = 1
            } else {
                // Detect speaker turn
                val isSpeakerLine = if (isTranscriptionSection) {
                    if (currentLine.startsWith("**")) {
                        val boldEnd = currentLine.indexOf("**", 2)
                        boldEnd != -1 && boldEnd < 80 && (currentLine.substring(2, boldEnd).trim().endsWith(":") || currentLine.startsWith(":", boldEnd + 2))
                    } else {
                        val colonIdx = currentLine.indexOf(":")
                        colonIdx > 0 && colonIdx < 70 && !currentLine.substring(0, colonIdx).contains("[") && !currentLine.substring(0, colonIdx).contains("]") && !currentLine.substring(0, colonIdx).contains("*")
                    }
                } else {
                    false
                }

                val requiredNewlines = if (isSpeakerLine) 2 else 1
                while (consecutiveNewlines < requiredNewlines) {
                    currentY += lineHeight
                    consecutiveNewlines++
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                if (isSpeakerLine) {
                    var speakerName = ""
                    var dialogue = ""
                    if (currentLine.startsWith("**")) {
                        val boldEnd = currentLine.indexOf("**", 2)
                        val inner = currentLine.substring(2, boldEnd).trim()
                        if (inner.endsWith(":")) {
                            speakerName = inner.removeSuffix(":").trim()
                            dialogue = currentLine.substring(boldEnd + 2).trim()
                        } else {
                            speakerName = inner
                            dialogue = currentLine.substring(boldEnd + 3).trim()
                        }
                    } else {
                        val colonIdx = currentLine.indexOf(":")
                        speakerName = currentLine.substring(0, colonIdx).trim()
                        dialogue = currentLine.substring(colonIdx + 1).trim()
                    }

                    // Render custom curvy tag chip background
                    val speakerLabel = "$speakerName:"
                    val tagTextW = boldPrimaryPaint.measureText(speakerLabel)
                    val paddingX = 6f
                    val paddingY = 3f
                    val rectLeft = marginLeft
                    val rectTop = currentY - paint.textSize - paddingY
                    val rectRight = marginLeft + tagTextW + (paddingX * 2)
                    val rectBottom = currentY + paddingY

                    canvas.drawRoundRect(RectF(rectLeft, rectTop, rectRight, rectBottom), 6f, 6f, bgPaint)
                    canvas.drawText(speakerLabel, marginLeft + paddingX, currentY, boldPrimaryPaint)

                    val startX = rectRight + 6f
                    val firstLineMax = maxLineWidth - (rectRight - marginLeft) - 6f
                    val words = dialogue.split(" ").filter { it.isNotEmpty() }
                    
                    var currentLineWords = mutableListOf<String>()
                    var isFirstLine = true

                    for (word in words) {
                        val cleanWord = word.replace("**", "").replace("[", "").replace("]", "")
                        val testLine = (currentLineWords + cleanWord).joinToString(" ")
                        val maxW = if (isFirstLine) firstLineMax else maxLineWidth
                        if (paint.measureText(testLine) > maxW) {
                            val x = if (isFirstLine) startX else marginLeft
                            drawJustifiedLine(canvas, currentLineWords, x, maxW, currentY, paint)
                            currentY += lineHeight
                            currentLineWords.clear()
                            isFirstLine = false

                            if (currentY > pageHeight - marginBottom) {
                                document.finishPage(page)
                                pageNumber++
                                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                                canvas = page.canvas
                                currentY = marginTop
                            }
                        }
                        currentLineWords.add(cleanWord)
                    }
                    if (currentLineWords.isNotEmpty()) {
                        val x = if (isFirstLine) startX else marginLeft
                        canvas.drawText(currentLineWords.joinToString(" "), x, currentY, paint)
                        currentY += lineHeight
                    }
                } else {
                    // Normal paragraph
                    val words = currentLine.split(" ").filter { it.isNotEmpty() }
                    var currentLineWords = mutableListOf<String>()
                    
                    for (word in words) {
                        val cleanWord = word.replace("**", "").replace("[", "").replace("]", "")
                        val testLine = (currentLineWords + cleanWord).joinToString(" ")
                        if (paint.measureText(testLine) > maxLineWidth) {
                            drawJustifiedLine(canvas, currentLineWords, marginLeft, maxLineWidth, currentY, paint)
                            currentY += lineHeight
                            currentLineWords.clear()

                            if (currentY > pageHeight - marginBottom) {
                                document.finishPage(page)
                                pageNumber++
                                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                                canvas = page.canvas
                                currentY = marginTop
                            }
                        }
                        currentLineWords.add(cleanWord)
                    }
                    if (currentLineWords.isNotEmpty()) {
                        canvas.drawText(currentLineWords.joinToString(" "), marginLeft, currentY, paint)
                        currentY += lineHeight
                    }
                }
                consecutiveNewlines = 1
            }
        }
        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    private fun drawJustifiedLine(
        canvas: android.graphics.Canvas,
        words: List<String>,
        startX: Float,
        maxW: Float,
        currentY: Float,
        paint: Paint
    ) {
        if (words.isEmpty()) return
        if (words.size == 1) {
            canvas.drawText(words[0], startX, currentY, paint)
            return
        }

        val totalWordsWidth = words.map { paint.measureText(it) }.sum()
        val totalSpaceWidth = maxW - totalWordsWidth
        val spaceWidth = totalSpaceWidth / (words.size - 1)

        var currentX = startX
        for (word in words) {
            canvas.drawText(word, currentX, currentY, paint)
            currentX += paint.measureText(word) + spaceWidth
        }
    }
}
