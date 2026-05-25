package com.example.meetloggerv2.ui.audio.fragment

import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.meetloggerv2.R
import com.example.meetloggerv2.databinding.FragmentUploadAudioBottomsheetBinding
import com.example.meetloggerv2.core.util.FileUtils
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.ui.audio.viewmodel.UploadAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.meetloggerv2.core.session.AuthSession
import java.io.File

class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentUploadAudioBottomsheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UploadAudioViewModel by viewModels()
    private val authSession = AuthSession()
    private lateinit var networkMonitor: NetworkMonitor
    
    private var selectedAudioUri: Uri? = null
    private var temporarySpeakerList: List<String>? = null
    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUri = uri
                binding.selectedAudioTextView.text = FileUtils.getFileNameFromUri(requireContext(), uri)
                binding.processAudioButton.isEnabled = true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUploadAudioBottomsheetBinding.inflate(inflater, container, false)
        networkMonitor = NetworkMonitor(requireContext())
        setupUI()
        setupObservers()
        return binding.root
    }

    private fun setupUI() {
        binding.processAudioButton.isEnabled = false
        binding.progressOverlay.visibility = View.GONE
        binding.uploadAudioButton.setOnClickListener { openAudioPicker() }
        binding.processAudioButton.setOnClickListener { checkAndRequestPermissions() }
        setupBackPressHandler()
    }

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            isProcessing = state is UploadAudioViewModel.UploadUiState.Processing
            binding.progressOverlay.visibility = if (isProcessing) View.VISIBLE else View.GONE
            binding.touchBlockOverlay.visibility = if (isProcessing) View.VISIBLE else View.GONE
            updateDismissalState()
            
            when (state) {
                is UploadAudioViewModel.UploadUiState.Processing -> binding.processAudioText.text = state.stage
                is UploadAudioViewModel.UploadUiState.Processed -> {
                    binding.processAudioText.text = "Process Audio"
                    Toast.makeText(context, "Processing started", Toast.LENGTH_LONG).show()
                }
                is UploadAudioViewModel.UploadUiState.Error -> {
                    binding.processAudioText.text = "Process Audio"
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
        networkMonitor.observe(viewLifecycleOwner) { isOnline -> if (!isOnline && isProcessing) abortCurrentOperation() }
    }

    private fun abortCurrentOperation() {
        isProcessing = false
        binding.progressOverlay.visibility = View.GONE
        binding.touchBlockOverlay.visibility = View.GONE
        binding.processAudioText.text = "Process Audio"
        updateDismissalState()
        Toast.makeText(requireContext(), "Aborted: No internet", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.6)
    }

    private fun setFixedBottomSheetHeight(percentage: Double) {
        val dialog = dialog as? BottomSheetDialog ?: return
        val b = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheetBehavior = BottomSheetBehavior.from(b)
        val dm = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(dm)
        b.layoutParams.height = (dm.heightPixels * percentage).toInt()
        b.requestLayout()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun updateDismissalState() {
        val d = dialog as? BottomSheetDialog ?: return
        d.setCancelable(!isProcessing)
        d.setCanceledOnTouchOutside(!isProcessing)
        if (::bottomSheetBehavior.isInitialized) bottomSheetBehavior.isDraggable = !isProcessing
    }

    private fun openAudioPicker() {
        audioPickerLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) })
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003)
        } else showSpeakerSelectionDialog()
    }

    private fun showSpeakerSelectionDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_selection, null)
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        v.findViewById<Button>(R.id.proceedButton).setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkedId == R.id.radioYes) { d.dismiss(); showSpeakerInputDialog() }
            else { d.dismiss(); showFollowUpSelectionDialog() }
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun showSpeakerInputDialog() {
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
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        addSpeakerBtn.setOnClickListener { addInput() }
        proceedBtn.setOnClickListener {
            temporarySpeakerList = speakerList.filter { it.isNotBlank() }
            d.dismiss()
            showFollowUpSelectionDialog()
        }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    private fun showFollowUpSelectionDialog() {
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
            d.dismiss(); processAudio(temporarySpeakerList ?: emptyList(), followUp)
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.show()
    }

    private fun processAudio(speakers: List<String>, followUp: String) {
        selectedAudioUri?.let { uri ->
            val file = FileUtils.uriToFile(requireContext(), uri)
            val uid = authSession.currentUserId()
            if (file != null && uid != null) viewModel.processAudio(uid, file, uri, speakers, followUp)
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!isProcessing) { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() } }
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
