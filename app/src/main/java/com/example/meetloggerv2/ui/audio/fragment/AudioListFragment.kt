package com.example.meetloggerv2.ui.audio.fragment

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.media.AudioPlayerManager
import com.example.meetloggerv2.core.util.ViewDragHelper
import com.example.meetloggerv2.ui.audio.viewmodel.AudioListViewModel
import java.io.File
import java.util.Locale
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialogHelper
import com.example.meetloggerv2.core.util.UIUtils

class AudioListFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var tickIcon: ImageView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var progressOverlay: FrameLayout
    private lateinit var mainContent: FrameLayout
    private lateinit var noInternetContainer: LinearLayout
    private lateinit var touchBlockOverlay: FrameLayout
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var miniPlayer: View
    private lateinit var playPauseButton: ImageView
    private lateinit var currentAudioName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var progressText: TextView
    
    private val progressTimeoutRunnable = Runnable {
        if (isAdded && progressOverlay.visibility == View.VISIBLE) {
            Toast.makeText(context, R.string.msg_please_wait, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val viewModel: AudioListViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var networkMonitor: NetworkMonitor
    
    private var currentAudioIndex: Int = -1
    private var isDeleteMode = false
    private var isRenaming = false
    private var renamingPosition = -1
    private var isProcessing = false
    private val selectedItems = HashSet<Int>()

    private val downloadFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                saveAudioToUri(pendingDownloadFileName ?: "", uri)
            }
        }
    }
    private var pendingDownloadFileName: String? = null
    private var pendingPlayFileName: String? = null
    private var pendingShareFileName: String? = null
    private var pendingProcessFileName: String? = null
    private var pendingProcessSpeakers: List<String>? = null
    private var pendingProcessFollowUp: String? = null
    private var pendingDownloadUri: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_audio_list, container, false)
        networkMonitor = NetworkMonitor(requireContext())
        initializeViews(view)
        setupListView()
        setupMediaControls()
        setupDeleteIcon()
        setupMiniPlayerDragging()
        setupBackPressHandler()
        setupSelectAllCheckbox()
        setupObservers()
        checkInternetAndLoad()
        return view
    }

    private fun initializeViews(view: View) {
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        UIUtils.setupSearchViewClickToFocus(searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon)
        tickIcon = view.findViewById(R.id.tickIcon)
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        progressOverlay = view.findViewById(R.id.progressOverlay)
        progressText = view.findViewById(R.id.progressText)
        mainContent = view.findViewById(R.id.mainContent)
        noInternetContainer = view.findViewById(R.id.noInternetContainer)
        touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        currentAudioName = view.findViewById(R.id.currentAudioName)
        seekBar = view.findViewById(R.id.seekBar)
        currentTime = view.findViewById(R.id.currentTime)
        totalTime = view.findViewById(R.id.totalTime)
        
        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<ImageView>(R.id.stopButton).setOnClickListener { audioPlayer.stop(); miniPlayer.visibility = View.GONE }
        view.findViewById<ImageView>(R.id.prevButton).setOnClickListener { playPreviousAudio() }
        view.findViewById<ImageView>(R.id.nextButton).setOnClickListener { playNextAudio() }
    }

    private fun showProgress(message: String) {
        progressText.text = message
        progressOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(progressTimeoutRunnable)
        handler.postDelayed(progressTimeoutRunnable, 7000)
    }

    private fun hideProgress() {
        progressOverlay.visibility = View.GONE
        handler.removeCallbacks(progressTimeoutRunnable)
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item_3, R.id.textViewFileName, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)
                val textView = view.findViewById<TextView>(R.id.textViewFileName)
                val editText = view.findViewById<EditText>(R.id.editTextFileName)
                val itemLayout = view.findViewById<LinearLayout>(R.id.listItemLayout)

                checkbox.visibility = if (isDeleteMode && !isRenaming) View.VISIBLE else View.GONE
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnClickListener { if (checkbox.isChecked) selectedItems.add(position) else selectedItems.remove(position); updateDeleteIconVisibility(); updateSelectAllCheckboxState() }

                menuIcon.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE
                menuIcon.setOnClickListener { showOptionsPopup(menuIcon, position) }

                if (position == renamingPosition && isRenaming) {
                    textView.visibility = View.GONE
                    editText.visibility = View.VISIBLE
                    editText.setText(getItem(position))
                    itemLayout.isClickable = false
                    itemLayout.isFocusable = false
                } else {
                    textView.visibility = View.VISIBLE
                    editText.visibility = View.GONE
                    textView.text = getItem(position)
                    itemLayout.isClickable = true
                    itemLayout.isFocusable = true
                }

                itemLayout.setOnClickListener {
                    if (!isDeleteMode && !isRenaming) {
                        currentAudioIndex = position
                        downloadAndPlayAudio(getItem(position)!!)
                    } else if (!isRenaming) {
                        if (selectedItems.contains(position)) selectedItems.remove(position) else selectedItems.add(position)
                        notifyDataSetChanged()
                        updateDeleteIconVisibility()
                        updateSelectAllCheckboxState()
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

    private fun setupObservers() {
        viewModel.filteredAudioFiles.observe(viewLifecycleOwner) { files ->
            adapter.clear(); adapter.addAll(files); togglePlaceholder(files.isEmpty())
        }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AudioListViewModel.AudioUiState.Loading -> { showProgress(state.message); isProcessing = true }
                is AudioListViewModel.AudioUiState.Idle -> { hideProgress(); isProcessing = false }
                is AudioListViewModel.AudioUiState.Error -> { hideProgress(); isProcessing = false; Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show() }
                is AudioListViewModel.AudioUiState.Processed -> { hideProgress(); isProcessing = false; Toast.makeText(context, "Ready!", Toast.LENGTH_SHORT).show() }
            }
        }
        viewModel.audioEvent.observe(viewLifecycleOwner) { event ->
            val content = event.getContentIfNotHandled() ?: return@observe
            when (content) {
                is AudioListViewModel.AudioEvent.DownloadFileSuccess -> {
                    hideProgress()
                    val fileName = content.fileName.substringBeforeLast(".mp3")
                    if (pendingPlayFileName == fileName) {
                        pendingPlayFileName = null
                        startPlayback(content.localFile.absolutePath, fileName)
                    }
                    if (pendingShareFileName == fileName) {
                        pendingShareFileName = null
                        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", content.localFile)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { 
                            type = "audio/mpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) 
                        }, "Share"))
                    }
                    if (pendingProcessFileName == fileName) {
                        val speakers = pendingProcessSpeakers ?: emptyList()
                        val followUp = pendingProcessFollowUp ?: ""
                        processAudioWithDownloadUrl(content.localFile, fileName, speakers, followUp)
                    }
                }
                is AudioListViewModel.AudioEvent.DownloadFileError -> {
                    hideProgress()
                    pendingPlayFileName = null
                    pendingShareFileName = null
                    pendingProcessFileName = null
                    Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                }
                is AudioListViewModel.AudioEvent.DownloadUrlSuccess -> {
                    val speakers = pendingProcessSpeakers ?: emptyList()
                    val followUp = pendingProcessFollowUp ?: ""
                    val fileName = content.fileName
                    if (pendingProcessFileName == fileName.substringBeforeLast(".mp3")) {
                        pendingProcessFileName = null
                        pendingProcessSpeakers = null
                        pendingProcessFollowUp = null
                        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
                        viewModel.processAudio(localFile, speakers, followUp, fileName, content.url)
                    }
                }
                is AudioListViewModel.AudioEvent.DownloadUrlError -> {
                    hideProgress()
                    pendingProcessFileName = null
                    pendingProcessSpeakers = null
                    pendingProcessFollowUp = null
                    Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                }
                is AudioListViewModel.AudioEvent.DownloadBytesSuccess -> {
                    hideProgress()
                    val uri = pendingDownloadUri
                    if (uri != null) {
                        pendingDownloadUri = null
                        pendingDownloadFileName = null
                        requireContext().contentResolver.openOutputStream(uri)?.use { it.write(content.bytes) }
                        Toast.makeText(requireContext(), "Downloaded successfully", Toast.LENGTH_SHORT).show()
                    }
                }
                is AudioListViewModel.AudioEvent.DownloadBytesError -> {
                    hideProgress()
                    pendingDownloadUri = null
                    pendingDownloadFileName = null
                    Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (isOnline) {
                if (noInternetContainer.visibility == View.VISIBLE) {
                    checkInternetAndLoad()
                }
            } else {
                mainContent.visibility = View.GONE
                noInternetContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun checkInternetAndLoad() {
        if (NetworkUtil.isNetworkAvailable(requireContext())) {
            mainContent.visibility = View.VISIBLE; noInternetContainer.visibility = View.GONE
            viewModel.fetchAudioFiles()
        } else {
            mainContent.visibility = View.GONE; noInternetContainer.visibility = View.VISIBLE
        }
    }

    private fun setupMediaControls() {
        playPauseButton.setOnClickListener { 
            if (audioPlayer.togglePlayPause()) {
                playPauseButton.setImageResource(R.drawable.pause1)
            } else {
                playPauseButton.setImageResource(R.drawable.play)
            }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if (f) { audioPlayer.seekTo(p); currentTime.text = audioPlayer.formatTime(p) } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?): Boolean {
                viewModel.setQuery(q ?: "")
                return true
            }
            override fun onQueryTextChange(n: String?): Boolean {
                viewModel.setQuery(n ?: "")
                return true
            }
        })
    }

    private fun downloadAndPlayAudio(fileName: String) {
        if (!NetworkUtil.isNetworkAvailable(requireContext())) return
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$fileName.mp3")
        if (localFile.exists()) startPlayback(localFile.absolutePath, fileName) else {
            showProgress("Downloading...")
            pendingPlayFileName = fileName
            viewModel.downloadAudioFile("$fileName.mp3", localFile)
        }
    }

    private fun startPlayback(path: String, name: String) {
        currentAudioName.text = name; miniPlayer.visibility = View.VISIBLE; playPauseButton.setImageResource(R.drawable.pause1)
        audioPlayer.play(path, { playNextAudio() }) { curr, dur ->
            seekBar.max = dur; seekBar.progress = curr; currentTime.text = audioPlayer.formatTime(curr); totalTime.text = audioPlayer.formatTime(dur)
        }
    }

    private fun playNextAudio() { if (adapter.count > 0) { currentAudioIndex = (currentAudioIndex + 1) % adapter.count; downloadAndPlayAudio(adapter.getItem(currentAudioIndex)!!) } }
    private fun playPreviousAudio() { if (adapter.count > 0) { currentAudioIndex = if (currentAudioIndex > 0) currentAudioIndex - 1 else adapter.count - 1; downloadAndPlayAudio(adapter.getItem(currentAudioIndex)!!) } }

    private fun toggleDeleteMode(enable: Boolean) {
        isDeleteMode = enable; if (!enable) selectedItems.clear()
        adapter.notifyDataSetChanged(); updateDeleteIconVisibility(); updateSelectAllCheckboxVisibility(); updateSelectAllCheckboxState()
    }

    private fun updateDeleteIconVisibility() { deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE }
    private fun updateSelectAllCheckboxVisibility() { selectAllCheckbox.visibility = if (isDeleteMode) View.VISIBLE else View.GONE; adjustListViewMargin() }
    private fun updateSelectAllCheckboxState() { selectAllCheckbox.isChecked = selectedItems.size == adapter.count && adapter.count > 0 }

    private fun adjustListViewMargin() {
        val lp = listView.layoutParams as FrameLayout.LayoutParams
        lp.topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (isDeleteMode) 190f else 150f, resources.displayMetrics).toInt()
        listView.layoutParams = lp
    }

    private fun setupDeleteIcon() { deleteIcon.setOnClickListener { if (isDeleteMode && selectedItems.isNotEmpty() && !isProcessing) showDeleteConfirmationDialog() } }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.msg_delete_selected_audio).setPositiveButton(R.string.dialog_delete) { _, _ ->
            viewModel.deleteAudioFiles(selectedItems.map { adapter.getItem(it)!! }); toggleDeleteMode(false)
        }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun showOptionsPopup(anchor: View, pos: Int) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_menu_layout, null)
        val list = popupView.findViewById<ListView>(R.id.popup_list)
        val opts = listOf("RENAME", "DOWNLOAD", "SHARE", "SUMMARIZE")
        
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
            val fileName = this@AudioListFragment.adapter.getItem(pos)
            if (fileName != null) {
                when (opts[i]) {
                    "RENAME" -> startRenaming(pos)
                    "DOWNLOAD" -> { 
                        pendingDownloadFileName = fileName
                        downloadFileLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { 
                            type = "audio/mpeg"
                            putExtra(Intent.EXTRA_TITLE, "$fileName.mp3") 
                        }) 
                    }
                    "SHARE" -> {
                        shareAudio(fileName)
                    }
                    "SUMMARIZE" -> {
                        AudioProcessingDialogHelper(
                            context = requireContext(),
                            lifecycleOwner = viewLifecycleOwner,
                            userFilesLiveData = viewModel.userFiles,
                            fetchUserFiles = { viewModel.fetchUserFiles() },
                            onProcessingConfirmed = { speakers, followUp ->
                                startAudioProcessing(fileName, speakers, followUp)
                            }
                        ).show()
                    }
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

    private fun shareAudio(name: String) {
        val temp = File(requireContext().cacheDir, "$name.mp3")
        showProgress("Loading...")
        pendingShareFileName = name
        viewModel.downloadAudioFile("$name.mp3", temp)
    }


    private fun startAudioProcessing(name: String, speakers: List<String>, followUp: String) {
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$name.mp3")
        
        if (localFile.exists()) {
            showProgress("Processing...")
            processAudioWithDownloadUrl(localFile, name, speakers, followUp)
        } else {
            showProgress("Downloading...")
            pendingProcessFileName = name
            pendingProcessSpeakers = speakers
            pendingProcessFollowUp = followUp
            viewModel.downloadAudioFile("$name.mp3", localFile)
        }
    }

    private fun processAudioWithDownloadUrl(localFile: File, name: String, speakers: List<String>, followUp: String) {
        pendingProcessFileName = name
        pendingProcessSpeakers = speakers
        pendingProcessFollowUp = followUp
        viewModel.getAudioDownloadUrl("$name.mp3")
    }

    private fun startRenaming(pos: Int) {
        isRenaming = true
        renamingPosition = pos
        toggleDeleteMode(false)
        adapter.notifyDataSetChanged()
        tickIcon.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE

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

        tickIcon.setOnClickListener {
            val edit = listView.getChildAt(pos - listView.firstVisiblePosition)
                ?.findViewById<EditText>(R.id.editTextFileName)
            val newName = edit?.text?.toString()?.trim()
            if (!newName.isNullOrEmpty() && newName != adapter.getItem(pos)) {
                progressOverlay.visibility = View.VISIBLE
                viewModel.renameAudioFile(adapter.getItem(pos)!!, newName)
            }
            cleanupRenamingMode()
        }
    }

    private fun cleanupRenamingMode() { 
        isRenaming = false
        renamingPosition = -1
        tickIcon.visibility = View.GONE
        touchBlockOverlay.visibility = View.GONE
        adapter.notifyDataSetChanged()
        
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(listView.windowToken, 0)
    }

    private fun saveAudioToUri(name: String, uri: Uri) {
        showProgress("Downloading...")
        pendingDownloadUri = uri
        pendingDownloadFileName = name
        viewModel.downloadAudioBytes("$name.mp3")
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isProcessing) Toast.makeText(context, "Wait...", Toast.LENGTH_SHORT).show()
                else if (isRenaming) cleanupRenamingMode() else if (isDeleteMode) toggleDeleteMode(false) else { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun setupSelectAllCheckbox() { selectAllCheckbox.setOnClickListener { if (selectAllCheckbox.isChecked) { for (i in 0 until adapter.count) selectedItems.add(i) } else selectedItems.clear(); adapter.notifyDataSetChanged(); updateDeleteIconVisibility() } }

    private fun togglePlaceholder(empty: Boolean) {
        val totalFilesEmpty = viewModel.filteredAudioFiles.value.isNullOrEmpty() && searchView.query.isNullOrEmpty()
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
        
        val noResults = empty && !searchView.query.isNullOrEmpty()
        view?.findViewById<TextView>(R.id.placeholderText)?.apply {
            text = when {
                totalFilesEmpty -> getString(R.string.empty_audio_message)
                noResults -> getString(R.string.empty_audio_search_message)
                else -> ""
            }
            visibility = if (totalFilesEmpty || noResults) View.VISIBLE else View.GONE
        }
        
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        searchView.visibility = if (totalFilesEmpty && adapter.count == 0) View.GONE else View.VISIBLE
    }

    private fun setupMiniPlayerDragging() {
        ViewDragHelper().attach(miniPlayer)
    }

    override fun onDestroy() { super.onDestroy(); audioPlayer.stop() }
}
