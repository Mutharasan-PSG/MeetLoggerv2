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
import com.example.meetloggerv2.core.session.AuthSession
import java.io.File
import java.util.Locale

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
    
    private val viewModel: AudioListViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentAudioIndex: Int = -1
    private var isDeleteMode = false
    private var isRenaming = false
    private var renamingPosition = -1
    private var isProcessing = false
    private val selectedItems = HashSet<Int>()
    private val authSession = AuthSession()

    private val downloadFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                saveAudioToUri(pendingDownloadFileName ?: "", uri)
            }
        }
    }
    private var pendingDownloadFileName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_audio_list, container, false)
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
        deleteIcon = view.findViewById(R.id.deleteIcon)
        tickIcon = view.findViewById(R.id.tickIcon)
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        progressOverlay = view.findViewById(R.id.progressOverlay)
        mainContent = view.findViewById(R.id.mainContent)
        noInternetContainer = view.findViewById(R.id.noInternetContainer)
        touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        currentAudioName = view.findViewById(R.id.currentAudioName)
        seekBar = view.findViewById(R.id.seekBar)
        currentTime = view.findViewById(R.id.currentTime)
        totalTime = view.findViewById(R.id.totalTime)
        
        view.findViewById<ImageView>(R.id.stopButton).setOnClickListener { audioPlayer.stop(); miniPlayer.visibility = View.GONE }
        view.findViewById<ImageView>(R.id.prevButton).setOnClickListener { playPreviousAudio() }
        view.findViewById<ImageView>(R.id.nextButton).setOnClickListener { playNextAudio() }
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item_3, R.id.textViewFileName, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)
                val textView = view.findViewById<TextView>(R.id.textViewFileName)
                val editText = view.findViewById<EditText>(R.id.editTextFileName)

                checkbox.visibility = if (isDeleteMode && !isRenaming) View.VISIBLE else View.GONE
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnClickListener { if (checkbox.isChecked) selectedItems.add(position) else selectedItems.remove(position); updateDeleteIconVisibility(); updateSelectAllCheckboxState() }

                menuIcon.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE
                menuIcon.setOnClickListener { showOptionsPopup(menuIcon, position) }

                if (position == renamingPosition && isRenaming) {
                    textView.visibility = View.GONE; editText.visibility = View.VISIBLE; editText.setText(getItem(position))
                } else {
                    textView.visibility = View.VISIBLE; editText.visibility = View.GONE; textView.text = getItem(position)
                }
                return view
            }
        }
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming) { currentAudioIndex = position; downloadAndPlayAudio(adapter.getItem(position)!!) }
            else if (!isRenaming) {
                if (selectedItems.contains(position)) selectedItems.remove(position) else selectedItems.add(position)
                adapter.notifyDataSetChanged(); updateDeleteIconVisibility(); updateSelectAllCheckboxState()
            }
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming) { toggleDeleteMode(true); selectedItems.add(position); adapter.notifyDataSetChanged(); updateDeleteIconVisibility(); true }
            else false
        }
    }

    private fun setupObservers() {
        viewModel.filteredAudioFiles.observe(viewLifecycleOwner) { files ->
            adapter.clear(); adapter.addAll(files); togglePlaceholder(files.isEmpty())
        }
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AudioListViewModel.AudioUiState.Loading -> { progressOverlay.visibility = View.VISIBLE; isProcessing = true }
                is AudioListViewModel.AudioUiState.Idle -> { progressOverlay.visibility = View.GONE; isProcessing = false }
                is AudioListViewModel.AudioUiState.Error -> { progressOverlay.visibility = View.GONE; isProcessing = false; Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show() }
                is AudioListViewModel.AudioUiState.Processed -> { progressOverlay.visibility = View.GONE; isProcessing = false; Toast.makeText(context, "Ready!", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun checkInternetAndLoad() {
        if (isNetworkAvailable()) {
            mainContent.visibility = View.VISIBLE; noInternetContainer.visibility = View.GONE
            val userId = authSession.currentUserId(); if (userId != null) viewModel.fetchAudioFiles(userId)
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
        if (!isNetworkAvailable()) return
        val userId = authSession.currentUserId() ?: return
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$fileName.mp3")
        if (localFile.exists()) startPlayback(localFile.absolutePath, fileName) else {
            progressOverlay.visibility = View.VISIBLE
            viewModel.downloadAudioFile(userId, "$fileName.mp3", localFile) { success, _ ->
                progressOverlay.visibility = View.GONE
                if (success) startPlayback(localFile.absolutePath, fileName)
            }
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
            val userId = authSession.currentUserId(); if (userId != null) viewModel.deleteAudioFiles(userId, selectedItems.map { adapter.getItem(it)!! }); toggleDeleteMode(false)
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
                        progressOverlay.visibility = View.VISIBLE
                        shareAudio(fileName)
                    }
                    "SUMMARIZE" -> showSpeakerSelectionDialog(fileName)
                }
            }
            popup.dismiss()
        }
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    private fun shareAudio(name: String) {
        val userId = authSession.currentUserId() ?: return
        val temp = File(requireContext().cacheDir, "$name.mp3")
        viewModel.downloadAudioFile(userId, "$name.mp3", temp) { success, _ ->
            if (success) {
                progressOverlay.visibility = View.GONE
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", temp)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { 
                    type = "audio/mpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) 
                }, "Share"))
            } else {
                progressOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to prepare file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSpeakerSelectionDialog(name: String) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_selection, null)
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        v.findViewById<Button>(R.id.proceedButton).setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkedId == R.id.radioYes) {
                d.dismiss()
                showSpeakerInputDialog(name)
            } else {
                d.dismiss()
                showFollowUpSelectionDialog(name, emptyList())
            }
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun showSpeakerInputDialog(name: String) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_input, null)
        val container = v.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerBtn = v.findViewById<Button>(R.id.addSpeakerButton)
        val proceedBtn = v.findViewById<Button>(R.id.proceedButton)
        val speakerList = mutableListOf<String>()

        val updateButtons = {
            val allFilled = speakerList.all { it.isNotBlank() } && speakerList.isNotEmpty()
            proceedBtn.isEnabled = allFilled
            addSpeakerBtn.isEnabled = allFilled && speakerList.size < 10
        }

        val addInput = {
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_speaker_input, container, false)
            val idx = speakerList.size
            speakerList.add("")
            val input = item.findViewById<EditText>(R.id.speakerNameInput)
            item.findViewById<TextView>(R.id.speakerLabel).text = "Speaker ${('A' + idx)}"
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    speakerList[idx] = s.toString().trim()
                    updateButtons()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            container.addView(item)
            input.post {
                input.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            updateButtons()
        }

        addInput()
        val d_input = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        addSpeakerBtn.setOnClickListener { addInput() }
        proceedBtn.setOnClickListener {
            val filtered = speakerList.filter { it.isNotBlank() }
            d_input.dismiss()
            showFollowUpSelectionDialog(name, filtered)
        }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d_input.dismiss()
            showSpeakerSelectionDialog(name)
        }
        d_input.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d_input.show()
    }

    private fun showFollowUpSelectionDialog(name: String, speakers: List<String>) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_follow_up_selection, null)
        val spinner = v.findViewById<Spinner>(R.id.spinnerFiles)
        val proceed = v.findViewById<Button>(R.id.proceedButton)
        v.findViewById<RadioGroup>(R.id.radioGroup).setOnCheckedChangeListener { _, id ->
            spinner.visibility = if (id == R.id.radioYes) View.VISIBLE else View.GONE
            if (id == R.id.radioYes) {
                proceed.isEnabled = false
                authSession.currentUserId()?.let { viewModel.fetchUserFiles(it) }
            } else proceed.isEnabled = true
        }
        viewModel.userFiles.observe(viewLifecycleOwner) { files ->
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, files.map { it.substringBeforeLast(".") })
            spinner.setTag(files)
            proceed.isEnabled = true
        }
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        proceed.setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val followUp = if (checkedId == R.id.radioYes) {
                (spinner.tag as? List<String>)?.getOrNull(spinner.selectedItemPosition) ?: ""
            } else ""
            d.dismiss()
            startAudioProcessing(name, speakers, followUp)
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d.dismiss()
            showSpeakerSelectionDialog(name)
        }
        d.show()
    }

    private fun startAudioProcessing(name: String, speakers: List<String>, followUp: String) {
        val userId = authSession.currentUserId() ?: return
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$name.mp3")
        
        if (localFile.exists()) {
            progressOverlay.visibility = View.VISIBLE
            processAudioWithDownloadUrl(userId, localFile, name, speakers, followUp)
        } else {
            progressOverlay.visibility = View.VISIBLE
            viewModel.downloadAudioFile(userId, "$name.mp3", localFile) { success, _ ->
                if (success) {
                    processAudioWithDownloadUrl(userId, localFile, name, speakers, followUp)
                } else {
                    progressOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun processAudioWithDownloadUrl(userId: String, localFile: File, name: String, speakers: List<String>, followUp: String) {
        viewModel.getAudioDownloadUrl(userId, "$name.mp3") { audioUrl, _ ->
            if (audioUrl != null) {
                viewModel.processAudio(userId, localFile, speakers, followUp, "$name.mp3", audioUrl)
            } else {
                progressOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to get audio URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRenaming(pos: Int) { 
        isRenaming = true; renamingPosition = pos; toggleDeleteMode(false); 
        adapter.notifyDataSetChanged(); tickIcon.visibility = View.VISIBLE; touchBlockOverlay.visibility = View.VISIBLE
        tickIcon.setOnClickListener {
            val edit = listView.getChildAt(pos - listView.firstVisiblePosition)?.findViewById<EditText>(R.id.editTextFileName)
            val newName = edit?.text?.toString()?.trim()
            if (!newName.isNullOrEmpty() && newName != adapter.getItem(pos)) {
                progressOverlay.visibility = View.VISIBLE
                val userId = authSession.currentUserId()
                if (userId != null) viewModel.renameAudioFile(userId, adapter.getItem(pos)!!, newName)
            }
            cleanupRenamingMode()
        }
    }

    private fun cleanupRenamingMode() { isRenaming = false; renamingPosition = -1; tickIcon.visibility = View.GONE; touchBlockOverlay.visibility = View.GONE; adapter.notifyDataSetChanged() }

    private fun saveAudioToUri(name: String, uri: Uri) {
        val userId = authSession.currentUserId() ?: return
        progressOverlay.visibility = View.VISIBLE
        viewModel.downloadAudioBytes(userId, "$name.mp3") { bytes, _ ->
            if (bytes != null) {
                progressOverlay.visibility = View.GONE
                requireContext().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(requireContext(), "Downloaded successfully", Toast.LENGTH_SHORT).show()
            } else {
                progressOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } ?: false
    }

    private val internetCheckTask = object : Runnable {
        override fun run() {
            if (isNetworkAvailable()) {
                if (noInternetContainer.visibility == View.VISIBLE) checkInternetAndLoad()
            } else {
                mainContent.visibility = View.GONE
                noInternetContainer.visibility = View.VISIBLE
            }
            handler.postDelayed(this, 2000)
        }
    }

    private fun togglePlaceholder(empty: Boolean) {
        val totalFilesEmpty = viewModel.filteredAudioFiles.value.isNullOrEmpty() && searchView.query.isNullOrEmpty()
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = if (totalFilesEmpty) View.VISIBLE else View.GONE
        
        val noResults = empty && !searchView.query.isNullOrEmpty()
        view?.findViewById<TextView>(R.id.placeholderText)?.apply {
            text = if (noResults) "No audio files found matching your search" else ""
            visibility = if (noResults) View.VISIBLE else View.GONE
        }
        
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        searchView.visibility = if (totalFilesEmpty && adapter.count == 0) View.GONE else View.VISIBLE
    }

    private fun setupMiniPlayerDragging() {
        ViewDragHelper().attach(miniPlayer)
    }

    override fun onResume() { super.onResume(); handler.post(internetCheckTask) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(internetCheckTask) }
    override fun onDestroy() { super.onDestroy(); audioPlayer.stop() }
}
