package com.example.meetloggerv2

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.apache.poi.xwpf.usermodel.XWPFDocument
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import java.io.OutputStream

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
                R.id.sharelayout -> Toast.makeText(requireContext(), "Share Clicked", Toast.LENGTH_SHORT).show()
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
            intent.putExtra(Intent.EXTRA_TITLE, "export.${if (format == "PDF") "pdf" else "docx"}")
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
        // Create a PdfDocument
        val document = PdfDocument()

        // Page dimensions and margins
        val pageWidth = 500
        val pageHeight = 700
        val marginTop = 30f  // Top margin
        val marginBottom = 40f  // Bottom margin
        val marginLeft = 40f  // Left margin
        val marginRight = 40f  // Right margin
        val lineHeight = 20f // Approximate line height for text
        val maxLinesPerPage = ((pageHeight - marginTop - marginBottom) / lineHeight).toInt() // Max lines per page

        var currentY = marginTop // Initial Y position for text drawing
        val paint = Paint()
        paint.color = Color.BLACK
        paint.textSize = 16f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL) // Default font style (Times New Roman or similar)

        val words = content.split(" ") // Split content into words
        var currentLine = StringBuilder()
        var canvas: Canvas
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        canvas = page.canvas

        // Function to handle line breaks, justification, and word wrapping
        fun drawLine(line: String, yPosition: Float) {
            // Draw the line and move to the next line
            canvas.drawText(line, marginLeft, yPosition, paint)
            currentY = yPosition + lineHeight
        }

        // Function to justify text within the given width
        fun justifyText(line: String): String {
            val wordsInLine = line.split(" ")

            // Calculate the total width of the words in the line
            val totalTextWidth = paint.measureText(line)
            val totalSpacesRequired = pageWidth - marginLeft - marginRight - totalTextWidth

            // If the total space required for justification is negative, avoid any adjustment
            if (totalSpacesRequired < 0) {
                return line // No justification, just return the line as it is
            }

            // Space between words to distribute
            val spaceBetweenWords = totalSpacesRequired / (wordsInLine.size - 1)

            val justifiedLine = StringBuilder()
            for (i in 0 until wordsInLine.size) {
                justifiedLine.append(wordsInLine[i])
                if (i < wordsInLine.size - 1) {
                    justifiedLine.append(" ".padEnd(spaceBetweenWords.toInt())) // Add calculated space between words
                }
            }
            return justifiedLine.toString()
        }

        // Word wrapping and breaking logic based on a word count limit
        val wordLimitPerLine = 12 // Maximum words per line (adjust this number as needed)
        var currentLineText = StringBuilder()

        // Iterate over all words
        for (i in words.indices) {
            val word = words[i]

            // Add the word to the current line
            currentLineText.append("$word ")

            // If the number of words in the current line exceeds the limit, break it to the next line
            if (currentLineText.toString().split(" ").size > wordLimitPerLine) {
                // Justify and draw the current line
                drawLine(justifyText(currentLineText.toString().trim()), currentY)

                // Reset the current line and continue with the next word (avoid repeating the last word)
                currentLineText = StringBuilder("$word ") // Start the new line with the word that caused the overflow
            }

            // If the current Y position exceeds the page height, start a new page
            if (currentY > pageHeight - marginBottom) {
                document.finishPage(page)
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create())
                canvas = page.canvas
                currentY = marginTop // Reset Y position for the new page
            }
        }

        // Draw the last line if any content is remaining
        if (currentLineText.isNotEmpty()) {
            drawLine(justifyText(currentLineText.toString().trim()), currentY)
        }

        // Finish the last page
        document.finishPage(page)

        try {
            // Write the document to the output stream
            document.writeTo(outputStream)
            Toast.makeText(requireContext(), "File saved as PDF", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save PDF", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }



    private fun saveAsDocx(content: String, outputStream: OutputStream?) {
        // Use Apache POI to create a DOCX file
        val document = XWPFDocument()
        val paragraphs = content.split("\n") // Split content into paragraphs

        for (paragraphText in paragraphs) {
            val paragraph = document.createParagraph()
            paragraph.createRun().setText(paragraphText)
        }

        document.write(outputStream)
        outputStream?.close()
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
