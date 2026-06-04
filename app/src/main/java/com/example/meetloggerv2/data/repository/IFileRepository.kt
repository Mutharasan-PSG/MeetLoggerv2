package com.example.meetloggerv2.data.repository

import com.example.meetloggerv2.data.model.User
import com.google.firebase.firestore.ListenerRegistration

interface IFileRepository {
    fun getUserFiles(userId: String, onUpdate: (List<Map<String, Any>>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration
    fun getFileDetails(userId: String, fileName: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit)
    fun updateFileContent(userId: String, fileName: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun deleteFile(userId: String, fileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun renameFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun copyFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun saveFileMetadata(userId: String, fileName: String, data: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun saveUser(user: User, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun getUser(userId: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit)
    fun checkUserExists(userId: String, onResult: (Boolean) -> Unit, onError: (Exception) -> Unit)
}
