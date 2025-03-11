package com.example.meetloggerv2

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class AudioListFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
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
    private var isPlaying = false
    private var currentAudioIndex: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_audio_list, container, false)
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        stopButton = view.findViewById(R.id.stopButton)
        prevButton = view.findViewById(R.id.prevButton)
        nextButton = view.findViewById(R.id.nextButton)
        currentAudioName = view.findViewById(R.id.currentAudioName)

        listView.setSelector(android.R.color.transparent)

        fileNamesList = ArrayList()
        filteredList = ArrayList()

        adapter = ArrayAdapter(requireContext(), R.layout.list_item, R.id.textViewFileName, filteredList)
        listView.adapter = adapter

        mediaPlayer = MediaPlayer()
        fetchAudioFiles()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterFiles(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterFiles(newText)
                return true
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedFileName = filteredList[position]
            downloadAndPlayAudio(selectedFileName)
        }

        playPauseButton.setOnClickListener {
            if (isPlaying) {
                mediaPlayer.pause()
                playPauseButton.setImageResource(R.drawable.play)
            } else {
                mediaPlayer.start()
                playPauseButton.setImageResource(R.drawable.pause)
            }
            isPlaying = !isPlaying
        }

        stopButton.setOnClickListener { stopPlayback() }
        prevButton.setOnClickListener { playPreviousAudio() }
        nextButton.setOnClickListener { playNextAudio() }

        return view
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
            }
    }

    private fun filterFiles(query: String?) {
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

        adapter.notifyDataSetChanged()
    }

    private fun togglePlaceholder() {
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (filteredList.isEmpty()) {
            placeholderText?.visibility = View.VISIBLE
            placeholderImage?.visibility = View.VISIBLE
            searchView.visibility = View.GONE
            listView.visibility = View.GONE
        } else {
            placeholderText?.visibility = View.GONE
            placeholderImage?.visibility = View.GONE
            searchView.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
        }
    }

    private fun downloadAndPlayAudio(fileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")

        val localFile = File.createTempFile("audio", ".mp3")

        storageRef.getFile(localFile).addOnSuccessListener {
            playAudio(localFile.absolutePath, fileName)
        }
    }

    private fun playAudio(filePath: String, fileName: String) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }

        mediaPlayer.setDataSource(filePath)
        mediaPlayer.prepare()
        mediaPlayer.start()

        miniPlayer.visibility = View.VISIBLE
        currentAudioName.text = fileName
        playPauseButton.setImageResource(R.drawable.pause)
        isPlaying = true

        // Set completion listener to loop
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.seekTo(0) // Reset to the beginning
            mediaPlayer.start()   // Restart playback
        }
    }

    private fun stopPlayback() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
        miniPlayer.visibility = View.GONE
        playPauseButton.setImageResource(R.drawable.play)
        isPlaying = false
    }

    private fun playPreviousAudio() {
        if (currentAudioIndex > 0) {
            currentAudioIndex--
            downloadAndPlayAudio(filteredList[currentAudioIndex])
        }
    }

    private fun playNextAudio() {
        if (currentAudioIndex < filteredList.size - 1) {
            currentAudioIndex++
            downloadAndPlayAudio(filteredList[currentAudioIndex])
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
