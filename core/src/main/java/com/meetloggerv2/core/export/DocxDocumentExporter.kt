package com.meetloggerv2.core.export

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.OutputStream

class DocxDocumentExporter : DocumentExporter {

    override val mimeType: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    override val fileExtension: String = ".docx"

    override fun export(content: String, outputStream: OutputStream) {
        val document = XWPFDocument()
        val lines = content.split("\n")
        
        var isDecisionsSection = false
        var isTranscriptionSection = false
        var consecutiveNewlines = 2
        
        for (line in lines) {
            val currentLine = line.trim()
            if (currentLine.isEmpty()) {
                if (consecutiveNewlines < 2) {
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }
                continue
            }
            
            // Premium meeting header (single leading '#'): centered title plus a row of
            // highlighted chips (duration, members) inside a shaded card cell.
            if (currentLine.startsWith("# ") && !currentLine.startsWith("## ")) {
                val raw = currentLine.removePrefix("# ")
                val headParts = raw.split("|||")
                val titleText = headParts[0].trim()
                val chips = if (headParts.size > 1)
                    headParts[1].split("||").map { it.trim() }.filter { it.isNotEmpty() }
                else emptyList()

                while (consecutiveNewlines < 2) {
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }

                // Single-cell table acts as the subtle rounded "card" background.
                val table = document.createTable(1, 1)
                val cell = table.getRow(0).getCell(0)
                cell.color = "EEF1FE" // subtle primary tint

                val titlePara = cell.paragraphs[0]
                titlePara.alignment = ParagraphAlignment.CENTER
                val titleRun = titlePara.createRun()
                titleRun.isBold = true
                titleRun.fontSize = 22
                titleRun.color = "4361EE"
                titleRun.setText(titleText)

                if (chips.isNotEmpty()) {
                    val chipPara = cell.addParagraph()
                    chipPara.alignment = ParagraphAlignment.CENTER
                    val chipRun = chipPara.createRun()
                    chipRun.isBold = true
                    chipRun.fontSize = 11
                    chipRun.color = "4361EE"
                    chipRun.setText(chips.joinToString("    ") { "[ $it ]" })
                }

                val titleSpacer = document.createParagraph()
                titleSpacer.createRun().setText("")

                consecutiveNewlines = 2
                isDecisionsSection = false
                isTranscriptionSection = false
                continue
            }

            // Check for Main uppercase titles
            if ((currentLine == "SUMMARY OF THE MEETING" || currentLine == "SUMMARY OF THE CONTENT" || currentLine == "TRANSCRIPTION OF SPEAKERS") && !currentLine.contains("*")) {
                while (consecutiveNewlines < 2) {
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }

                val para = document.createParagraph()
                para.alignment = ParagraphAlignment.LEFT
                val run = para.createRun()
                run.isBold = true
                run.fontSize = 16
                run.color = "4361EE"
                run.setText(currentLine)
                
                val spacerPara = document.createParagraph()
                spacerPara.createRun().setText("")
                
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
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }
                
                val para = document.createParagraph()
                para.alignment = ParagraphAlignment.LEFT
                val run = para.createRun()
                run.isBold = true
                run.fontSize = 14
                run.color = "4361EE"
                
                val icon = when {
                    headerText.contains("SUMMARY", ignoreCase = true) -> "📝 "
                    headerText.contains("DECISIONS", ignoreCase = true) -> "🤝 "
                    headerText.contains("ACTIONS", ignoreCase = true) -> "⚡ "
                    headerText.contains("POINTS", ignoreCase = true) || headerText.contains("TAKEAWAYS", ignoreCase = true) -> "🎯 "
                    headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) -> "🗣️ "
                    else -> "💡 "
                }
                
                run.setText(icon + headerText)
                
                val spacerPara = document.createParagraph()
                spacerPara.createRun().setText("")
                
                consecutiveNewlines = 2
                continue
            }
            
            // List item matching
            val isBullet = currentLine.startsWith("* ") || currentLine.startsWith("- ")
            if (isBullet) {
                while (consecutiveNewlines < 1) {
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }
                
                val para = document.createParagraph()
                para.alignment = ParagraphAlignment.BOTH
                
                val bulletIcon = if (isDecisionsSection) "✓" else "•"
                val runBullet = para.createRun()
                runBullet.setText("\u00A0\u00A0$bulletIcon\u00A0\u00A0")
                
                val cleanContent = currentLine.substring(2).trim()
                writeFormattedTextRuns(para, cleanContent)
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
                    val para = document.createParagraph()
                    para.createRun().setText("")
                    consecutiveNewlines++
                }
                
