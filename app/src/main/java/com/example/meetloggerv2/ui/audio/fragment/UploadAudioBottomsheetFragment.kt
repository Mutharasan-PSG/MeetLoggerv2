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
import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialogHelper
import java.io.File

class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentUploadAudioBottomsheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UploadAudioViewModel by viewModels()
    private val authSession = AuthSession()
    private lateinit var networkMonitor: NetworkMonitor
    
    private var selectedAudioUri: Uri? = null
    private var isProcessing = false
    private val handler = Handler(Looper.getMainLooper())
    private val progressTimeoutRunnable = Runnable {
        if (_binding != null && binding.progressOverlay.visibility == View.VISIBLE) {
            Toast.makeText(context, R.string.msg_please_wait, Toast.LENGTH_SHORT).show()
        }
    }
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUri = uri
                val name = FileUtils.getFileNameFromUri(requireContext(), uri)
                binding.selectedAudioTextView.text = name
                
                val blueColor = ContextCompat.getColor(requireContext(), R.color.BLUE)
                val softBlueColor = android.graphics.Color.parseColor("#154361EE")
                
                binding.selectedFileContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(softBlueColor)
                binding.selectedFileIcon.imageTintList = android.content.res.ColorStateList.valueOf(blueColor)
                binding.selectedAudioTextView.setTextColor(blueColor)
                
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
        
        val variantColor = ContextCompat.getColor(requireContext(), R.color.onSurfaceVariant)
        binding.selectedFileContainer.backgroundTintList = null
        binding.selectedFileIcon.imageTintList = android.content.res.ColorStateList.valueOf(variantColor)
        binding.selectedAudioTextView.setTextColor(variantColor)
    }

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            isProcessing = state is UploadAudioViewModel.UploadUiState.Processing
            binding.progressOverlay.visibility = if (isProcessing) View.VISIBLE else View.GONE
            binding.touchBlockOverlay.visibility = if (isProcessing) View.VISIBLE else View.GONE
            
            if (isProcessing) {
                handler.removeCallbacks(progressTimeoutRunnable)
                handler.postDelayed(progressTimeoutRunnable, 7000)
            } else {
                handler.removeCallbacks(progressTimeoutRunnable)
            }
            
            updateDismissalState()
            
            when (state) {
                is UploadAudioViewModel.UploadUiState.Processing -> {
                    binding.progressText.text = state.stage
                    binding.processAudioButton.text = state.stage
                }
                is UploadAudioViewModel.UploadUiState.Processed -> {
                    binding.processAudioButton.text = getString(R.string.btn_process_audio)
                    Toast.makeText(context, "Processing started", Toast.LENGTH_LONG).show()
                }
                is UploadAudioViewModel.UploadUiState.Error -> {
                    binding.processAudioButton.text = getString(R.string.btn_process_audio)
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
        binding.processAudioButton.text = getString(R.string.btn_process_audio)
        handler.removeCallbacks(progressTimeoutRunnable)
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
        } else {
            AudioProcessingDialogHelper(
                context = requireContext(),
                lifecycleOwner = viewLifecycleOwner,
                userFilesLiveData = viewModel.userFiles,
                fetchUserFiles = {
                    val uid = authSession.currentUserId()
                    if (uid != null) viewModel.fetchUserFiles(uid)
                },
                onProcessingConfirmed = { speakers, followUp ->
                    processAudio(speakers, followUp)
                }
            ).show()
        }
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
