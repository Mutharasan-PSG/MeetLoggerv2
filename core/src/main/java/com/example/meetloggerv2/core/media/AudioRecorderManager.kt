package com.example.meetloggerv2.core.media

import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderManager {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false

    fun start(outputFile: File) {
        if (isRecording) return
        
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        isPaused = false
    }

    fun pause() {
        if (!isRecording || isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            isPaused = true
        }
    }

    fun resume() {
        if (!isRecording || !isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            isPaused = false
        }
    }

    fun stop() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignore stop errors if stop is called immediately after preparation/start
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            isPaused = false
        }
    }

    fun release() {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
            }
        } catch (e: Exception) {
            // Ignore stop errors during cleanup
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            isPaused = false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun isRecording(): Boolean = isRecording
    fun isPaused(): Boolean = isPaused
}
