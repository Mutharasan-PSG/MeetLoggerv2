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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
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
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.Timestamp

class FileDetailsFragment : Fragment() {

    private lateinit var responseTextView: TextView
    private lateinit var bottomContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var languageSwitchButton: LinearLayout
    private var fileName: String? = null
    private var isBottomContainerVisible = true
    private var isEditing = false
    private var isTranslating = false
    private var isContentTranslated = false
    private lateinit var editText: EditText
    private lateinit var editButton: View
    private lateinit var exportButton: View
    private lateinit var shareButton: View
    private lateinit var updateButton: Button
    private lateinit var cancelButton: Button
    private var selectedLanguageCode = "en"
    private var originalLanguageCode: String? = null

    // Define language list as a class-level constant
    private val languages = listOf(
        "English" to "en",
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
        "Spanish" to "es",
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
    ).sortedBy { it.first }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString("fileName")
        Log.d("FileDetailsFragment", "onCreate: fileName = $fileName")
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
            text = "UPDATE"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.BLUE))
            setTextColor(Color.WHITE)
            typeface = ResourcesCompat.getFont(context, R.font.poppins_bold)
        }

        cancelButton = Button(context).apply {
            text = "CANCEL"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.BLUE))
            setTextColor(Color.WHITE)
            typeface = ResourcesCompat.getFont(context, R.font.poppins_bold)
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
        setupBackPressHandler()
        setupScrollListener()
        fetchFileDetails()

        return view
    }

    private fun fetchFileDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (fileName == null) {
            Log.e("FileDetailsFragment", "fetchFileDetails: fileName is null")
            Toast.makeText(requireContext(), "No file specified", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("FileDetailsFragment", "fetchFileDetails: Fetching for fileName = $fileName")
        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName!!)

        fileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val responseText = document.getString("Response") ?: "No response available"
                    val cleanedText = responseText.replace("*", "").replace("#", "").trim()
                    responseTextView.text = cleanedText
                    originalLanguageCode = document.getString("OriginalLanguage") ?: "en"
                    selectedLanguageCode = originalLanguageCode ?: "en" // Reset to original
                    isContentTranslated = false // Reset translation state
                    Log.d("FileDetailsFragment", "fetchFileDetails: Loaded Response = $cleanedText, OriginalLanguage = $originalLanguageCode")
                } else {
                    Log.w("FileDetailsFragment", "fetchFileDetails: Document does not exist for $fileName")
                    Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e("FileDetailsFragment", "fetchFileDetails: Failed for $fileName, error = ${exception.message}")
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
            checkAndSaveEditedContent()
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
        languageSwitchButton.isEnabled = false
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
        languageSwitchButton.isEnabled = true
    }

    private fun checkAndSaveEditedContent() {
        if (originalLanguageCode == null) {
            Toast.makeText(requireContext(), "Original language not found", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedContent = editText.text.toString().trim()
        if (updatedContent.isEmpty()) {
            Toast.makeText(requireContext(), "Content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isContentTranslated) {
            // Editing in original language, update directly
            saveEditedContent()
        } else {
            // Editing translated content, show dialog
            showSaveOptionsDialog()
        }
    }

    private fun showSaveOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.overwrite_button).setOnClickListener {
            dialog.dismiss()
            // Warn about overwriting with translated edits using XML dialog
            val warningView = layoutInflater.inflate(R.layout.dialog_overwrite_warning, null)
            val warningDialog = AlertDialog.Builder(requireContext())
                .setView(warningView)
                .create()

            val originalLangName = languages.find { it.second == originalLanguageCode }?.first ?: "unknown"
            val currentLangName = languages.find { it.second == selectedLanguageCode }?.first ?: "unknown"
            warningView.findViewById<TextView>(R.id.warning_message).text =
                "Overwriting will replace the original content ($originalLangName language) with edits made in $currentLangName."

            warningView.findViewById<Button>(R.id.yes_button).setOnClickListener {
                warningDialog.dismiss()
                saveEditedContent()
            }

            warningView.findViewById<Button>(R.id.no_button).setOnClickListener {
                warningDialog.dismiss()
            }

            warningDialog.show()
        }

        dialogView.findViewById<Button>(R.id.new_copy_button).setOnClickListener {
            dialog.dismiss()
            saveAsNewCopy()
        }

        dialogView.findViewById<ImageView>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
            switchToViewMode()
        }

        dialog.show()
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

        fileRef.update("Response", updatedContent,
            "OriginalLanguage", selectedLanguageCode
        )
            .addOnSuccessListener {
                responseTextView.text = updatedContent
                originalLanguageCode = selectedLanguageCode
                isContentTranslated = false // Reset since we're updating the original
                selectedLanguageCode = originalLanguageCode ?: "en"
                switchToViewMode()
                Toast.makeText(requireContext(), "Content updated successfully", Toast.LENGTH_SHORT).show()
                Log.d("FileDetailsFragment", "saveEditedContent: Updated Response for $fileName")
            }
            .addOnFailureListener {e ->
                Toast.makeText(requireContext(), "Failed to update content", Toast.LENGTH_SHORT).show()
                Log.e("FileDetailsFragment", "saveEditedContent: Failed for $fileName, error = ${e.message}")
            }
    }

    private fun saveAsNewCopy() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e("FileDetailsFragment", "saveAsNewCopy: No user ID found")
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        if (fileName == null) {
            Log.e("FileDetailsFragment", "saveAsNewCopy: fileName is null")
            Toast.makeText(requireContext(), "File name not provided", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val originalFileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName!!)

        val updatedContent = editText.text.toString()
        val cleanFileName = fileName!!.substringBeforeLast(".")
        // Extract the original extension, default to ".mp3" if none (rare case)
        val originalExtension = fileName!!.substringAfterLast(".", "mp3")

        // Map selectedLanguageCode to full language name
        val fullLanguageName = languages.find { it.second == selectedLanguageCode }?.first
            ?: run {
                Log.w("FileDetailsFragment", "Language code $selectedLanguageCode not found, defaulting to code")
                selectedLanguageCode
            }
        val newFileName = "$cleanFileName ($fullLanguageName).$originalExtension"
        val displayFileName = "$cleanFileName ($fullLanguageName)" // Name without extension for Toast

        val newFileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(newFileName)

        val istTimestamp = Timestamp.now()

        originalFileRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                if (document.exists()) {
                    // Copy all fields from the original document
                    val data = document.data?.toMutableMap() ?: mutableMapOf()

                    // Update or set required fields
                    data["fileName"] = newFileName
                    data["Response"] = updatedContent
                    data["OriginalLanguage"] = selectedLanguageCode
                    data["NewFileCreated"] = istTimestamp

                    // Ensure critical fields are present
                    if (!data.containsKey("timestamp_clientUpload")) {
                        data["timestamp_clientUpload"] = istTimestamp
                        Log.d("FileDetailsFragment", "saveAsNewCopy: Added missing timestamp_clientUpload for $newFileName")
                    }
                    if (!data.containsKey("status")) {
                        data["status"] = "processed" // Default to 'processed' for new copies
                        Log.d("FileDetailsFragment", "saveAsNewCopy: Added missing status for $newFileName")
                    }

                    newFileRef.set(data)
                        .addOnSuccessListener {
                            if (!isAdded) return@addOnSuccessListener
                            switchToViewMode()
                            Toast.makeText(requireContext(), "New file created: $displayFileName", Toast.LENGTH_SHORT).show()
                            Log.d("FileDetailsFragment", "saveAsNewCopy: New file created with name = $newFileName")
                            openNewFileDetailsFragment(newFileName)
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener
                            Toast.makeText(requireContext(), "Failed to create new file: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("FileDetailsFragment", "saveAsNewCopy: Failed for $newFileName, error = ${e.message}")
                        }
                } else {
                    Toast.makeText(requireContext(), "Original file not found", Toast.LENGTH_SHORT).show()
                    Log.w("FileDetailsFragment", "saveAsNewCopy: Original file $fileName not found")
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to fetch original file: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FileDetailsFragment", "saveAsNewCopy: Failed to fetch $fileName, error = ${e.message}")
            }
    }

    private fun openNewFileDetailsFragment(newFileName: String) {
        val fileDetailsFragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", newFileName)
            }
        }

        val transaction: FragmentTransaction = parentFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        transaction.replace(R.id.fragment_container, fileDetailsFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun setupLanguageSwitch() {
        languageSwitchButton.setOnClickListener {
            if (isEditing) {
                Toast.makeText(
                    requireContext(),
                    "Cannot change language while editing",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
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

        val languageNames = languages.map { it.first }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            languageNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        languageSpinner.adapter = adapter

        // Set default to original language if available
        val defaultPosition = languages.indexOfFirst { it.second == (originalLanguageCode ?: "en") }
        languageSpinner.setSelection(defaultPosition)

        languageSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLanguageCode = languages[position].second
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedLanguageCode = originalLanguageCode ?: "en"
            }
        }

        changeLanguageButton.setOnClickListener {
            dialog.dismiss()
            if (selectedLanguageCode == originalLanguageCode) {
                Toast.makeText(requireContext(), "Content is already in the selected language", Toast.LENGTH_SHORT).show()
                fetchFileDetails()
            } else {
                isTranslating = true
                progressOverlay.visibility = View.VISIBLE
                translateContent(selectedLanguageCode) {
                    progressOverlay.visibility = View.GONE
                    isTranslating = false
                }
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun translateContent(targetLanguageCode: String, onComplete: () -> Unit) {
        if (originalLanguageCode == null) {
            Toast.makeText(requireContext(), "Original language not found", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }

        if (targetLanguageCode == originalLanguageCode) {
            fetchFileDetails()
            onComplete()
            return
        }

        // Fetch original content from Firestore to ensure translation from source
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            onComplete()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName!!)

        fileRef.get().addOnSuccessListener { document ->
            if (!isAdded) {
                onComplete()
                return@addOnSuccessListener
            }

            if (document.exists()) {
                val originalContent = document.getString("Response") ?: run {
                    Toast.makeText(requireContext(), "No content to translate", Toast.LENGTH_SHORT).show()
                    onComplete()
                    return@addOnSuccessListener
                }
                val cleanedContent = originalContent.replace("*", "").replace("#", "").trim()
                if (cleanedContent.isEmpty()) {
                    Toast.makeText(requireContext(), "No content to translate", Toast.LENGTH_SHORT).show()
                    onComplete()
                    return@addOnSuccessListener
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(originalLanguageCode!!)
                    .setTargetLanguage(targetLanguageCode)
                    .build()

                val translator = Translation.getClient(options)
                val conditions = DownloadConditions.Builder()
                    .build()

                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener {
                        if (!isAdded) {
                            onComplete()
                            return@addOnSuccessListener
                        }
                        val segments = splitContentIntoSegments(cleanedContent)
                        val translationTasks = segments.map { segment ->
                            translator.translate(segment.text)
                                .continueWith { task -> Pair(segment, task.result) }
                        }

                        com.google.android.gms.tasks.Tasks.whenAllSuccess<Pair<ContentSegment, String>>(translationTasks)
                            .addOnSuccessListener { results ->
                                if (!isAdded) {
                                    onComplete()
                                    return@addOnSuccessListener
                                }
                                val translatedContent = results.joinToString("") { pair ->
                                    pair.second + pair.first.suffix
                                }
                                responseTextView.text = translatedContent
                                isContentTranslated = true // Mark content as translated
                                selectedLanguageCode = targetLanguageCode
                                Toast.makeText(requireContext(), "Translated successfully", Toast.LENGTH_SHORT).show()
                                Log.d("FileDetailsFragment", "translateContent: Translated to $targetLanguageCode: $translatedContent")
                                onComplete()
                            }
                            .addOnFailureListener { exception ->
                                if (!isAdded) {
                                    onComplete()
                                    return@addOnFailureListener
                                }
                                Toast.makeText(requireContext(), "Translation failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                Log.e("FileDetailsFragment", "translateContent: Translation failed for $targetLanguageCode, error = ${exception.message}")
                                onComplete()
                            }
                    }
                    .addOnFailureListener { exception ->
                        if (!isAdded) {
                            onComplete()
                            return@addOnFailureListener
                        }
                        Toast.makeText(requireContext(), "Model download failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                        Log.e("FileDetailsFragment", "translateContent: Model download failed for $targetLanguageCode, error = ${exception.message}")
                        onComplete()
                    }
            } else {
                Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                Log.w("FileDetailsFragment", "translateContent: File $fileName not found")
                onComplete()
            }
        }.addOnFailureListener { exception ->
            if (!isAdded) {
                onComplete()
                return@addOnFailureListener
            }
            Toast.makeText(requireContext(), "Failed to fetch original content: ${exception.message}", Toast.LENGTH_SHORT).show()
            Log.e("FileDetailsFragment", "translateContent: Failed to fetch $fileName, error = ${exception.message}")
            onComplete()
        }
    }

    private data class ContentSegment(
        val text: String,
        val suffix: String
    )

    private fun splitContentIntoSegments(content: String): List<ContentSegment> {
        val segments = mutableListOf<ContentSegment>()
        val paragraphs = content.split("\n\n")

        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                segments.add(ContentSegment("", "\n\n"))
                continue
            }

            val lines = paragraph.split("\n").filter { it.isNotBlank() }
            for ((index, line) in lines.withIndex()) {
                val isLastLine = index == lines.size - 1
                // Translate each line, preserving structure with appropriate suffix
                val suffix = if (isLastLine) "\n\n" else "\n"
                segments.add(ContentSegment(line.trim(), suffix))
            }
        }

        // Remove trailing \n\n from the last segment if present
        if (segments.isNotEmpty() && segments.last().suffix == "\n\n") {
            segments[segments.lastIndex] = segments.last().copy(suffix = "")
        }

        return segments
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext())
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
        val dialog = AlertDialog.Builder(requireContext())
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
        val minWordsForJustification = 7

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

        val lines = content.split("\n")
        val maxLineWidth = pageWidth - marginLeft - marginRight

        for (line in lines) {
            if (line.isBlank()) {
                currentY += lineHeight
                continue
            }

            var lineText = line.trim().replace(Regex("\\s+:\\s+"), ": ")
            lineText = formatSummarization(lineText)
            val colonIndex = lineText.indexOf(":")

            if (lineText.startsWith("SUMMARY OF THE CONTENT") ||
                lineText.startsWith("TRANSCRIPTION OF SPEAKERS")) {
                canvas.drawText(lineText, marginLeft, currentY, boldPaint)
                currentY += lineHeight * 1.5f
                continue
            }

            if (colonIndex > -1) {
                val beforeColon = lineText.substring(0, colonIndex).trim()
                val afterColon = lineText.substring(colonIndex + 1).trim()

                canvas.drawText("$beforeColon:", marginLeft, currentY, boldPaint)

                if (afterColon.isNotEmpty()) {
                    currentY += lineHeight
                    val words = afterColon.split(" ")
                    val wordBuffer = mutableListOf<String>()

                    for (word in words) {
                        val testLine = (wordBuffer + word).joinToString(" ")
                        val textWidth = normalPaint.measureText(testLine)

                        if (textWidth > maxLineWidth) {
                            if (wordBuffer.size >= minWordsForJustification) {
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
                        if (wordBuffer.size >= minWordsForJustification) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                    }
                } else {
                    currentY += lineHeight
                }
            } else if (lineText.matches(Regex("^Speaker [A-Z]+:.*"))) {
                val parts = lineText.split(":", limit = 2)
                val speaker = parts[0].trim()
                val text = parts.getOrNull(1)?.trim() ?: ""

                canvas.drawText(speaker, marginLeft, currentY, boldPaint)
                currentY += lineHeight

                if (text.isNotEmpty()) {
                    val words = text.split(" ")
                    val wordBuffer = mutableListOf<String>()

                    for (word in words) {
                        val testLine = (wordBuffer + word).joinToString(" ")
                        val textWidth = normalPaint.measureText(testLine)

                        if (textWidth > maxLineWidth) {
                            if (wordBuffer.size >= minWordsForJustification) {
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
                        if (wordBuffer.size >= minWordsForJustification) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                    }
                }
            } else {
                val words = lineText.split(" ")
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        if (wordBuffer.size >= minWordsForJustification) {
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
                    if (wordBuffer.size >= minWordsForJustification) {
                        drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                    } else {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    }
                    currentY += lineHeight
                }
            }

            if (currentY > pageHeight - marginBottom) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                currentY = marginTop
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
        var isSummarization = true

        for (line in lines) {
            if (line.isBlank()) {
                document.createParagraph()
                continue
            }

            if (line.startsWith("Speaker")) {
                isSummarization = false
            }

            val para = document.createParagraph()
            para.alignment = ParagraphAlignment.BOTH

            if (isSummarization) {
                para.spacingAfter = 200
            } else {
                para.spacingBefore = 200
            }

            val processedLine = line.trim().replace(Regex("\\s+:\\s+"), ": ")
            val colonIndex = processedLine.indexOf(":")

            when {
                processedLine == "SUMMARY OF THE CONTENT" ||
                        processedLine == "TRANSCRIPTION OF SPEAKERS" -> {
                    val boldRun = para.createRun()
                    boldRun.isBold = true
                    boldRun.setText(processedLine)
                    para.spacingAfter = 300
                }
                colonIndex > -1 -> {
                    val beforeColon = processedLine.substring(0, colonIndex).trim()
                    val afterColon = processedLine.substring(colonIndex + 1).trim()

                    val boldRun = para.createRun()
                    boldRun.isBold = true
                    boldRun.setText("$beforeColon:")

                    if (afterColon.isNotEmpty()) {
                        val normalRun = para.createRun()
                        normalRun.setText(" $afterColon")
                    }
                }
                processedLine.matches(Regex("^Speaker [A-Z]+:.*")) -> {
                    val parts = processedLine.split(":", limit = 2)
                    val speakerName = parts[0].trim()
                    val remainingText = parts.getOrNull(1)?.trim() ?: ""

                    val speakerRun = para.createRun()
                    speakerRun.isBold = true
                    speakerRun.setText("$speakerName:")

                    if (remainingText.isNotEmpty()) {
                        val contentRun = para.createRun()
                        contentRun.setText(" $remainingText")
                    }
                }
                else -> {
                    val run = para.createRun()
                    run.setText(processedLine)
                }
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