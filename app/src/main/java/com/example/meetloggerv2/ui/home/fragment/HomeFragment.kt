package com.example.meetloggerv2.ui.home.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.audio.fragment.AudioListFragment
import com.example.meetloggerv2.ui.audio.fragment.RecordAudioBottomsheetFragment
import com.example.meetloggerv2.ui.audio.fragment.UploadAudioBottomsheetFragment
import com.example.meetloggerv2.ui.home.adapter.FileListAdapter
import com.example.meetloggerv2.ui.home.viewmodel.HomeViewModel
import com.example.meetloggerv2.ui.profile.fragment.ProfileFragment
import com.example.meetloggerv2.ui.report.fragment.ReportFragment
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.meetloggerv2.core.session.AuthSession
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var profilePic: ImageView
    private lateinit var bottomNavBar: BottomNavigationView
    private lateinit var audioButton: LinearLayout
    private lateinit var audioOptionsLayout: LinearLayout
    private lateinit var audioOptionsOverlay: FrameLayout
    private lateinit var closeButton: ImageView
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var adapter: FileListAdapter
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var onBackPressedCallback: androidx.activity.OnBackPressedCallback
    
    private val viewModel: HomeViewModel by viewModels()
    private val fileList = ArrayList<Triple<String, String, com.google.firebase.Timestamp>>()
    private val filteredList = ArrayList<Triple<String, String, com.google.firebase.Timestamp>>()

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        networkMonitor = NetworkMonitor(requireContext())
        initializeViews(view)
        setupObservers()
        setupListeners(view)
        checkNotificationAndMicrophonePermissions()
        return view
    }

    private fun initializeViews(view: View) {
        profilePic = view.findViewById(R.id.profilePic)
        bottomNavBar = view.findViewById(R.id.bottomNavBar)
        audioButton = view.findViewById(R.id.AudioButton)
        audioOptionsLayout = view.findViewById(R.id.AudioOptionsLayout)
        audioOptionsOverlay = view.findViewById(R.id.audioOptionsOverlay)
        closeButton = view.findViewById(R.id.closeButton)
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        UIUtils.setupSearchViewClickToFocus(searchView)

        bottomNavBar.itemIconTintList = null
        bottomNavBar.selectedItemId = R.id.menu_home
        UIUtils.applyPoppinsFontToBottomNav(requireContext(), bottomNavBar)

        adapter = FileListAdapter(requireContext(), filteredList)
        listView.adapter = adapter

        // Register onBackPressedCallback, initially disabled
        onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                toggleAudioOptions(false)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun setupObservers() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            fileList.clear(); fileList.addAll(files)
            filterFiles(searchView.query?.toString())
        }

        viewModel.error.observe(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }

        viewModel.userProfile.observe(viewLifecycleOwner) { data ->
            data?.let {
                val name = it["name"] as? String ?: "User"
                val avatarDrawable = com.example.meetloggerv2.core.util.AvatarGenerator.getAvatar(requireContext(), name)
                Glide.with(this)
                    .load(it["photoUrl"] as? String)
                    .placeholder(avatarDrawable)
                    .error(avatarDrawable)
                    .fallback(avatarDrawable)
                    .circleCrop()
                    .into(profilePic)
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            val noNet = view?.findViewById<LinearLayout>(R.id.noInternetContainer)
            val mainContent = view?.findViewById<View>(R.id.mainContent)
            if (isOnline) {
                noNet?.visibility = View.GONE
                mainContent?.visibility = View.VISIBLE
                viewModel.fetchFiles()
            } else {
                noNet?.visibility = View.VISIBLE
                listView.visibility = View.GONE
                searchView.visibility = View.GONE
                view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
            }
        }
    }

    private fun setupListeners(view: View) {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true.also { filterFiles(query) }
            override fun onQueryTextChange(newText: String?) = true.also { filterFiles(newText) }
        })

        audioButton.setOnClickListener { toggleAudioOptions(true) }
        closeButton.setOnClickListener { toggleAudioOptions(false) }
        audioOptionsOverlay.setOnClickListener { toggleAudioOptions(false) }

        view.findViewById<LinearLayout>(R.id.RecordAudio).setOnClickListener {
            toggleAudioOptions(false)
            RecordAudioBottomsheetFragment().show(parentFragmentManager, "RecordAudioSheet")
        }

        view.findViewById<LinearLayout>(R.id.UploadAudio).setOnClickListener {
            toggleAudioOptions(false)
            UploadAudioBottomsheetFragment().show(parentFragmentManager, "UploadAudioSheet")
        }

        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> true
                R.id.menu_audio -> { navigateTo(AudioListFragment()); true }
                R.id.menu_report -> { navigateTo(ReportFragment()); true }
                else -> false
            }
        }

        profilePic.setOnClickListener { navigateTo(ProfileFragment()) }
        viewModel.loadUserProfile()
    }

    private fun toggleAudioOptions(show: Boolean) {
        onBackPressedCallback.isEnabled = show
        if (show) {
            // Cancel any running animations
            audioOptionsOverlay.animate().cancel()
            audioOptionsLayout.animate().cancel()
            audioButton.animate().cancel()
            closeButton.animate().cancel()

            // 1. Prepare and animate overlay in
            audioOptionsOverlay.alpha = 0f
            audioOptionsOverlay.visibility = View.VISIBLE
            audioOptionsOverlay.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            // 2. Prepare and animate AudioButton out
            audioButton.animate()
                .alpha(0f)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    audioButton.visibility = View.GONE
                }
                .start()

            // 3. Prepare and animate options panel in
            audioOptionsLayout.visibility = View.VISIBLE
            audioOptionsLayout.alpha = 0f
            audioOptionsLayout.scaleX = 0.3f
            audioOptionsLayout.scaleY = 0.3f

            // Post so pivot calculations are correct after view layout
            audioOptionsLayout.post {
                audioOptionsLayout.pivotX = audioOptionsLayout.width.toFloat()
                audioOptionsLayout.pivotY = audioOptionsLayout.height.toFloat()
                audioOptionsLayout.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                    .start()
            }

            // 4. Smoothly rotate close button into lock position (180deg)
            closeButton.rotation = 0f
            closeButton.animate()
                .rotation(180f)
                .setDuration(450)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                .start()
        } else {
            // Cancel any running animations
            audioOptionsOverlay.animate().cancel()
            audioOptionsLayout.animate().cancel()
            audioButton.animate().cancel()
            closeButton.animate().cancel()

            // 1. Animate overlay out
            audioOptionsOverlay.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    audioOptionsOverlay.visibility = View.GONE
                }
                .start()

            // 2. Animate options panel out
            audioOptionsLayout.pivotX = audioOptionsLayout.width.toFloat()
            audioOptionsLayout.pivotY = audioOptionsLayout.height.toFloat()
            audioOptionsLayout.animate()
                .alpha(0f)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    audioOptionsLayout.visibility = View.GONE
                }
                .start()

            // 3. Prepare and animate AudioButton back in
            audioButton.visibility = View.VISIBLE
            audioButton.alpha = 0f
            audioButton.scaleX = 0.3f
            audioButton.scaleY = 0.3f
            audioButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            // 4. Smoothly rotate close button back to 0deg
            closeButton.animate()
                .rotation(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }
    }

    private fun filterFiles(query: String?) {
        filteredList.clear()
        if (query.isNullOrEmpty()) filteredList.addAll(fileList)
        else {
            val q = query.lowercase(Locale.getDefault())
            filteredList.addAll(fileList.filter { it.first.lowercase(Locale.getDefault()).contains(q) })
        }
        adapter.notifyDataSetChanged()
        togglePlaceholder()
    }

    private fun togglePlaceholder() {
        val noFilesAtAll = viewModel.files.value.isNullOrEmpty()
        val noFilteredFiles = filteredList.isEmpty()
        
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
        
        val placeholderTv = view?.findViewById<TextView>(R.id.placeholderText)
        if (placeholderTv != null) {
            if (noFilesAtAll) {
                placeholderTv.setText(R.string.empty_home_message)
                placeholderTv.visibility = View.VISIBLE
            } else if (noFilteredFiles) {
                placeholderTv.setText(R.string.empty_home_search_message)
                placeholderTv.visibility = View.VISIBLE
            } else {
                placeholderTv.visibility = View.GONE
            }
        }
        
        listView.visibility = if (noFilteredFiles) View.GONE else View.VISIBLE
        searchView.visibility = if (noFilesAtAll) View.GONE else View.VISIBLE
    }

    private fun navigateTo(fragment: Fragment) {
        parentFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).addToBackStack(null).commit()
    }

    private fun checkNotificationAndMicrophonePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO)
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1001)
    }

    override fun onResume() { super.onResume(); bottomNavBar.selectedItemId = R.id.menu_home }
}
