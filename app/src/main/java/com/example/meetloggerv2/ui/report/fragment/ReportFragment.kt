package com.example.meetloggerv2.ui.report.fragment

import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.audio.fragment.AudioListFragment
import com.example.meetloggerv2.ui.details.fragment.FileDetailsFragment
import com.example.meetloggerv2.core.export.DocumentExportManager
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.util.ShareHelper
import com.example.meetloggerv2.ui.report.viewmodel.ReportViewModel
import java.io.File
import java.io.FileOutputStream
import com.example.meetloggerv2.core.util.UIUtils

class ReportFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var progressOverlay: FrameLayout
    private lateinit var mainContent: FrameLayout
    private lateinit var noInternetContainer: LinearLayout
    private lateinit var touchBlockOverlay: FrameLayout
    private lateinit var audioListIcon: ImageView
    private lateinit var adapter: ArrayAdapter<Triple<String, com.google.firebase.Timestamp, String>>
    
    private lateinit var progressText: TextView
    private val progressTimeoutRunnable = Runnable {
        if (isAdded && progressOverlay.visibility == View.VISIBLE) {
            Toast.makeText(context, R.string.msg_please_wait, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val viewModel: ReportViewModel by viewModels()
    private lateinit var exportManager: DocumentExportManager
    private lateinit var networkMonitor: NetworkMonitor
    
    private var renamingPosition = -1
    private var isDeleteMode = false
    private var isRenaming = false
    private var isProcessing = false
    private val selectedItems = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())

    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val content = pendingContent ?: return@registerForActivityResult
            saveContentToUri(uri, content, pendingFormat ?: "PDF")
        }
    }
    private var pendingContent: String? = null
    private var pendingFormat: String? = null
    private var pendingReportAction: String? = null
    private var pendingReportName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        exportManager = DocumentExportManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        initializeViews(view)
        setupListView()
        setupSearchView()
        setupBackPressHandler()
        setupSelectAllCheckbox()
        setupObservers()
        return view
    }

    private fun initializeViews(view: View) {
        listView = view.findViewById(R.id.listView); searchView = view.findViewById(R.id.searchView)
        UIUtils.setupSearchViewClickToFocus(searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon); selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        progressOverlay = view.findViewById(R.id.progressOverlay); mainContent = view.findViewById(R.id.mainContent)
        progressText = view.findViewById(R.id.progressText)
        noInternetContainer = view.findViewById(R.id.noInternetContainer); touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        audioListIcon = view.findViewById(R.id.audioListIcon)
        audioListIcon.setOnClickListener { openAudioListFragment() }
        view.findViewById<ImageView>(R.id.tickIcon).setOnClickListener { finishRenaming() }
        deleteIcon.setOnClickListener { if (isDeleteMode && selectedItems.isNotEmpty()) showDeleteConfirmationDialog() }
        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.msg_delete_selected_reports)
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                val filesToDelete = selectedItems.mapNotNull { 
                    val shortName = adapter.getItem(it)?.first
                    if (shortName != null) viewModel.getFullFileName(shortName) else null
                }
                viewModel.deleteFiles(filesToDelete)
                toggleDeleteMode(false)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<Triple<String, com.google.firebase.Timestamp, String>>(requireContext(), R.layout.list_item, R.id.textViewFileName, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val item = getItem(position)!!
                val name = item.first
                val tv = view.findViewById<TextView>(R.id.textViewFileName); val et = view.findViewById<EditText>(R.id.editTextFileName)
                val cb = view.findViewById<CheckBox>(R.id.checkbox); val mi = view.findViewById<ImageView>(R.id.menuIcon)
                val itemLayout = view.findViewById<LinearLayout>(R.id.listItemLayout)
                
                cb.visibility = if (isDeleteMode && !isRenaming) View.VISIBLE else View.GONE
                cb.isChecked = selectedItems.contains(position)
                cb.setOnClickListener { if (cb.isChecked) selectedItems.add(position) else selectedItems.remove(position); updateDeleteIconVisibility(); updateSelectAllCheckboxState() }
                
                mi.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE
                mi.setOnClickListener { showOptionsPopup(mi, position) }
                
                if (position == renamingPosition && isRenaming) {
                    tv.visibility = View.GONE
                    et.visibility = View.VISIBLE
                    et.setText(name)
                    itemLayout.isClickable = false
                    itemLayout.isFocusable = false
                } else {
                    tv.visibility = View.VISIBLE
                    et.visibility = View.GONE
                    tv.text = name
                    itemLayout.isClickable = true
                    itemLayout.isFocusable = true
                }
                
                itemLayout.setOnClickListener {
                    if (isDeleteMode) {
                        if (selectedItems.contains(position)) selectedItems.remove(position) else selectedItems.add(position)
                        notifyDataSetChanged()
                        updateDeleteIconVisibility()
                        updateSelectAllCheckboxState()
                    } else if (!isRenaming) {
                        openFileDetailsFragment(name)
                    }
                }
                
                itemLayout.setOnLongClickListener {
                    if (!isDeleteMode && !isRenaming) {
                        toggleDeleteMode(true)
                        selectedItems.add(position)
                        notifyDataSetChanged()
                        updateDeleteIconVisibility()
                        true
                    } else false
                }
                
                return view
            }
        }
        listView.adapter = adapter
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean {
                viewModel.setQuery(q ?: "")
                return true
            }
            override fun onQueryTextChange(q: String?): Boolean {
                viewModel.setQuery(q ?: "")
                return true
            }
        })
    }

    private fun setupObservers() {
        viewModel.filteredFiles.observe(viewLifecycleOwner) { files -> adapter.clear(); adapter.addAll(files); togglePlaceholder(files.isEmpty()) }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ReportViewModel.ReportUiState.Loading -> {
                    progressOverlay.visibility = View.VISIBLE
                    isProcessing = true
                    progressText.text = state.message
                    handler.removeCallbacks(progressTimeoutRunnable)
                    handler.postDelayed(progressTimeoutRunnable, 7000)
                }
                is ReportViewModel.ReportUiState.Idle -> {
                    progressOverlay.visibility = View.GONE
                    isProcessing = false
                    handler.removeCallbacks(progressTimeoutRunnable)
                }
                is ReportViewModel.ReportUiState.Error -> {
                    progressOverlay.visibility = View.GONE
                    isProcessing = false
                    handler.removeCallbacks(progressTimeoutRunnable)
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        viewModel.reportEvent.observe(viewLifecycleOwner) { event ->
            val content = event.getContentIfNotHandled() ?: return@observe
            when (content) {
                is ReportViewModel.ReportEvent.FetchDetailsSuccess -> {
                    val name = pendingReportName ?: ""
                    val format = pendingFormat ?: "PDF"
                    val reportContent = content.content
                    
                    val exporter = exportManager.getExporter(format)
                    if (exporter != null) {
                        if (pendingReportAction == "EXPORT") {
                            pendingReportAction = null
                            pendingReportName = null
                            pendingContent = reportContent
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { 
                                type = exporter.mimeType
                                putExtra(Intent.EXTRA_TITLE, "$name${exporter.fileExtension}") 
                            }
                            exportFileLauncher.launch(intent)
                        } else if (pendingReportAction == "SHARE") {
                            pendingReportAction = null
                            pendingReportName = null
                            val ext = exporter.fileExtension.removePrefix(".")
                            val temp = File(requireContext().cacheDir, "$name.$ext")
                            val os = FileOutputStream(temp)
                            exportManager.export(reportContent, format, os)
                            os.close()
                            startActivity(Intent.createChooser(ShareHelper.getShareIntent(requireContext(), temp, exporter.mimeType), "Share"))
                        }
                    }
                }
                is ReportViewModel.ReportEvent.FetchDetailsError -> {
                    pendingReportAction = null
                    pendingReportName = null
                    Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (isOnline) {
                mainContent.visibility = View.VISIBLE
                noInternetContainer.visibility = View.GONE
                viewModel.fetchFiles()
            } else {
                mainContent.visibility = View.GONE
                noInternetContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun showOptionsPopup(anchor: View, pos: Int) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_menu_layout, null)
        val list = popupView.findViewById<ListView>(R.id.popup_list)
        val opts = listOf("RENAME", "EXPORT", "SHARE", "COPY")
        
        val popupAdapter = object : ArrayAdapter<String>(requireContext(), R.layout.popup_menu_item, opts) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(ContextCompat.getColor(requireContext(), R.color.onSurfaceColor))
                return v
            }
        }
        list.adapter = popupAdapter
        
        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.options_background))
        popup.elevation = 30f
        
        list.setOnItemClickListener { _, _, i, _ ->
            val reportItem = this@ReportFragment.adapter.getItem(pos)
            if (reportItem != null) {
                val name = reportItem.first
                when (opts[i]) {
                    "RENAME" -> startRenaming(pos)
                    "EXPORT" -> showExportDialog(name)
                    "SHARE" -> showShareDialog(name)
                    "COPY" -> showCopyDialog(pos)
                }
            }
            popup.dismiss()
        }
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
        try {
            val container = popupView.rootView
            val wm = requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val p = container.layoutParams as WindowManager.LayoutParams
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            p.dimAmount = 0.4f
            wm.updateViewLayout(container, p)
        } catch (e: Exception) {}
    }

    private fun startRenaming(pos: Int) {
        isRenaming = true
        renamingPosition = pos
        adapter.notifyDataSetChanged()
        view?.findViewById<ImageView>(R.id.tickIcon)?.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        updateNavigationIconVisibility()

        listView.postDelayed({
            val rowView = listView.getChildAt(pos - listView.firstVisiblePosition)
            val edit = rowView?.findViewById<EditText>(R.id.editTextFileName)
            if (edit != null) {
                edit.requestFocus()
                edit.setSelection(edit.text?.length ?: 0)
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }, 150)
    }

    private fun finishRenaming() {
        val view = listView.getChildAt(renamingPosition - listView.firstVisiblePosition)
        val name = view?.findViewById<EditText>(R.id.editTextFileName)?.text.toString().trim()
        val oldFull = viewModel.getFullFileName(adapter.getItem(renamingPosition)!!.first)
        if (name.isNotEmpty() && oldFull != null) {
            val ext = oldFull.substringAfterLast(".")
            viewModel.renameFile(oldFull, "$name.$ext")
        }
        cleanupRenamingMode()
    }

    private fun cleanupRenamingMode() { 
        isRenaming = false
        renamingPosition = -1
        view?.findViewById<ImageView>(R.id.tickIcon)?.visibility = View.GONE
        touchBlockOverlay.visibility = View.GONE
        updateNavigationIconVisibility()
        adapter.notifyDataSetChanged()
        
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(listView.windowToken, 0)
    }

    private fun showExportDialog(name: String) {
        com.example.meetloggerv2.core.util.UIUtils.showFormatSelectionDialog(requireContext()) { format ->
            performExport(name, format)
        }
    }

    private fun performExport(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        pendingReportAction = "EXPORT"
        pendingReportName = name
        pendingFormat = format
        viewModel.fetchFileDetails(full)
    }

    private fun showShareDialog(name: String) {
        com.example.meetloggerv2.core.util.UIUtils.showFormatSelectionDialog(requireContext()) { format ->
            performShare(name, format)
        }
    }

    private fun performShare(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        pendingReportAction = "SHARE"
        pendingReportName = name
        pendingFormat = format
        viewModel.fetchFileDetails(full)
    }

    private fun saveContentToUri(uri: Uri, content: String, format: String) {
        requireContext().contentResolver.openOutputStream(uri)?.use { os ->
            exportManager.export(content, format, os)
        }
    }
    private fun showCopyDialog(pos: Int) {
        val item = adapter.getItem(pos) ?: return
        val name = item.first
        val full = viewModel.getFullFileName(name) ?: return
        
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_copy_file, null)
        val edit = v.findViewById<EditText>(R.id.editTextNewFileName)
        val cancelBtn = v.findViewById<Button>(R.id.buttonCancel)
        val proceedBtn = v.findViewById<Button>(R.id.buttonProceed)
        
        edit.setText("copy of $name")
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)
            
        val onCopyAction = {
            val newName = edit.text.toString().trim()
            if (newName.isNotEmpty()) {
                val ext = full.substringAfterLast(".", "mp3")
                viewModel.copyFile(full, "$newName.$ext")
                dialog.dismiss()
            } else {
                Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
            }
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        proceedBtn.setOnClickListener { onCopyAction() }

        dialog.show()
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (isProcessing) return else if (isRenaming) cleanupRenamingMode() else if (isDeleteMode) toggleDeleteMode(false) else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() } }
        })
    }

    private fun toggleDeleteMode(en: Boolean) { 
        isDeleteMode = en; if (!en) selectedItems.clear()
        adapter.notifyDataSetChanged(); updateDeleteIconVisibility()
        updateSelectAllCheckboxVisibility()
        updateNavigationIconVisibility()
    }
    private fun updateDeleteIconVisibility() { deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE }
    private fun updateSelectAllCheckboxVisibility() { selectAllCheckbox.visibility = if (isDeleteMode) View.VISIBLE else View.GONE; adjustListViewMargin() }
    private fun updateSelectAllCheckboxState() { selectAllCheckbox.isChecked = selectedItems.size == adapter.count && adapter.count > 0 }
    private fun updateNavigationIconVisibility() { audioListIcon.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE }
    private fun adjustListViewMargin() { val lp = listView.layoutParams as FrameLayout.LayoutParams; lp.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (isDeleteMode) 190f else 150f, resources.displayMetrics).toInt(); listView.layoutParams = lp }

    private fun setupSelectAllCheckbox() { selectAllCheckbox.setOnClickListener { if (selectAllCheckbox.isChecked) { for (i in 0 until adapter.count) selectedItems.add(i) } else selectedItems.clear(); adapter.notifyDataSetChanged(); updateDeleteIconVisibility() } }
    private fun togglePlaceholder(empty: Boolean) {
        val noResults = empty && !searchView.query.isNullOrEmpty()
        val totalEmpty = empty && searchView.query.isNullOrEmpty()
        
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
        view?.findViewById<TextView>(R.id.placeholderText)?.apply {
            text = when {
                totalEmpty -> getString(R.string.empty_report_message)
                noResults -> getString(R.string.empty_report_search_message)
                else -> ""
            }
            visibility = if (totalEmpty || noResults) View.VISIBLE else View.GONE
        }
        
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        searchView.visibility = if (totalEmpty && adapter.count == 0) View.GONE else View.VISIBLE
    }
    private fun openFileDetailsFragment(name: String) {
        val full = viewModel.getFullFileName(name) ?: return
        parentFragmentManager.beginTransaction().replace(R.id.fragment_container, FileDetailsFragment().apply { arguments = Bundle().apply { putString("fileName", full) } }).addToBackStack(null).commit()
    }
    private fun openAudioListFragment() { parentFragmentManager.beginTransaction().replace(R.id.fragment_container, AudioListFragment()).addToBackStack(null).commit() }
}