                val para = document.createParagraph()
                para.alignment = ParagraphAlignment.BOTH
                
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
                    
                    // Style speaker tag in POI
                    val runSpeaker = para.createRun()
                    runSpeaker.isBold = true
                    runSpeaker.color = "4361EE"
                    runSpeaker.setText("\u00A0$speakerName:\u00A0")
                    try {
                        val domNode = runSpeaker.ctr.domNode
                        val doc = domNode.ownerDocument
                        
                        // Find or create w:rPr node
                        var rPrNode: org.w3c.dom.Node? = null
                        val childNodes = domNode.childNodes
                        for (idx in 0 until childNodes.length) {
                            val child = childNodes.item(idx)
                            if (child.nodeName == "w:rPr" || child.localName == "rPr") {
                                rPrNode = child
                                break
                            }
                        }
                        if (rPrNode == null) {
                            rPrNode = doc.createElementNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:rPr")
                            domNode.insertBefore(rPrNode, domNode.firstChild)
                        }
                        
                        // Find or create w:shd node
                        var shdNode: org.w3c.dom.Node? = null
                        val rPrChildren = rPrNode.childNodes
                        for (idx in 0 until rPrChildren.length) {
                            val child = rPrChildren.item(idx)
                            if (child.nodeName == "w:shd" || child.localName == "shd") {
                                shdNode = child
                                break
                            }
                        }
                        if (shdNode == null) {
                            shdNode = doc.createElementNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:shd")
                            rPrNode.appendChild(shdNode)
                        }
                        
                        // Set standard OpenXML shading fill color attribute
                        val element = shdNode as org.w3c.dom.Element
                        element.setAttribute("w:fill", "EEF1FE")
                    } catch (e: Exception) {
                        runSpeaker.setTextHighlightColor("lightGray")
                    }
                    
                    val runSpace = para.createRun()
                    runSpace.setText("\u00A0")
                    
                    writeFormattedTextRuns(para, dialogue)
                } else {
                    writeFormattedTextRuns(para, currentLine)
                }
                consecutiveNewlines = 1
            }
        }
        
        document.write(outputStream)
        document.close()
    }
    
    private fun writeFormattedTextRuns(paragraph: org.apache.poi.xwpf.usermodel.XWPFParagraph, text: String) {
        var i = 0
        while (i < text.length) {
            // Check for bold bracket tags: **[tag]**
            if (text.startsWith("**[", i)) {
                val endIdx = text.indexOf("]**", i + 3)
                if (endIdx != -1) {
                    val boldContent = text.substring(i + 2, endIdx + 1)
                    val run = paragraph.createRun()
                    run.isBold = true
                    run.color = "4361EE"
                    run.setText(boldContent)
                    i = endIdx + 3
                    continue
                }
            }
            
            // Check for plain bracket tags: [tag]
            if (text.startsWith("[", i)) {
                val endIdx = text.indexOf("]", i + 1)
                if (endIdx != -1) {
                    val tagContent = text.substring(i, endIdx + 1)
                    val run = paragraph.createRun()
                    run.isBold = true
                    run.color = "4361EE"
                    run.setText(tagContent)
                    i = endIdx + 1
                    continue
                }
            }
            
            // Check for bold text: **text**
            if (text.startsWith("**", i)) {
                val endIdx = text.indexOf("**", i + 2)
                if (endIdx != -1) {
                    val boldContent = text.substring(i + 2, endIdx)
                    val run = paragraph.createRun()
                    run.isBold = true
                    run.setText(boldContent)
                    i = endIdx + 2
                    continue
                }
            }
            
            // Standard characters
            val nextMarker = findNextMarker(text, i)
            val segment = text.substring(i, nextMarker)
            val run = paragraph.createRun()
            run.setText(segment)
            i = nextMarker
        }
    }
    
    private fun findNextMarker(text: String, startIdx: Int): Int {
        var minIdx = text.length
        listOf("**", "[", "*").forEach { marker ->
            val idx = text.indexOf(marker, startIdx)
            if (idx != -1 && idx < minIdx) {
                minIdx = idx
            }
        }
        return minIdx
    }
}
