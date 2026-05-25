package com.example.meetloggerv2.ui.home

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
import com.example.meetloggerv2.ui.audio.AudioListFragment
import com.example.meetloggerv2.ui.audio.RecordAudioBottomsheetFragment
import com.example.meetloggerv2.ui.audio.UploadAudioBottomsheetFragment
import com.example.meetloggerv2.ui.profile.ProfileFragment
import com.example.meetloggerv2.ui.report.ReportFragment
import com.example.meetloggerv2.util.NetworkMonitor
import com.example.meetloggerv2.util.UIUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
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
    
    private val viewModel: HomeViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()
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

        bottomNavBar.itemIconTintList = null
        bottomNavBar.selectedItemId = R.id.menu_home
        UIUtils.applyPoppinsFontToBottomNav(requireContext(), bottomNavBar)

        adapter = FileListAdapter(requireContext(), filteredList)
        listView.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            fileList.clear(); fileList.addAll(files)
            filterFiles(searchView.query?.toString())
        }

        viewModel.error.observe(viewLifecycleOwner) { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }

        viewModel.userProfile.observe(viewLifecycleOwner) { data ->
            data?.let {
                Glide.with(this).load(it["photoUrl"]).circleCrop().placeholder(R.drawable.default_profile_pic).into(profilePic)
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            val noNet = view?.findViewById<LinearLayout>(R.id.noInternetContainer)
            val mainContent = view?.findViewById<View>(R.id.mainContent)
            if (isOnline) {
                noNet?.visibility = View.GONE
                mainContent?.visibility = View.VISIBLE
                auth.currentUser?.uid?.let { viewModel.fetchFiles(it) }
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
        auth.currentUser?.uid?.let { viewModel.loadUserProfile(it) }
    }

    private fun toggleAudioOptions(show: Boolean) {
        audioOptionsOverlay.visibility = if (show) View.VISIBLE else View.GONE
        audioOptionsLayout.visibility = if (show) View.VISIBLE else View.GONE
        audioButton.visibility = if (show) View.GONE else View.VISIBLE
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
        
        view?.findViewById<ImageView>(R.id.placeholderImage)?.visibility = if (noFilesAtAll) View.VISIBLE else View.GONE
        view?.findViewById<TextView>(R.id.placeholderText)?.visibility = if (!noFilesAtAll && noFilteredFiles) View.VISIBLE else View.GONE
        
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
