package com.example.meetloggerv2.ui.report

import android.graphics.Color
import androidx.appcompat.app.AlertDialog
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
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.audio.AudioListFragment
import com.example.meetloggerv2.ui.details.FileDetailsFragment
import com.example.meetloggerv2.util.DocumentExportManager
import com.example.meetloggerv2.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream

class ReportFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var progressOverlay: FrameLayout
    private lateinit var mainContent: RelativeLayout
    private lateinit var noInternetContainer: LinearLayout
    private lateinit var touchBlockOverlay: FrameLayout
    private lateinit var audioListIcon: ImageView
    private lateinit var adapter: ArrayAdapter<Triple<String, com.google.firebase.Timestamp, String>>
    
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
        deleteIcon = view.findViewById(R.id.deleteIcon); selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        progressOverlay = view.findViewById(R.id.progressOverlay); mainContent = view.findViewById(R.id.mainContent)
        noInternetContainer = view.findViewById(R.id.noInternetContainer); touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        audioListIcon = view.findViewById(R.id.audioListIcon)
        audioListIcon.setOnClickListener { openAudioListFragment() }
        view.findViewById<ImageView>(R.id.tickIcon).setOnClickListener { finishRenaming() }
        deleteIcon.setOnClickListener { if (isDeleteMode && selectedItems.isNotEmpty()) showDeleteConfirmationDialog() }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("Delete selected reports?")
            .setPositiveButton("Delete") { _, _ ->
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                val filesToDelete = selectedItems.mapNotNull { 
                    val shortName = adapter.getItem(it)?.first
                    if (shortName != null) viewModel.getFullFileName(shortName) else null
                }
                viewModel.deleteFiles(userId, filesToDelete)
                toggleDeleteMode(false)
            }
            .setNegativeButton("Cancel", null)
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
                
                cb.visibility = if (isDeleteMode && !isRenaming) View.VISIBLE else View.GONE
                cb.isChecked = selectedItems.contains(position)
                cb.setOnClickListener { if (cb.isChecked) selectedItems.add(position) else selectedItems.remove(position); updateDeleteIconVisibility(); updateSelectAllCheckboxState() }
                
                mi.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE
                mi.setOnClickListener { showOptionsPopup(mi, position) }
                
                if (position == renamingPosition && isRenaming) { tv.visibility = View.GONE; et.visibility = View.VISIBLE; et.setText(name) }
                else { tv.visibility = View.VISIBLE; et.visibility = View.GONE; tv.text = name }
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, i, _ -> 
            if (isDeleteMode) {
                if (selectedItems.contains(i)) selectedItems.remove(i) else selectedItems.add(i)
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                updateSelectAllCheckboxState()
            } else if (!isRenaming) {
                openFileDetailsFragment(adapter.getItem(i)!!.first)
            }
        }
        listView.setOnItemLongClickListener { _, _, i, _ ->
            if (!isDeleteMode && !isRenaming) {
                toggleDeleteMode(true)
                selectedItems.add(i)
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                true
            } else false
        }
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
                is ReportViewModel.ReportUiState.Loading -> { progressOverlay.visibility = View.VISIBLE; isProcessing = true }
                is ReportViewModel.ReportUiState.Idle -> { progressOverlay.visibility = View.GONE; isProcessing = false }
                is ReportViewModel.ReportUiState.Error -> { progressOverlay.visibility = View.GONE; isProcessing = false; Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show() }
            }
        }
        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (isOnline) {
                mainContent.visibility = View.VISIBLE
                noInternetContainer.visibility = View.GONE
                FirebaseAuth.getInstance().currentUser?.uid?.let { viewModel.fetchFiles(it) }
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
                v.setTextColor(Color.BLACK)
                return v
            }
        }
        list.adapter = popupAdapter
        
        val popup = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.dialog_holo_light_frame))
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
    }

    private fun startRenaming(pos: Int) { 
        isRenaming = true; renamingPosition = pos; adapter.notifyDataSetChanged()
        view?.findViewById<ImageView>(R.id.tickIcon)?.visibility = View.VISIBLE 
        updateNavigationIconVisibility()
    }

    private fun finishRenaming() {
        val view = listView.getChildAt(renamingPosition - listView.firstVisiblePosition)
        val name = view?.findViewById<EditText>(R.id.editTextFileName)?.text.toString().trim()
        val oldFull = viewModel.getFullFileName(adapter.getItem(renamingPosition)!!.first)
        if (name.isNotEmpty() && oldFull != null) {
            val ext = oldFull.substringAfterLast(".")
            viewModel.renameFile(FirebaseAuth.getInstance().currentUser?.uid ?: "", oldFull, "$name.$ext")
        }
        cleanupRenamingMode()
    }

    private fun cleanupRenamingMode() { 
        isRenaming = false; renamingPosition = -1
        view?.findViewById<ImageView>(R.id.tickIcon)?.visibility = View.GONE
        adapter.notifyDataSetChanged()
        updateNavigationIconVisibility()
    }

    private fun showExportDialog(name: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            performExport(name, "PDF")
        }
        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            performExport(name, "DOCX")
        }
        dialog.show()
    }

    private fun performExport(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        viewModel.fetchFileDetails(FirebaseAuth.getInstance().currentUser?.uid ?: "", full) { content ->
            pendingContent = content
            pendingFormat = format
            val mimeType = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val ext = if (format == "PDF") "pdf" else "docx"
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { 
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, "$name.$ext") 
            }
            exportFileLauncher.launch(intent)
        }
    }

    private fun showShareDialog(name: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()
        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            performShare(name, "PDF")
        }
        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            performShare(name, "DOCX")
        }
        dialog.show()
    }

    private fun performShare(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        viewModel.fetchFileDetails(FirebaseAuth.getInstance().currentUser?.uid ?: "", full) { content ->
            val ext = if (format == "PDF") "pdf" else "docx"
            val temp = File(requireContext().cacheDir, "$name.$ext")
            val os = FileOutputStream(temp)
            if (format == "PDF") exportManager.exportToPdf(content, os) else exportManager.exportToDocx(content, os)
            os.close()
            startActivity(Intent.createChooser(exportManager.getShareIntent(temp, format), "Share"))
        }
    }

    private fun saveContentToUri(uri: Uri, content: String, format: String) {
        requireContext().contentResolver.openOutputStream(uri)?.use { os ->
            if (format == "PDF") exportManager.exportToPdf(content, os) else exportManager.exportToDocx(content, os)
        }
    }
    private fun showCopyDialog(pos: Int) {
        val item = adapter.getItem(pos) ?: return
        val name = item.first
        val full = viewModel.getFullFileName(name) ?: return
        
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_audio, null)
        val edit = v.findViewById<EditText>(R.id.fileNameInput)
        val saveBtn = v.findViewById<Button>(R.id.saveFileButton)
        edit.setText("Copy of $name")
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Copy Report")
            .setView(v)
            .setCancelable(false)
            .create()
            
        val onCopyAction = {
            val newName = edit.text.toString().trim()
            if (newName.isNotEmpty()) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val ext = full.substringAfterLast(".", "mp3")
                    viewModel.copyFile(userId, full, "$newName.$ext")
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        saveBtn?.setOnClickListener { onCopyAction() }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Copy") { _, _ -> onCopyAction() }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { d, _ -> d.dismiss() }

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
    private fun adjustListViewMargin() { val lp = listView.layoutParams as RelativeLayout.LayoutParams; lp.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (isDeleteMode) 125f else 90f, resources.displayMetrics).toInt(); listView.layoutParams = lp }

    private fun setupSelectAllCheckbox() { selectAllCheckbox.setOnClickListener { if (selectAllCheckbox.isChecked) { for (i in 0 until adapter.count) selectedItems.add(i) } else selectedItems.clear(); adapter.notifyDataSetChanged(); updateDeleteIconVisibility() } }
    private fun togglePlaceholder(empty: Boolean) {
        val noResults = empty && !searchView.query.isNullOrEmpty()
        val totalEmpty = empty && searchView.query.isNullOrEmpty()
        
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = if (totalEmpty) View.VISIBLE else View.GONE
        view?.findViewById<TextView>(R.id.placeholderText)?.apply {
            text = if (noResults) "No reports found matching your search" else ""
            visibility = if (noResults) View.VISIBLE else View.GONE
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
