package com.example.meetloggerv2.core.export

import java.io.OutputStream

class TxtDocumentExporter : DocumentExporter {
    override val mimeType: String = "text/plain"
    override val fileExtension: String = ".txt"

    override fun export(content: String, outputStream: OutputStream) {
        val lines = content.split("\n")
        var isDecisionsSection = false
        var isTranscriptionSection = false
        var consecutiveNewlines = 2
        val parsedLines = mutableListOf<String>()
        
        for (line in lines) {
            val currentLine = line.trim()
            if (currentLine.isEmpty()) {
                if (consecutiveNewlines < 2) {
                    parsedLines.add("")
                    consecutiveNewlines++
                }
                continue
            }
            
            // Check for Main uppercase titles
            if ((currentLine == "SUMMARY OF THE CONTENT" || currentLine == "TRANSCRIPTION OF SPEAKERS") && !currentLine.contains("*")) {
                while (consecutiveNewlines < 2) {
                    parsedLines.add("")
                    consecutiveNewlines++
                }
                parsedLines.add(currentLine)
                parsedLines.add("")
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
                    parsedLines.add("")
                    consecutiveNewlines++
                }
                
                val icon = when {
                    headerText.contains("SUMMARY", ignoreCase = true) -> "📝 "
                    headerText.contains("DECISIONS", ignoreCase = true) -> "🤝 "
                    headerText.contains("ACTIONS", ignoreCase = true) -> "⚡ "
                    headerText.contains("POINTS", ignoreCase = true) || headerText.contains("TAKEAWAYS", ignoreCase = true) -> "🎯 "
                    headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) -> "🗣️ "
                    else -> "💡 "
                }
                
                parsedLines.add(icon + headerText)
                parsedLines.add("")
                consecutiveNewlines = 2
                continue
            }
            
            // List item matching
            val isBullet = currentLine.startsWith("* ") || currentLine.startsWith("- ")
            if (isBullet) {
                while (consecutiveNewlines < 1) {
                    parsedLines.add("")
                    consecutiveNewlines++
                }
                val bulletIcon = if (isDecisionsSection) "✓" else "•"
                val cleanContent = currentLine.substring(2).trim().replace("**", "").replace("[", "").replace("]", "")
                val bulletLine = "  $bulletIcon $cleanContent"
                parsedLines.addAll(wrapAndJustify(bulletLine, 80))
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
                    parsedLines.add("")
                    consecutiveNewlines++
                }
                
                var cleanLine = currentLine.replace("**", "").replace("[", "").replace("]", "")
                if (isSpeakerLine) {
                    var speakerName = ""
                    var dialogue = ""
                    if (currentLine.startsWith("**")) {
                        val boldEnd = currentLine.indexOf("**", 2)
                        val inner = currentLine.substring(2, boldEnd).trim()
                        if (inner.endsWith(":")) {
                            speakerName = inner.removeSuffix(":").trim()
                            dialogue = currentLine.substring(boldEnd + 2).trim().replace("**", "").replace("[", "").replace("]", "")
                        } else {
                            speakerName = inner
                            dialogue = currentLine.substring(boldEnd + 3).trim().replace("**", "").replace("[", "").replace("]", "")
                        }
                    } else {
                        val colonIdx = currentLine.indexOf(":")
                        speakerName = currentLine.substring(0, colonIdx).trim()
                        dialogue = currentLine.substring(colonIdx + 1).trim().replace("**", "").replace("[", "").replace("]", "")
                    }
                    cleanLine = "$speakerName: $dialogue"
                }
                
                parsedLines.addAll(wrapAndJustify(cleanLine, 80))
                consecutiveNewlines = 1
            }
        }
        
        val formattedContent = parsedLines.joinToString("\n")
        outputStream.write(formattedContent.toByteArray(Charsets.UTF_8))
    }

    private fun wrapAndJustify(paragraphText: String, maxLineLen: Int): List<String> {
        val words = paragraphText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")
        
        val lines = mutableListOf<String>()
        val currentLineWords = mutableListOf<String>()
        var currentLen = 0
        
        for (word in words) {
            val wordLen = word.length
            val spaceNeeded = if (currentLineWords.isEmpty()) 0 else 1
            if (currentLen + spaceNeeded + wordLen > maxLineLen) {
                if (currentLineWords.isNotEmpty()) {
                    lines.add(justifyTextLine(currentLineWords, maxLineLen))
                    currentLineWords.clear()
                }
                currentLineWords.add(word)
                currentLen = wordLen
            } else {
                currentLineWords.add(word)
                currentLen += spaceNeeded + wordLen
            }
        }
        
        if (currentLineWords.isNotEmpty()) {
            lines.add(currentLineWords.joinToString(" "))
        }
        
        return lines
    }

    private fun justifyTextLine(words: List<String>, maxLineLen: Int): String {
        if (words.size == 1) return words[0]
        
        val totalWordsLen = words.sumOf { it.length }
        val totalSpacesNeeded = maxLineLen - totalWordsLen
        val gapsCount = words.size - 1
        
        val baseSpaces = totalSpacesNeeded / gapsCount
        val extraSpaces = totalSpacesNeeded % gapsCount
        
        val sb = java.lang.StringBuilder()
        for (i in 0 until words.size) {
            sb.append(words[i])
            if (i < gapsCount) {
                val spacesCount = baseSpaces + if (i < extraSpaces) 1 else 0
                sb.append(" ".repeat(spacesCount))
            }
        }
        return sb.toString()
    }
}
