package com.example.meetloggerv2

import android.content.Intent
import android.content.res.ColorStateList
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class FileDetailsFragment : Fragment() {

    private lateinit var responseTextView: TextView
    private lateinit var bottomContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var languageSwitchButton: LinearLayout
    private var fileName: String? = null
    private var isBottomContainerVisible = true
    private var isEditing = false
    private var isTranslating = false // New flag for translation state
    private lateinit var editText: EditText
    private lateinit var editButton: View
    private lateinit var exportButton: View
    private lateinit var shareButton: View
    private lateinit var updateButton: Button
    private lateinit var cancelButton: Button
    private var selectedLanguageCode = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString("fileName")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_file_details, container, false)

        responseTextView = view.findViewById(R.id.responseTextView)
        bottomContainer = view.findViewById(R.id.bottomContainer)
        scrollView = view.findViewById(R.id.scrollView)
        progressOverlay = view.findViewById(R.id.progressOverlay)
        languageSwitchButton = view.findViewById(R.id.languageSwitchButton)

        val progressBar = view.findViewById<ProgressBar>(R.id.translationProgressBar)
        progressBar.indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black))

        editButton = view.findViewById(R.id.editlayout)
        exportButton = view.findViewById(R.id.exportlayout)
        shareButton = view.findViewById(R.id.sharelayout)

        updateButton = Button(context).apply {
            text = "Update"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
        }
        cancelButton = Button(context).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
        }

        bottomContainer.addView(updateButton)
        bottomContainer.addView(cancelButton)

        val slideUp = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
        bottomContainer.startAnimation(slideUp)

        setupButtonAnimation(editButton)
        setupButtonAnimation(exportButton)
        setupButtonAnimation(shareButton)
        setupEditControls()
        setupLanguageSwitch()
        setupBackPressHandler() // New: Handle back press restriction

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
                    val cleanedText = responseText.replace("*", "").trim()
                    responseTextView.text = cleanedText
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
            if (isTranslating) {
                Toast.makeText(requireContext(), "Translation in progress, please wait", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val scaleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.scale_bounce)
            button.startAnimation(scaleAnim)

            when (button.id) {
                R.id.editlayout -> switchToEditMode()
                R.id.exportlayout -> showExportDialog()
                R.id.sharelayout -> showShareDialog()
            }
        }
    }

    private fun setupEditControls() {
        updateButton.setOnClickListener {
            saveEditedContent()
        }

        cancelButton.setOnClickListener {
            switchToViewMode()
        }
    }

    private fun switchToEditMode() {
        if (isEditing) return

        isEditing = true

        editText = EditText(context).apply {
            setText(responseTextView.text)
            layoutParams = responseTextView.layoutParams
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            textSize = 16f
        }

        val parent = responseTextView.parent as ViewGroup
        val index = parent.indexOfChild(responseTextView)
        parent.removeView(responseTextView)
        parent.addView(editText, index)

        editButton.visibility = View.GONE
        exportButton.visibility = View.GONE
        shareButton.visibility = View.GONE
        updateButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
    }

    private fun switchToViewMode() {
        if (!isEditing) return

        isEditing = false

        val parent = editText.parent as ViewGroup
        val index = parent.indexOfChild(editText)
        parent.removeView(editText)
        parent.addView(responseTextView, index)

        editButton.visibility = View.VISIBLE
        exportButton.visibility = View.VISIBLE
        shareButton.visibility = View.VISIBLE
        updateButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
    }

    private fun saveEditedContent() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (fileName == null) return

        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName!!)

        val updatedContent = editText.text.toString()

        fileRef.update("Response", updatedContent)
            .addOnSuccessListener {
                responseTextView.text = updatedContent
                switchToViewMode()
                Toast.makeText(requireContext(), "Content updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to update content", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupLanguageSwitch() {
        languageSwitchButton.setOnClickListener {
            if (isTranslating) {
                Toast.makeText(requireContext(), "Translation in progress, please wait", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showLanguageDialog()
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTranslating) {
                    Toast.makeText(requireContext(), "Translation in progress, please wait", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showLanguageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_switch, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val languageSpinner = dialogView.findViewById<Spinner>(R.id.languageSpinner)
        val changeLanguageButton = dialogView.findViewById<Button>(R.id.changeLanguageButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelLanguageButton)

        val languages = listOf(
            "English" to "en",
            "Spanish" to "es",
            "French" to "fr",
            "German" to "de",
            "Afrikaans" to "af",
            "Arabic" to "ar",
            "Belarusian" to "be",
            "Bulgarian" to "bg",
            "Bengali" to "bn",
            "Catalan" to "ca",
            "Czech" to "cs",
            "Welsh" to "cy",
            "Danish" to "da",
            "Greek" to "el",
            "Esperanto" to "eo",
            "Estonian" to "et",
            "Persian" to "fa",
            "Finnish" to "fi",
            "Irish" to "ga",
            "Galician" to "gl",
            "Gujarati" to "gu",
            "Hebrew" to "he",
            "Hindi" to "hi",
            "Croatian" to "hr",
            "Haitian" to "ht",
            "Hungarian" to "hu",
            "Indonesian" to "id",
            "Icelandic" to "is",
            "Italian" to "it",
            "Japanese" to "ja",
            "Georgian" to "ka",
            "Kannada" to "kn",
            "Korean" to "ko",
            "Lithuanian" to "lt",
            "Latvian" to "lv",
            "Macedonian" to "mk",
            "Marathi" to "mr",
            "Malay" to "ms",
            "Maltese" to "mt",
            "Dutch" to "nl",
            "Norwegian" to "no",
            "Polish" to "pl",
            "Portuguese" to "pt",
            "Romanian" to "ro",
            "Russian" to "ru",
            "Slovak" to "sk",
            "Slovenian" to "sl",
            "Albanian" to "sq",
            "Swedish" to "sv",
            "Swahili" to "sw",
            "Tamil" to "ta",
            "Telugu" to "te",
            "Thai" to "th",
            "Tagalog" to "tl",
            "Turkish" to "tr",
            "Ukrainian" to "uk",
            "Urdu" to "ur",
            "Vietnamese" to "vi",
            "Chinese" to "zh"
        )

        val languageNames = languages.map { it.first }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languageNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.adapter = adapter

        val defaultPosition = languages.indexOfFirst { it.second == "en" }
        languageSpinner.setSelection(defaultPosition)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLanguageCode = languages[position].second
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedLanguageCode = "en"
            }
        }

        changeLanguageButton.setOnClickListener {
            dialog.dismiss()
            isTranslating = true
            progressOverlay.visibility = View.VISIBLE
            translateContent(selectedLanguageCode) {
                progressOverlay.visibility = View.GONE
                isTranslating = false
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun translateContent(targetLanguageCode: String, onComplete: () -> Unit) {
        if (targetLanguageCode == "en") {
            fetchFileDetails()
            onComplete()
            return
        }

        val originalContent = responseTextView.text.toString()
        if (originalContent.isEmpty()) {
            Toast.makeText(requireContext(), "No content to translate", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage("en")
            .setTargetLanguage(targetLanguageCode)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                if (!isAdded) {
                    onComplete()
                    return@addOnSuccessListener
                }
                val segments = splitContentIntoSegments(originalContent)
                val translatedSegments = mutableListOf<String>()

                val translationTasks = segments.map { segment ->
                    if (segment.isTranslatable) {
                        translator.translate(segment.text)
                            .continueWith { task -> Pair(segment, task.result) }
                    } else {
                        com.google.android.gms.tasks.Tasks.forResult(Pair(segment, segment.text))
                    }
                }

                com.google.android.gms.tasks.Tasks.whenAllSuccess<Pair<ContentSegment, String>>(translationTasks)
                    .addOnSuccessListener { results ->
                        if (!isAdded) {
                            onComplete()
                            return@addOnSuccessListener
                        }
                        val translatedContent = results.joinToString("") { pair ->
                            if (pair.first.isTranslatable) pair.second + pair.first.suffix
                            else pair.second
                        }
                        responseTextView.text = translatedContent
                        Toast.makeText(requireContext(), "Translated successfully", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                    .addOnFailureListener { exception ->
                        if (!isAdded) {
                            onComplete()
                            return@addOnFailureListener
                        }
                        Toast.makeText(requireContext(), "Translation failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
            }
            .addOnFailureListener { exception ->
                if (!isAdded) {
                    onComplete()
                    return@addOnFailureListener
                }
                Toast.makeText(requireContext(), "Model download failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                onComplete()
            }
    }

    // Data class to hold segment information (unchanged)
    private data class ContentSegment(
        val text: String,
        val isTranslatable: Boolean,
        val suffix: String
    )

    private fun splitContentIntoSegments(content: String): List<ContentSegment> {
        val segments = mutableListOf<ContentSegment>()
        val paragraphs = content.split("\n\n")

        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                segments.add(ContentSegment("", false, "\n\n"))
                continue
            }

            val lines = paragraph.split("\n").filter { it.isNotBlank() }
            var currentText = StringBuilder()
            var isFirstSpeakerInParagraph = true

            for (line in lines) {
                val speakerPattern = Regex("^Speaker [A-Z]+:")
                val matchResult = speakerPattern.find(line)

                if (matchResult != null) {
                    if (currentText.isNotEmpty()) {
                        segments.add(ContentSegment(currentText.toString().trim(), true, "\n\n"))
                        currentText = StringBuilder()
                    }

                    val speakerLabel = matchResult.value
                    segments.add(ContentSegment(speakerLabel, false, " "))

                    val textToTranslate = line.substringAfter(speakerLabel).trim()
                    if (textToTranslate.isNotEmpty()) {
                        currentText.append(textToTranslate).append("\n")
                    }
                } else {
                    currentText.append(line.trim()).append("\n")
                }
            }

            if (currentText.isNotEmpty()) {
                segments.add(ContentSegment(currentText.toString().trim(), true, "\n\n"))
            }
        }

        if (segments.isNotEmpty() && segments.last().suffix == "\n\n") {
            segments[segments.lastIndex] = segments.last().copy(suffix = "")
        }

        return segments
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportContent("PDF")
        }

        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
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

        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportAndShareContent("PDF")
        }

        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
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
            val cleanFileName = fileName?.substringBeforeLast(".")
            val tempFile = File(requireContext().cacheDir, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")

            val outputStream = FileOutputStream(tempFile)
            when (format) {
                "PDF" -> saveAsPdf(content, outputStream)
                "DOCX" -> saveAsDocx(content, outputStream)
            }
            outputStream.close()

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
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val cleanFileName = fileName?.substringBeforeLast(".")
            intent.putExtra(Intent.EXTRA_TITLE, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

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

        if (content.startsWith("TRANSCRIPTION OF AUDIO")) {
            canvas.drawText("TRANSCRIPTION OF AUDIO", marginLeft, currentY, boldPaint)
            currentY += lineHeight * 1.5f
        }

        val lines = content.split("\n")

        for (line in lines) {
            if (line.matches(Regex("^Speaker [A-Z]+:.*"))) {
                val parts = line.split(":", limit = 2)
                val speaker = parts[0].trim()
                var text = parts.getOrNull(1)?.trim() ?: ""

                text = formatSummarization(text)

                canvas.drawText(speaker, marginLeft, currentY, boldPaint)
                currentY += lineHeight

                val words = text.split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        if (wordBuffer.size > 7 && currentY < pageHeight - marginBottom) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                        wordBuffer.clear()
                    }

                    wordBuffer.add(word)

                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                if (wordBuffer.isNotEmpty()) {
                    if (wordBuffer.size > 7) {
                        drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                    } else {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    }
                    currentY += lineHeight
                }
            } else {
                var lineText = line.trim()

                lineText = formatSummarization(lineText)

                val words = lineText.split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        if (wordBuffer.size > 7) {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                        wordBuffer.clear()
                    }

                    wordBuffer.add(word)

                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

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

    private fun formatSummarization(text: String): String {
        var formattedText = text
        formattedText = formattedText.replace(Regex("\\*\\*(.*?)\\*\\*")) { match ->
            "**${match.groupValues[1]}**"
        }
        formattedText = formattedText.replace(Regex("(?<=\\w)\\*(?=\\W)")) { "" }
        formattedText = formattedText.replace(Regex("(?<=\\W)\\*(?=\\w)")) { "" }
        formattedText = formattedText.replace(Regex("\\*(.*?)\\*")) { match ->
            match.groupValues[1]
        }
        return formattedText
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
            para.alignment = ParagraphAlignment.BOTH

            val speakerPattern = Regex("^(Speaker \\w+:)")
            val matchResult = speakerPattern.find(line)

            if (matchResult != null) {
                para.spacingBefore = 200

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
                isBottomContainerVisible = false
                bottomContainer.startAnimation(
                    AnimationUtils.loadAnimation(
                        requireContext(),
                        R.anim.slide_down
                    )
                )
                bottomContainer.visibility = View.GONE
            } else if (scrollY < oldScrollY && !isBottomContainerVisible) {
                isBottomContainerVisible = true
                bottomContainer.visibility = View.VISIBLE
                bottomContainer.startAnimation(
                    AnimationUtils.loadAnimation(
                        requireContext(),
                        R.anim.slide_up
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isTranslating) {
            Toast.makeText(requireContext(), "Previous translation interrupted", Toast.LENGTH_SHORT).show()
            progressOverlay.visibility = View.GONE
            isTranslating = false
        }
    }
}