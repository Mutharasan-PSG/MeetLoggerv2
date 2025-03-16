package com.example.meetloggerv2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale
import androidx.core.view.size
import androidx.core.view.get
import com.google.firebase.Timestamp

class HomeFragment : Fragment() {

    private lateinit var profilePic: ImageView
    private lateinit var bottomNavBar: BottomNavigationView
    private lateinit var audioButton: LinearLayout
    private lateinit var audioOptionsLayout: LinearLayout
    private lateinit var audioOptionsOverlay: FrameLayout
    private lateinit var closeButton: ImageView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var fileList: ArrayList<Triple<String, String, Timestamp>>
    private lateinit var filteredList: ArrayList<Triple<String, String, Timestamp>>
    private lateinit var adapter: FileListAdapter
    private var isDataLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val internetCheckTask = object : Runnable {
        override fun run() {
            checkInternetStatus()
            handler.postDelayed(this, 2000) // Check every 2 seconds
        }
    }
    private val fetchDebounceTask = Runnable { fetchFileNamesAndStatus() }
    private val TAG = "HomeFragment"

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        profilePic = view.findViewById(R.id.profilePic)
        bottomNavBar = view.findViewById(R.id.bottomNavBar)
        audioButton = view.findViewById(R.id.AudioButton)
        audioOptionsLayout = view.findViewById(R.id.AudioOptionsLayout)
        audioOptionsOverlay = view.findViewById(R.id.audioOptionsOverlay)
        closeButton = view.findViewById(R.id.closeButton)
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)

        // Disable icon tinting to keep original colors
        bottomNavBar.itemIconTintList = null

        // Force "Home" as selected item
        bottomNavBar.selectedItemId = R.id.menu_home

        // Apply Poppins font to menu items
        applyPoppinsFontToBottomNav(bottomNavBar)

        fileList = ArrayList()
        filteredList = ArrayList()
        adapter = FileListAdapter(requireContext(), filteredList)
        listView.adapter = adapter

        // Initial UI state: defer to checkInternetAndLoad
        view.findViewById<ImageView>(R.id.placeholderImage).visibility = View.GONE
        view.findViewById<TextView>(R.id.placeholderText).visibility = View.GONE
        listView.visibility = View.GONE
        searchView.visibility = View.GONE

        handler.post(internetCheckTask) // Start internet monitoring
        checkInternetAndLoad()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })

        audioButton.setOnClickListener {
            audioOptionsOverlay.visibility = View.VISIBLE
            audioOptionsLayout.visibility = View.VISIBLE
            audioButton.visibility = View.GONE
        }

        closeButton.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
        }

        val recordAudioLayout: LinearLayout = view.findViewById(R.id.RecordAudio)
        val uploadAudioLayout: LinearLayout = view.findViewById(R.id.UploadAudio)

        recordAudioLayout.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
            val recordAudioSheet = RecordAudioBottomsheetFragment()
            recordAudioSheet.show(parentFragmentManager, "RecordAudioSheet")
        }

        uploadAudioLayout.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
            val uploadAudioSheet = UploadAudioBottomsheetFragment()
            uploadAudioSheet.show(parentFragmentManager, "UploadAudioSheet")
        }

        setupBottomNavigation()
        loadUserProfile()

        profilePic.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyPoppinsFontToBottomNav(bottomNavBar: BottomNavigationView) {
        val poppinsFont = ResourcesCompat.getFont(requireContext(), R.font.poppins_medium)
        val menu = bottomNavBar.menu
        for (i in 0 until menu.size) {
            val menuItem = menu[i]
            // Create a SpannableString to apply the typeface
            val spannableTitle = android.text.SpannableString(menuItem.title)
            spannableTitle.setSpan(
                poppinsFont?.let { android.text.style.TypefaceSpan(it) },
                0,
                spannableTitle.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            menuItem.title = spannableTitle
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = try {
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Context unavailable, assuming no network: ${e.message}")
            return false
        }
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkInternetStatus() {
        if (!isAdded) return
        val isOnline = isNetworkAvailable()
        val listView = view?.findViewById<ListView>(R.id.listView)
        val searchView = view?.findViewById<SearchView>(R.id.searchView)
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
        val noInternetContainer = view?.findViewById<LinearLayout>(R.id.noInternetContainer)

        if (!isOnline && noInternetContainer?.isShown != true) {
            listView?.visibility = View.GONE
            searchView?.visibility = View.GONE
            placeholderImage?.visibility = View.GONE
            placeholderText?.visibility = View.GONE
            noInternetContainer?.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Internet connection lost", Toast.LENGTH_SHORT).show()
        } else if (isOnline && noInternetContainer?.isShown == true) {
            noInternetContainer.visibility = View.GONE
            togglePlaceholder()
            searchView?.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Internet connection restored", Toast.LENGTH_SHORT).show()
            scheduleFetchDebounce()
        }
    }

    private fun checkInternetAndLoad() {
        if (!isAdded) return
        val isOnline = isNetworkAvailable()
        val listView = view?.findViewById<ListView>(R.id.listView)
        val searchView = view?.findViewById<SearchView>(R.id.searchView)
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
        val noInternetContainer = view?.findViewById<LinearLayout>(R.id.noInternetContainer)

        if (isOnline) {
            view?.findViewById<View>(R.id.mainContent)?.visibility = View.VISIBLE
            noInternetContainer?.visibility = View.GONE
            fetchFileNamesAndStatus()
        } else {
            view?.findViewById<View>(R.id.mainContent)?.visibility = View.VISIBLE
            listView?.visibility = View.GONE
            searchView?.visibility = View.GONE
            placeholderImage?.visibility = View.GONE
            placeholderText?.visibility = View.GONE
            noInternetContainer?.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> true
                R.id.menu_audio -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, AudioListFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.menu_report -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ReportFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadUserProfile() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded) return@addOnSuccessListener
                val profileUrl = document.getString("photoUrl") ?: return@addOnSuccessListener
                Glide.with(this)
                    .load(profileUrl)
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_pic)
                    .into(profilePic)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Failed to load profile: ${e.message}", e)
            }
    }

    private fun fetchFileNamesAndStatus() {
        if (!isNetworkAvailable()) {
            if (isAdded) {
                val listView = view?.findViewById<ListView>(R.id.listView)
                val searchView = view?.findViewById<SearchView>(R.id.searchView)
                val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
                val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
                val noInternetContainer = view?.findViewById<LinearLayout>(R.id.noInternetContainer)
                listView?.visibility = View.GONE
                searchView?.visibility = View.GONE
                placeholderImage?.visibility = View.GONE
                placeholderText?.visibility = View.GONE
                noInternetContainer?.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found in fetchFileNamesAndStatus")
            return
        }
        val userFilesRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles")

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = Timestamp(calendar.time)

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (!isAdded || !isNetworkAvailable()) return@addSnapshotListener
            if (error != null) {
                Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                togglePlaceholderOnError()
                Toast.makeText(requireContext(), "Failed to load files: ${error.message}", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ fetchFileNamesAndStatus() }, 2000) // Retry after 2s
                return@addSnapshotListener
            }

            Log.d(TAG, "Received snapshot update")
            synchronized(fileList) {
                synchronized(filteredList) {
                    fileList.clear()
                    snapshot?.documents?.forEach { document ->
                        val fileName = document.getString("fileName") ?: run {
                            Log.w(TAG, "Null filename in document: ${document.id}")
                            return@forEach
                        }
                        val status = document.getString("status") ?: "processing"
                        val timestamp = document.getTimestamp("timestamp_clientUpload") ?: return@forEach

                        if (timestamp.toDate().after(sevenDaysAgo.toDate()) || timestamp.toDate() == sevenDaysAgo.toDate()) {
                            fileList.add(Triple(fileName, status, timestamp))
                        }
                    }

                    // Sort fileList by timestamp_clientUpload in descending order (newest first)
                    fileList.sortByDescending { it.third }

                    filteredList.clear()
                    filteredList.addAll(fileList)
                    isDataLoaded = true
                    adapter.notifyDataSetChanged()
                    togglePlaceholder()
                }
            }
        }
    }

    private fun filterFiles(query: String?): Boolean {
        synchronized(filteredList) {
            filteredList.clear()
            if (query.isNullOrEmpty()) {
                filteredList.addAll(fileList)
            } else {
                val lowerCaseQuery = query.lowercase(Locale.getDefault())
                fileList.forEach { (fileName, status, timestamp) ->
                    if (fileName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                        filteredList.add(Triple(fileName, status, timestamp))
                    }
                }
            }

            if (!isAdded) return true
            val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
            val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
            if (filteredList.isEmpty()) {
                placeholderText?.text = if (fileList.isEmpty()) "No files from the last 7 days" else "No files found"
                placeholderText?.visibility = View.VISIBLE
                placeholderImage?.visibility = View.GONE
                listView.visibility = View.GONE
            } else {
                placeholderText?.visibility = View.GONE
                placeholderImage?.visibility = View.GONE
                listView.visibility = View.VISIBLE
            }
            adapter.notifyDataSetChanged()
        }
        return true
    }

    private fun togglePlaceholder() {
        if (!isDataLoaded || !isAdded) return

        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (fileList.isEmpty()) {
            placeholderImage?.visibility = View.VISIBLE
            placeholderText?.visibility = View.GONE
            searchView.visibility = View.GONE
            listView.visibility = View.GONE
        } else {
            placeholderImage?.visibility = View.GONE
            placeholderText?.visibility = View.GONE
            searchView.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
        }
    }

    private fun togglePlaceholderOnError() {
        if (!isAdded) return
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
        placeholderImage?.visibility = View.VISIBLE
        placeholderText?.visibility = View.GONE
        searchView.visibility = View.GONE
        listView.visibility = View.GONE
    }

    private fun scheduleFetchDebounce() {
        handler.removeCallbacks(fetchDebounceTask)
        handler.postDelayed(fetchDebounceTask, 1000) // 1s debounce
    }

    override fun onResume() {
        super.onResume()
        // Ensure "Home" is highlighted when returning to the fragment
        val bottomNavBar = view?.findViewById<BottomNavigationView>(R.id.bottomNavBar)
        bottomNavBar?.selectedItemId = R.id.menu_home
        handler.post(internetCheckTask)
        scheduleFetchDebounce()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(internetCheckTask)
        handler.removeCallbacks(fetchDebounceTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(internetCheckTask)
        handler.removeCallbacks(fetchDebounceTask)
    }
}