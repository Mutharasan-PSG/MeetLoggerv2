package com.example.meetloggerv2.ui.details.fragment

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.export.DocumentExportManager
import com.example.meetloggerv2.ui.details.viewmodel.FileDetailsViewModel
import com.example.meetloggerv2.core.session.AuthSession
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileOutputStream
import java.util.*

class FileDetailsFragment : Fragment() {
    private lateinit var responseTextView: TextView
    private lateinit var bottomContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var languageSwitchButton: LinearLayout
    private lateinit var editText: EditText
    private lateinit var updateButton: Button
    private lateinit var cancelButton: Button
    private lateinit var editLayout: View
    private lateinit var exportLayout: View
    private lateinit var shareLayout: View
    
    private val viewModel: FileDetailsViewModel by viewModels()
    private val authSession = AuthSession()
    private lateinit var exportManager: DocumentExportManager
    
    private var fileName: String? = null
    private var isEditing = false
    private var isTranslating = false
    private var isContentTranslated = false
    private var selectedLanguageCode = "en"
    private var originalLanguageCode: String? = null
    private var pendingExportFormat: String? = null

    private val languages = listOf(
        "English" to "en", "French" to "fr", "German" to "de", "Afrikaans" to "af", "Arabic" to "ar",
        "Belarusian" to "be", "Bulgarian" to "bg", "Bengali" to "bn", "Catalan" to "ca", "Czech" to "cs",
        "Welsh" to "cy", "Danish" to "da", "Greek" to "el", "Spanish" to "es", "Esperanto" to "eo",
        "Estonian" to "et", "Persian" to "fa", "Finnish" to "fi", "Irish" to "ga", "Galician" to "gl",
        "Gujarati" to "gu", "Hebrew" to "he", "Hindi" to "hi", "Croatian" to "hr", "Haitian" to "ht",
        "Hungarian" to "hu", "Indonesian" to "id", "Icelandic" to "is", "Italian" to "it", "Japanese" to "ja",
        "Georgian" to "ka", "Kannada" to "kn", "Korean" to "ko", "Lithuanian" to "lt", "Latvian" to "lv",
        "Macedonian" to "mk", "Marathi" to "mr", "Malay" to "ms", "Maltese" to "mt", "Dutch" to "nl",
        "Norwegian" to "no", "Polish" to "pl", "Portuguese" to "pt", "Romanian" to "ro", "Russian" to "ru",
        "Slovak" to "sk", "Slovenian" to "sl", "Albanian" to "sq", "Swedish" to "sv", "Swahili" to "sw",
        "Tamil" to "ta", "Telugu" to "te", "Thai" to "th", "Tagalog" to "tl", "Turkish" to "tr",
        "Ukrainian" to "uk", "Urdu" to "ur", "Vietnamese" to "vi", "Chinese" to "zh"
    ).sortedBy { it.first }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); fileName = arguments?.getString("fileName") }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_file_details, container, false)
        exportManager = DocumentExportManager(requireContext())
        initializeViews(view)
        setupObservers()
        fetchFileDetails()
        return view
    }

    private fun initializeViews(view: View) {
        responseTextView = view.findViewById(R.id.responseTextView); bottomContainer = view.findViewById(R.id.bottomContainer)
        scrollView = view.findViewById(R.id.scrollView); progressOverlay = view.findViewById(R.id.progressOverlay)
        languageSwitchButton = view.findViewById(R.id.languageSwitchButton)
        languageSwitchButton.setOnClickListener { showLanguageDialog() }
        editLayout = view.findViewById(R.id.editlayout)
        exportLayout = view.findViewById(R.id.exportlayout)
        shareLayout = view.findViewById(R.id.sharelayout)
        
        editLayout.setOnClickListener { switchToEditMode() }
        exportLayout.setOnClickListener { showExportDialog() }
        shareLayout.setOnClickListener { showShareDialog() }
        
        updateButton = Button(context).apply { 
            text = getString(R.string.dialog_update)
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.BLUE))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        cancelButton = Button(context).apply { 
            text = getString(R.string.dialog_cancel)
            visibility = View.GONE
            setBackgroundColor(ContextCompat.getColor(context, R.color.BLUE))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        bottomContainer.addView(updateButton); bottomContainer.addView(cancelButton)
        updateButton.setOnClickListener { checkAndSaveEditedContent() }
        cancelButton.setOnClickListener { switchToViewMode() }
        setupBackPressHandler()
        setupScrollListener()
    }

    private fun setupObservers() {
        viewModel.fileDetails.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                responseTextView.text = (data["Response"] as? String)?.replace("*", "")?.trim()
                originalLanguageCode = data["OriginalLanguage"] as? String ?: "en"
                selectedLanguageCode = originalLanguageCode ?: "en"
            }
        }
        viewModel.translatedText.observe(viewLifecycleOwner) { result -> responseTextView.text = result; isContentTranslated = true; isTranslating = false }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            progressOverlay.visibility = if (state is FileDetailsViewModel.DetailsUiState.Loading) View.VISIBLE else View.GONE
            if (state is FileDetailsViewModel.DetailsUiState.NewFileCreated) openNewFileDetailsFragment(state.fileName)
        }
    }

    private fun fetchFileDetails() { authSession.currentUserId()?.let { uid -> fileName?.let { viewModel.fetchDetails(uid, it) } } }

    private fun switchToEditMode() { 
        isEditing = true
        editText = EditText(context).apply { 
            setText(responseTextView.text)
            layoutParams = responseTextView.layoutParams
            setTextColor(ContextCompat.getColor(context, R.color.onSurfaceColor))
        }
        val p = responseTextView.parent as ViewGroup
        val i = p.indexOfChild(responseTextView)
        p.removeView(responseTextView)
        p.addView(editText, i)
        
        updateButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        editLayout.visibility = View.GONE
        exportLayout.visibility = View.GONE
        shareLayout.visibility = View.GONE
    }

    private fun switchToViewMode() { 
        isEditing = false
        val p = editText.parent as ViewGroup
        val i = p.indexOfChild(editText)
        p.removeView(editText)
        p.addView(responseTextView, i)
        
        updateButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
        editLayout.visibility = View.VISIBLE
        exportLayout.visibility = View.VISIBLE
        shareLayout.visibility = View.VISIBLE
    }

    private fun checkAndSaveEditedContent() { if (!isContentTranslated) saveEditedContent() else showSaveOptionsDialog() }
    private fun saveEditedContent() { authSession.currentUserId()?.let { uid -> fileName?.let { viewModel.updateContent(uid, it, editText.text.toString(), selectedLanguageCode) } }; switchToViewMode() }
    private fun saveAsNewCopy() { authSession.currentUserId()?.let { uid -> fileName?.let { val name = "${it.substringBeforeLast(".")} (Copy).mp3"; viewModel.saveAsNewCopy(uid, name, viewModel.fileDetails.value?.toMutableMap()?.apply { put("fileName", name); put("Response", editText.text.toString()) } ?: mapOf()) } }; switchToViewMode() }

    private fun showSaveOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        
        dialogView.findViewById<Button>(R.id.overwrite_button).setOnClickListener {
            dialog.dismiss()
            saveEditedContent()
        }
        
        dialogView.findViewById<Button>(R.id.new_copy_button).setOnClickListener {
            dialog.dismiss()
            saveAsNewCopy()
        }
        
        dialogView.findViewById<ImageView>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showLanguageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_switch, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        
        val spinner = dialogView.findViewById<Spinner>(R.id.languageSpinner)
        val changeBtn = dialogView.findViewById<Button>(R.id.changeLanguageButton)
        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelLanguageButton)
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        
        val currentIdx = languages.indexOfFirst { it.second == selectedLanguageCode }
        if (currentIdx != -1) spinner.setSelection(currentIdx)
        
        changeBtn.setOnClickListener {
            selectedLanguageCode = languages[spinner.selectedItemPosition].second
            val content = viewModel.fileDetails.value?.get("Response") as? String ?: return@setOnClickListener
            isTranslating = true
            viewModel.translateContent(content, originalLanguageCode ?: "en", selectedLanguageCode)
            dialog.dismiss()
        }
        
        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            performExport("PDF")
        }
        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            performExport("DOCX")
        }
        dialog.show()
    }

    private fun performExport(format: String) {
        pendingExportFormat = format
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { 
            type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val ext = if (format == "PDF") "pdf" else "docx"
            val cleanName = fileName?.substringBeforeLast(".") ?: "export"
            putExtra(Intent.EXTRA_TITLE, "$cleanName.$ext") 
        }
        startActivityForResult(intent, 100)
    }

    private fun showShareDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            performShare("PDF")
        }
        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            performShare("DOCX")
        }
        dialog.show()
    }

    private fun performShare(format: String) {
        val cleanName = fileName?.substringBeforeLast(".") ?: "share"
        val ext = if (format == "PDF") "pdf" else "docx"
        val temp = File(requireContext().cacheDir, "$cleanName.$ext")
        val os = FileOutputStream(temp)
        val content = responseTextView.text.toString()
        if (format == "PDF") exportManager.exportToPdf(content, os) else exportManager.exportToDocx(content, os)
        os.close()
        startActivity(Intent.createChooser(exportManager.getShareIntent(temp, format), "Share"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK) data?.data?.let { uri -> 
            requireContext().contentResolver.openOutputStream(uri)?.use { 
                if (pendingExportFormat == "PDF") exportManager.exportToPdf(responseTextView.text.toString(), it) 
                else exportManager.exportToDocx(responseTextView.text.toString(), it) 
            }
        }
    }

    private fun openNewFileDetailsFragment(name: String) { parentFragmentManager.beginTransaction().replace(R.id.fragment_container, FileDetailsFragment().apply { arguments = Bundle().apply { putString("fileName", name) } }).addToBackStack(null).commit() }
    private fun setupBackPressHandler() { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { if (isEditing) switchToViewMode() else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() } } }) }
    private fun setupScrollListener() { scrollView.setOnScrollChangeListener { _, _, y, _, oldY -> bottomContainer.visibility = if (y > oldY) View.GONE else View.VISIBLE } }
}
