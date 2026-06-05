package com.example.meetloggerv2.ui.details.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.data.repository.IFileRepository
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FileDetailsViewModel @Inject constructor(
    private val fileRepository: IFileRepository,
    private val authSession: AuthSession
) : ViewModel() {

    private val _fileDetails = MutableStateFlow<Map<String, Any>?>(null)
    val fileDetails: StateFlow<Map<String, Any>?> = _fileDetails.asStateFlow()

    private val _translatedText = MutableStateFlow<String>("")
    val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Idle)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    fun fetchDetails(fileName: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = DetailsUiState.Loading("Loading details...")
        fileRepository.getFileDetails(userId, fileName, {
            _fileDetails.value = it
            _uiState.value = DetailsUiState.Idle
        }, {
            _uiState.value = DetailsUiState.Error(it.message ?: "Failed to fetch details")
        })
    }

    fun translateContent(text: String, sourceLang: String, targetLang: String) {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading("Downloading translation model...")
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                val translator = Translation.getClient(options)
                val conditions = DownloadConditions.Builder().build()
                
                translator.downloadModelIfNeeded(conditions).await()
                
                _uiState.value = DetailsUiState.Loading("Translating...")
                
                val paragraphs = text.split("\n\n")
                val translatedParagraphs = paragraphs.map { p ->
                    if (p.isBlank()) "" else translator.translate(p).await()
                }
                
                val result = translatedParagraphs.joinToString("\n\n")
                _translatedText.value = result
                _uiState.value = DetailsUiState.Idle
                translator.close()
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error("Translation failed: ${e.message}")
            }
        }
    }

    fun updateContent(fileName: String, content: String, languageCode: String) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = DetailsUiState.Loading("Updating...")
        val updates = mapOf(
            "Response" to content,
            "OriginalLanguage" to languageCode
        )
        fileRepository.updateFileContent(userId, fileName, updates, {
            _uiState.value = DetailsUiState.Success("Content updated")
            fetchDetails(fileName)
        }, {
            _uiState.value = DetailsUiState.Error(it.message ?: "Update failed")
        })
    }

    fun saveAsNewCopy(newFileName: String, data: Map<String, Any>) {
        val userId = authSession.currentUserId() ?: return
        _uiState.value = DetailsUiState.Loading("Saving new copy...")
        val newData = data.toMutableMap()
        newData["isCopy"] = true
        fileRepository.saveFileMetadata(userId, newFileName, newData, {
            _uiState.value = DetailsUiState.NewFileCreated(newFileName)
        }, {
            _uiState.value = DetailsUiState.Error(it.message ?: "Failed to create new copy")
        })
    }

    sealed class DetailsUiState {
        object Idle : DetailsUiState()
        data class Loading(val message: String) : DetailsUiState()
        data class Success(val message: String) : DetailsUiState()
        data class NewFileCreated(val fileName: String) : DetailsUiState()
        data class Error(val message: String) : DetailsUiState()
    }
}
