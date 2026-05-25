package com.example.meetloggerv2.util

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateCallback: ((Int, Int) -> Unit)? = null

    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    updateCallback?.invoke(it.currentPosition, it.duration)
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    fun play(filePath: String, onCompletion: () -> Unit, onUpdate: (Int, Int) -> Unit) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
            setOnCompletionListener { 
                handler.removeCallbacks(updateSeekBarTask)
                onCompletion() 
            }
        }
        updateCallback = onUpdate
        handler.post(updateSeekBarTask)
    }

    fun togglePlayPause(): Boolean {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                handler.removeCallbacks(updateSeekBarTask)
                return false
            } else {
                it.start()
                handler.post(updateSeekBarTask)
                return true
            }
        }
        return false
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacks(updateSeekBarTask)
    }

    fun seekTo(progress: Int) {
        mediaPlayer?.seekTo(progress)
    }

    fun rewind() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPosition)
        }
    }

    fun fastForward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition + 10000).coerceAtMost(it.duration)
            it.seekTo(newPosition)
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun isReady(): Boolean = mediaPlayer != null

    fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
