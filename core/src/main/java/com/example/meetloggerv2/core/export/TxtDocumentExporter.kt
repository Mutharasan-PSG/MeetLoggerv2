package com.example.meetloggerv2.core.export

import java.io.OutputStream

class TxtDocumentExporter : DocumentExporter {
    override val mimeType: String = "text/plain"
    override val fileExtension: String = ".txt"

    override fun export(content: String, outputStream: OutputStream) {
        val paragraphs = content.split("\n")
        val formattedContent = paragraphs.flatMap { paragraph ->
            wrapAndJustify(paragraph, 80)
        }.joinToString("\n")
        
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
