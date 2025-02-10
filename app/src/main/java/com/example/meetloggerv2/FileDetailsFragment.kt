package com.example.meetloggerv2

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigInteger

class FileDetailsFragment : Fragment() {

    private lateinit var responseTextView: TextView
    private lateinit var bottomContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var fileName: String? = null
    private var isBottomContainerVisible = true  // Track visibility

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString("fileName")  // Get file name from arguments
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_details, container, false)

        responseTextView = view.findViewById(R.id.responseTextView)
        bottomContainer = view.findViewById(R.id.bottomContainer)
        scrollView = view.findViewById(R.id.scrollView)

        // Animate the bottom container initially
        val slideUp = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        bottomContainer.startAnimation(slideUp)

        // Setup button animations
        setupButtonAnimation(view.findViewById(R.id.editlayout))
        setupButtonAnimation(view.findViewById(R.id.exportlayout))
        setupButtonAnimation(view.findViewById(R.id.sharelayout))

        // Handle scroll events to show/hide bottom container
        setupScrollListener()

        fetchFileDetails()

        return view
    }

    private fun fetchFileDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (fileName == null) return

        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName!!)

        fileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val responseText = document.getString("Response") ?: "No response available"
                    responseTextView.text = responseText
                } else {
                    Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load file details", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtonAnimation(button: View) {
        button.setOnClickListener {
            val scaleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.scale_bounce)
            button.startAnimation(scaleAnim)

            when (button.id) {
                R.id.editlayout -> Toast.makeText(requireContext(), "Edit Clicked", Toast.LENGTH_SHORT).show()
                R.id.exportlayout -> {
                    // Show the export dialog
                    showExportDialog()
                }
                R.id.sharelayout -> {
                    showShareDialog()
                }
            }
        }
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btn_export_pdf).setOnClickListener {
            dialog.dismiss()
            exportContent("PDF")
        }

        dialogView.findViewById<Button>(R.id.btn_export_docx).setOnClickListener {
            dialog.dismiss()
            exportContent("DOCX")
        }

        dialog.show()
    }

    private fun showShareDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btn_export_pdf).setOnClickListener {
            dialog.dismiss()
            exportAndShareContent("PDF")
        }

        dialogView.findViewById<Button>(R.id.btn_export_docx).setOnClickListener {
            dialog.dismiss()
            exportAndShareContent("DOCX")
        }

        dialog.show()
    }

    private fun exportAndShareContent(format: String) {
        val content = responseTextView.text.toString()

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create a temporary file
            val tempFile = File(requireContext().cacheDir, "$fileName.${if (format == "PDF") "pdf" else "docx"}")

            val outputStream = FileOutputStream(tempFile)
            when (format) {
                "PDF" -> saveAsPdf(content, outputStream)
                "DOCX" -> saveAsDocx(content, outputStream)
            }
            outputStream.close()

            // Now, share the file using Intent
            val uri = FileProvider.getUriForFile(requireContext(), "com.example.meetloggerv2.fileprovider", tempFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error during file sharing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }



    private fun exportContent(format: String) {
        val content = responseTextView.text.toString()

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create a file chooser intent to let user pick the directory
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            intent.putExtra(Intent.EXTRA_TITLE, "$fileName.${if (format == "PDF") "pdf" else "docx"}")
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    // Handle the result of the file picker
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            val uri: Uri = data.data!!
            saveContentToUri(uri, responseTextView.text.toString())
        }
    }

    private fun saveContentToUri(uri: Uri, content: String) {
        try {
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                when {
                    uri.toString().endsWith("pdf") -> saveAsPdf(content, outputStream)
                    uri.toString().endsWith("docx") -> saveAsDocx(content, outputStream)
                }
                Toast.makeText(requireContext(), "File saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsPdf(content: String, outputStream: OutputStream?) {
        if (outputStream == null) return

        val document = PdfDocument()
        val pageWidth = 500
        val pageHeight = 700
        val marginLeft = 40f
        val marginRight = 40f
        val marginTop = 50f
        val marginBottom = 50f
        val lineHeight = 20f

        val normalPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }

        val boldPaint = Paint(normalPaint).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
        }

        var currentY = marginTop
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        // Add bold "TRANSCRIPTION OF AUDIO" at the start if it exists
        if (content.startsWith("TRANSCRIPTION OF AUDIO")) {
            // Draw "TRANSCRIPTION OF AUDIO" in bold
            canvas.drawText("TRANSCRIPTION OF AUDIO", marginLeft, currentY, boldPaint)
            currentY += lineHeight * 1.5f // Add line space after it
        }

        val lines = content.split("\n") // Split by line to process speakers separately

        for (line in lines) {
            if (line.matches(Regex("^Speaker [A-Z]+:.*"))) {
                // Speaker name detected, add a new line space
              //  if (currentY > marginTop) currentY += lineHeight

                val parts = line.split(":", limit = 2)
                val speaker = parts[0].trim()
                val text = parts.getOrNull(1)?.trim() ?: ""

                // Draw speaker name in bold
                canvas.drawText(speaker, marginLeft, currentY, boldPaint)
                currentY += lineHeight

                val words = text.split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        // Check if the line is the last one or too short to justify
                        if (wordBuffer.size > 7 && currentY < pageHeight - marginBottom) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            // If it's the last line or too short, don't justify, just draw normally
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                        wordBuffer.clear()
                    }

                    wordBuffer.add(word)

                    // Start a new page if needed
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                // Draw remaining words without justification if it's the last line or too short
                if (wordBuffer.isNotEmpty()) {
                    if (wordBuffer.size > 7) {
                        drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                    } else {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    }
                    currentY += lineHeight
                }
            } else {
                // Normal paragraph processing without speaker name
                val words = line.split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        // Check if the line is the last one or too short to justify
                        if (wordBuffer.size > 7) {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                        wordBuffer.clear()
                    }

                    wordBuffer.add(word)

                    // Start a new page if needed
                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                // Draw remaining words without justification if it's the last line or too short
                if (wordBuffer.isNotEmpty()) {
                    canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    currentY += lineHeight
                }
            }
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
        outputStream.close()
    }

    private fun drawJustifiedText(canvas: Canvas, words: List<String>, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (words.isEmpty()) return

        val totalWords = words.size
        val textWithoutSpacing = words.joinToString("") { it }
        val textWidth = paint.measureText(textWithoutSpacing)
        val extraSpacing = (maxWidth - textWidth) / (totalWords - 1).coerceAtLeast(1)

        var currentX = x
        for ((index, word) in words.withIndex()) {
            canvas.drawText(word, currentX, y, paint)
            currentX += paint.measureText(word) + if (index < words.lastIndex) extraSpacing else 0f
        }
    }


    private fun saveAsDocx(content: String, outputStream: OutputStream?) {
        if (outputStream == null) return

        val document = XWPFDocument()
        val lines = content.split("\n")

        for (line in lines) {
            if (line.isBlank()) continue

            val para = document.createParagraph()
            para.alignment = ParagraphAlignment.BOTH  // Justify text

            val speakerPattern = Regex("^(Speaker \\w+:)")
            val matchResult = speakerPattern.find(line)

            if (matchResult != null) {
                para.spacingBefore = 200  // Add space before new speaker

                val speakerName = matchResult.value
                val remainingText = line.removePrefix(speakerName).trim()

                val speakerRun = para.createRun()
                speakerRun.isBold = true
                speakerRun.setText(speakerName)

                val contentRun = para.createRun()
                contentRun.setText(" $remainingText")
            } else {
                val run = para.createRun()
                run.setText(line)
            }
        }

        document.write(outputStream)
        document.close()
    }




    private fun setupScrollListener() {
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY && isBottomContainerVisible) {
                // Scrolling down - hide bottom container
                isBottomContainerVisible = false
                bottomContainer.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_down))
                bottomContainer.visibility = View.GONE
            } else if (scrollY < oldScrollY && !isBottomContainerVisible) {
                // Scrolling up - show bottom container
                isBottomContainerVisible = true
                bottomContainer.visibility = View.VISIBLE
                bottomContainer.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up))
            }
        }
    }
}