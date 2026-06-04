package com.example.meetloggerv2.ui.details.fragment

import android.os.Handler
import android.os.Looper
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
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.export.DocumentExportManager
import com.example.meetloggerv2.core.util.ShareHelper
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
    private lateinit var progressText: TextView
    private lateinit var languageSwitchButton: LinearLayout
    private lateinit var editText: EditText
    private lateinit var updateButton: View
    private lateinit var cancelButton: View
    private lateinit var editLayout: View
    private lateinit var exportLayout: View
    private lateinit var shareLayout: View
    
    private val progressTimeoutHandler = Handler(Looper.getMainLooper())
    private val progressTimeoutRunnable = Runnable {
        if (isAdded && progressOverlay.visibility == View.VISIBLE) {
            Toast.makeText(context, R.string.msg_please_wait, Toast.LENGTH_SHORT).show()
        }
    }
    
    private var isBottomContainerVisible = true
    private val showBottomBarRunnable = Runnable {
        if (isAdded && !isBottomContainerVisible) {
            isBottomContainerVisible = true
            bottomContainer.visibility = View.VISIBLE
            bottomContainer.animate().cancel()
            bottomContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
        }
    }
    
    private val viewModel: FileDetailsViewModel by viewModels()
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
        progressText = view.findViewById(R.id.progressText)
        languageSwitchButton = view.findViewById(R.id.languageSwitchButton)
        languageSwitchButton.setOnClickListener { showLanguageDialog() }
        editLayout = view.findViewById(R.id.editlayout)
        exportLayout = view.findViewById(R.id.exportlayout)
        shareLayout = view.findViewById(R.id.sharelayout)
        
        editLayout.setOnClickListener { switchToEditMode() }
        exportLayout.setOnClickListener { showExportDialog() }
        shareLayout.setOnClickListener { showShareDialog() }
        
        updateButton = view.findViewById(R.id.updatelayout)
        cancelButton = view.findViewById(R.id.cancellayout)
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
            val isLoading = state is FileDetailsViewModel.DetailsUiState.Loading
            progressOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                progressText.text = (state as FileDetailsViewModel.DetailsUiState.Loading).message
                progressTimeoutHandler.removeCallbacks(progressTimeoutRunnable)
                progressTimeoutHandler.postDelayed(progressTimeoutRunnable, 7000)
            } else {
                progressTimeoutHandler.removeCallbacks(progressTimeoutRunnable)
            }
            if (state is FileDetailsViewModel.DetailsUiState.NewFileCreated) openNewFileDetailsFragment(state.fileName)
        }
    }

    private fun fetchFileDetails() { fileName?.let { viewModel.fetchDetails(it) } }

    private fun switchToEditMode() { 
        isEditing = true
        if (!isBottomContainerVisible) {
            progressTimeoutHandler.removeCallbacks(showBottomBarRunnable)
            showBottomBarRunnable.run()
        }
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
        if (!isBottomContainerVisible) {
            progressTimeoutHandler.removeCallbacks(showBottomBarRunnable)
            showBottomBarRunnable.run()
        }
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
    private fun saveEditedContent() { fileName?.let { viewModel.updateContent(it, editText.text.toString(), selectedLanguageCode) }; switchToViewMode() }
    private fun saveAsNewCopy() { fileName?.let { val name = "${it.substringBeforeLast(".")} (Copy).mp3"; viewModel.saveAsNewCopy(name, viewModel.fileDetails.value?.toMutableMap()?.apply { put("fileName", name); put("Response", editText.text.toString()) } ?: mapOf()) }; switchToViewMode() }

    private fun showSaveOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)
        
        dialogView.findViewById<Button>(R.id.overwrite_button).setOnClickListener {
            dialog.dismiss()
            val warningView = layoutInflater.inflate(R.layout.dialog_overwrite_warning, null)
            val warningDialog = MaterialAlertDialogBuilder(requireContext()).setView(warningView).create()
            warningDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            warningDialog.window?.setDimAmount(0.8f)
            
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
            
            warningView.findViewById<ImageView>(R.id.cancel_button).setOnClickListener {
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
        }
        
        dialog.show()
    }

    private fun showLanguageDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_switch, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)
        
        val spinner = dialogView.findViewById<Spinner>(R.id.languageSpinner)
        val changeBtn = dialogView.findViewById<Button>(R.id.changeLanguageButton)
        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelLanguageButton)
        
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_selected_item, languages.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
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
        com.example.meetloggerv2.core.util.UIUtils.showFormatSelectionDialog(requireContext()) { format ->
            performExport(format)
        }
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
        com.example.meetloggerv2.core.util.UIUtils.showFormatSelectionDialog(requireContext()) { format ->
            performShare(format)
        }
    }

    private fun performShare(format: String) {
        val cleanName = fileName?.substringBeforeLast(".") ?: "share"
        val exporter = exportManager.getExporter(format) ?: return
        val ext = exporter.fileExtension.removePrefix(".")
        val temp = File(requireContext().cacheDir, "$cleanName.$ext")
        val os = FileOutputStream(temp)
        val content = responseTextView.text.toString()
        exportManager.export(content, format, os)
        os.close()
        startActivity(Intent.createChooser(ShareHelper.getShareIntent(requireContext(), temp, exporter.mimeType), "Share"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK) data?.data?.let { uri -> 
            requireContext().contentResolver.openOutputStream(uri)?.use { 
                pendingExportFormat?.let { format ->
                    exportManager.export(responseTextView.text.toString(), format, it)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressTimeoutHandler.removeCallbacks(progressTimeoutRunnable)
        progressTimeoutHandler.removeCallbacks(showBottomBarRunnable)
    }

    private fun openNewFileDetailsFragment(name: String) { parentFragmentManager.beginTransaction().replace(R.id.fragment_container, FileDetailsFragment().apply { arguments = Bundle().apply { putString("fileName", name) } }).addToBackStack(null).commit() }
    private fun setupBackPressHandler() { requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { if (isEditing) switchToViewMode() else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() } } }) }
    private fun setupScrollListener() {
        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            progressTimeoutHandler.removeCallbacks(showBottomBarRunnable)
            if (isBottomContainerVisible) {
                isBottomContainerVisible = false
                val hideTranslation = if (bottomContainer.height > 0) bottomContainer.height.toFloat() + 100f else 250f
                bottomContainer.animate().cancel()
                bottomContainer.animate()
                    .alpha(0f)
                    .translationY(hideTranslation)
                    .setDuration(200)
                    .withEndAction { bottomContainer.visibility = View.GONE }
                    .start()
            }
            progressTimeoutHandler.postDelayed(showBottomBarRunnable, 300)
        }
    }
}
