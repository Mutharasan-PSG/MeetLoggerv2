package com.example.meetloggerv2

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.concurrent.TimeUnit

class AudioListFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var miniPlayer: View
    private lateinit var playPauseButton: ImageView
    private lateinit var stopButton: ImageView
    private lateinit var prevButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var currentAudioName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private var isPlaying = false
    private var currentAudioIndex: Int = -1
    private var isDeleteMode = false
    private val selectedItems = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying) {
                val currentPosition = mediaPlayer.currentPosition
                seekBar.progress = currentPosition
                currentTime.text = formatTime(currentPosition)
            }
            handler.postDelayed(this, 1000)
        }
    }
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_audio_list, container, false)
        initializeViews(view)

        view.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.placeholderText)?.visibility = View.GONE

        setupListView()
        setupMediaControls()
        setupDeleteIcon()
        setupMiniPlayerDragging()

        fetchAudioFiles()

        return view
    }

    private fun initializeViews(view: View) {
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        stopButton = view.findViewById(R.id.stopButton)
        prevButton = view.findViewById(R.id.prevButton)
        nextButton = view.findViewById(R.id.nextButton)
        currentAudioName = view.findViewById(R.id.currentAudioName)
        seekBar = view.findViewById(R.id.seekBar)
        currentTime = view.findViewById(R.id.currentTime)
        totalTime = view.findViewById(R.id.totalTime)

        fileNamesList = ArrayList()
        filteredList = ArrayList()
        mediaPlayer = MediaPlayer()
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item_3, R.id.textViewFileName, filteredList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                checkbox.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedItems.add(position) else selectedItems.remove(position)
                    updateDeleteIconVisibility()
                }
                return view
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode) {
                val selectedFileName = filteredList[position]
                currentAudioIndex = position
                downloadAndPlayAudio(selectedFileName)
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isDeleteMode) {
                toggleDeleteMode(true)
                selectedItems.add(position)
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                true
            } else false
        }
    }

    private fun setupMediaControls() {
        playPauseButton.setOnClickListener { togglePlayPause() }
        stopButton.setOnClickListener { stopPlayback() }
        prevButton.setOnClickListener { playPreviousAudio() }
        nextButton.setOnClickListener { playNextAudio() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                    currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })
    }

    private fun setupDeleteIcon() {
        deleteIcon.setOnClickListener {
            if (isDeleteMode && selectedItems.isNotEmpty()) {
                showDeleteConfirmationDialog()
            }
        }
    }

    private fun setupMiniPlayerDragging() {
        miniPlayer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    v.x = initialX + deltaX
                    v.y = initialY + deltaY

                    val parent = v.parent as View
                    v.x = v.x.coerceIn(0f, parent.width - v.width.toFloat())
                    v.y = v.y.coerceIn(0f, parent.height - v.height.toFloat())
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleDeleteMode(enable: Boolean) {
        isDeleteMode = enable
        if (!enable) selectedItems.clear()
        adapter.notifyDataSetChanged()
        updateDeleteIconVisibility()
    }

    private fun updateDeleteIconVisibility() {
        deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage("Are you sure you want to delete the selected audio files?")
            .setPositiveButton("OK") { _, _ -> deleteSelectedItems() }
            .setNegativeButton("Cancel") { _, _ -> toggleDeleteMode(false) }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLUE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
    }

    private fun deleteSelectedItems() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/")

        val itemsToDelete = selectedItems.map { filteredList[it] }.toList()
        itemsToDelete.forEach { fileName ->
            val fileRef = storageRef.child("$fileName.mp3")
            fileRef.delete()
        }

        fetchAudioFiles()
        toggleDeleteMode(false)
    }

    private fun downloadAndPlayAudio(fileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val localDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val localFile = File(localDir, "$fileName.mp3")

        mediaPlayer.reset()

        if (localFile.exists()) {
            playAudio(localFile.absolutePath, fileName)
        } else {
            storageRef.getFile(localFile).addOnSuccessListener {
                playAudio(localFile.absolutePath, fileName)
            }.addOnFailureListener {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playAudio(filePath: String, fileName: String) {
        mediaPlayer.setDataSource(filePath)
        mediaPlayer.prepare()
        mediaPlayer.start()

        // Ensure mini player is visible immediately
        miniPlayer.visibility = View.VISIBLE
        currentAudioName.text = fileName
        playPauseButton.setImageResource(R.drawable.pause)
        isPlaying = true

        // Do not reset position here - let it stay where it is
        seekBar.max = mediaPlayer.duration
        totalTime.text = formatTime(mediaPlayer.duration)
        currentTime.text = "00:00"
        handler.post(updateSeekBarTask)

        mediaPlayer.setOnCompletionListener {
            playNextAudio()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer.pause()
            playPauseButton.setImageResource(R.drawable.play)
            handler.removeCallbacks(updateSeekBarTask)
        } else {
            mediaPlayer.start()
            playPauseButton.setImageResource(R.drawable.pause)
            handler.post(updateSeekBarTask)
        }
        isPlaying = !isPlaying
    }

    private fun stopPlayback() {
        mediaPlayer.reset()
        miniPlayer.visibility = View.GONE
        playPauseButton.setImageResource(R.drawable.play)
        isPlaying = false
        handler.removeCallbacks(updateSeekBarTask)
        currentTime.text = "00:00"
        seekBar.progress = 0
    }

    private fun playPreviousAudio() {
        if (filteredList.isEmpty()) return

        if (currentAudioIndex > 0) {
            currentAudioIndex--
        } else {
            // If at start, go to last audio or replay if only one
            currentAudioIndex = if (filteredList.size > 1) filteredList.size - 1 else 0
        }
        downloadAndPlayAudio(filteredList[currentAudioIndex])
    }

    private fun playNextAudio() {
        if (filteredList.isEmpty()) return

        if (currentAudioIndex < filteredList.size - 1) {
            currentAudioIndex++
        } else {
            // If at end, go to first audio or replay if only one
            currentAudioIndex = 0
        }
        downloadAndPlayAudio(filteredList[currentAudioIndex])
    }

    private fun fetchAudioFiles() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/")

        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                fileNamesList.clear()
                for (file in listResult.items) {
                    fileNamesList.add(file.name.substringBeforeLast("."))
                }
                filteredList.clear()
                filteredList.addAll(fileNamesList)
                adapter.notifyDataSetChanged()
                togglePlaceholder()
                updateDeleteIconVisibility()
            }
            .addOnFailureListener {
                togglePlaceholder()
                Toast.makeText(context, "Failed to load audio files", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterFiles(query: String?): Boolean {
        filteredList.clear()
        if (query.isNullOrEmpty()) {
            filteredList.addAll(fileNamesList)
        } else {
            val lowerCaseQuery = query.toLowerCase()
            fileNamesList.forEach { fileName ->
                if (fileName.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(fileName)
                }
            }
        }

        adapter.notifyDataSetChanged()

        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
        if (filteredList.isEmpty()) {
            if (fileNamesList.isEmpty()) {
                // No files exist (initial state)
                placeholderText?.text = "No files found"
                placeholderImage?.visibility = View.GONE
            } else {
                // Search result empty
                placeholderText?.visibility = View.GONE
                placeholderImage?.visibility = View.GONE
            }
            placeholderText?.visibility = View.VISIBLE
            placeholderText?.text = "No files found"
            listView.visibility = View.GONE
        } else {
            placeholderText?.visibility = View.GONE
            placeholderImage?.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        updateDeleteIconVisibility()
        return true
    }

    private fun togglePlaceholder() {
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (fileNamesList.isEmpty()) {
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

    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarTask)
        mediaPlayer.release()
    }
}