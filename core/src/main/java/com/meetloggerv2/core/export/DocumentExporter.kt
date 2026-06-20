package com.meetloggerv2.core.export

import java.io.OutputStream

interface DocumentExporter {
    fun export(content: String, outputStream: OutputStream)
    val mimeType: String
    val fileExtension: String
}
