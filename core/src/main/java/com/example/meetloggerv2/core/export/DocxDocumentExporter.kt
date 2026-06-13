package com.example.meetloggerv2.core.export

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.OutputStream

class DocxDocumentExporter : DocumentExporter {

    override val mimeType: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    override val fileExtension: String = ".docx"

    override fun export(content: String, outputStream: OutputStream) {
        val document = XWPFDocument()
        content.split("\n").forEach { line ->
            val para = document.createParagraph()
            para.alignment = ParagraphAlignment.BOTH
            para.createRun().setText(line)
        }
        document.write(outputStream)
        document.close()
    }
}
