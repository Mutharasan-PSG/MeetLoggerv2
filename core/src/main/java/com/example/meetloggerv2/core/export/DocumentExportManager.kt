package com.example.meetloggerv2.core.export

import android.content.Context
import java.io.OutputStream

class DocumentExportManager @JvmOverloads constructor(
    private val context: Context? = null
) {
    private val strategies = mutableMapOf<String, DocumentExporter>()

    init {
        registerExporter("PDF", PdfDocumentExporter())
        registerExporter("DOCX", DocxDocumentExporter())
        registerExporter("TXT", TxtDocumentExporter())
    }

    fun registerExporter(format: String, exporter: DocumentExporter) {
        strategies[format.uppercase()] = exporter
    }

    fun getExporter(format: String): DocumentExporter? {
        return strategies[format.uppercase()]
    }

    fun export(content: String, format: String, outputStream: OutputStream) {
        val exporter = getExporter(format) ?: throw IllegalArgumentException("Unsupported format: $format")
        exporter.export(content, outputStream)
    }
}
