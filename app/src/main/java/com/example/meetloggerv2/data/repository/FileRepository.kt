package com.example.meetloggerv2.data.repository

import com.example.meetloggerv2.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

class FileRepository() {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    fun getUserFiles(userId: String, onUpdate: (List<Map<String, Any>>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration {
        return firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .orderBy("timestamp_clientUpload", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val files = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.toMutableMap()?.apply { put("id", doc.id) }
                } ?: emptyList()
                onUpdate(files)
            }
    }

    fun getFileDetails(userId: String, fileName: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .get()
            .addOnSuccessListener { onSuccess(it.data) }
            .addOnFailureListener { onError(it) }
    }

    fun updateFileContent(userId: String, fileName: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun deleteFile(userId: String, fileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun renameFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val oldFileRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles").document(oldFullName)
        val newFileRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles").document(newFullName)

        oldFileRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val data = document.data?.toMutableMap() ?: mutableMapOf()
                data["fileName"] = newFullName
                firestore.runTransaction { transaction ->
                    transaction.set(newFileRef, data)
                    transaction.delete(oldFileRef)
                }.addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            } else {
                onSuccess()
            }
        }.addOnFailureListener { onError(it) }
    }

    fun copyFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val oldDocRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles").document(oldFullName)
        val newDocRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles").document(newFullName)

        oldDocRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val newData = document.data?.toMutableMap() ?: mutableMapOf()
                newData["fileName"] = newFullName
                newData.remove("AudioLink")
                newDocRef.set(newData).addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            } else {
                onError(Exception("File not found"))
            }
        }.addOnFailureListener { onError(it) }
    }

    fun saveFileMetadata(userId: String, fileName: String, data: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
    
    fun saveUser(user: User, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("Users").document(user.id)
            .set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getUser(userId: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { onSuccess(it.data) }
            .addOnFailureListener { onError(it) }
    }

    fun checkUserExists(userId: String, onResult: (Boolean) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onError(it) }
    }
}
