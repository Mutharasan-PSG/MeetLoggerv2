package com.example.meetloggerv2.data.repository

import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.data.local.db.UserDao
import com.example.meetloggerv2.data.local.db.LocalFileDao
import com.example.meetloggerv2.data.local.db.UserEntity
import com.example.meetloggerv2.data.local.db.LocalFileEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.*
import javax.inject.Inject

class FileRepository @Inject constructor(
    private val userDao: UserDao,
    private val localFileDao: LocalFileDao
) : IFileRepository {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun getUserFiles(userId: String, onUpdate: (List<Map<String, Any>>) -> Unit, onError: (Exception) -> Unit): ListenerRegistration {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val cached = localFileDao.getUserFiles(userId).map { it.toMap() }
                withContext(Dispatchers.Main) {
                    onUpdate(cached)
                }
            } catch (e: Exception) {
                // Ignore local database errors for initial cache load
            }
        }

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

                scope.launch {
                    try {
                        val entities = files.map { LocalFileEntity.fromMap(userId, it) }
                        localFileDao.clearUserFiles(userId)
                        localFileDao.insertFiles(entities)

                        val updatedCached = localFileDao.getUserFiles(userId).map { it.toMap() }
                        withContext(Dispatchers.Main) {
                            onUpdate(updatedCached)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError(e)
                        }
                    }
                }
            }
    }

    override fun getFileDetails(userId: String, fileName: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val cached = localFileDao.getFileDetails(userId, fileName)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        onSuccess(cached.toMap())
                    }
                }
            } catch (e: Exception) {
                // Ignore local read error
            }
        }

        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (data != null) {
                    scope.launch {
                        try {
                            val entity = LocalFileEntity.fromMap(userId, data.toMutableMap().apply { put("fileName", fileName) })
                            localFileDao.insertFile(entity)
                            withContext(Dispatchers.Main) {
                                onSuccess(entity.toMap())
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                onError(e)
                            }
                        }
                    }
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onError(it) }
    }

    override fun updateFileContent(userId: String, fileName: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val existing = localFileDao.getFileDetails(userId, fileName)
                if (existing != null) {
                    val mergedData = existing.toMap().toMutableMap().apply {
                        putAll(updates)
                    }
                    val updatedEntity = LocalFileEntity.fromMap(userId, mergedData)
                    localFileDao.insertFile(updatedEntity)
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Ignore local save errors, prioritize remote sync success
            }
        }

        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .update(updates)
            .addOnSuccessListener {
                // Already updated locally in background, if remote succeeds we do nothing more
            }
            .addOnFailureListener { onError(it) }
    }

    override fun deleteFile(userId: String, fileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                localFileDao.deleteFile(userId, fileName)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Ignore local database error
            }
        }

        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .delete()
            .addOnFailureListener { onError(it) }
    }

    override fun renameFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val existing = localFileDao.getFileDetails(userId, oldFullName)
                if (existing != null) {
                    val newEntity = existing.copy(fileName = newFullName)
                    localFileDao.deleteFile(userId, oldFullName)
                    localFileDao.insertFile(newEntity)
                }
            } catch (e: Exception) {
                // Ignore local database error
            }
        }

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

    override fun copyFile(userId: String, oldFullName: String, newFullName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val existing = localFileDao.getFileDetails(userId, oldFullName)
                if (existing != null) {
                    val copyEntity = existing.copy(fileName = newFullName, audioUrl = null, isCopy = true)
                    localFileDao.insertFile(copyEntity)
                }
            } catch (e: Exception) {
                // Ignore local database error
            }
        }

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

    override fun saveFileMetadata(userId: String, fileName: String, data: Map<String, Any>, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val entity = LocalFileEntity.fromMap(userId, data.toMutableMap().apply { put("fileName", fileName) })
                localFileDao.insertFile(entity)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Ignore local database error
            }
        }

        firestore.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)
            .set(data)
            .addOnFailureListener { onError(it) }
    }
    
    override fun saveUser(user: User, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                userDao.insertUser(UserEntity.fromUser(user))
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                // Ignore local database error
            }
        }

        firestore.collection("Users").document(user.id)
            .set(user)
            .addOnFailureListener { onError(it) }
    }

    override fun getUser(userId: String, onSuccess: (Map<String, Any>?) -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val cached = userDao.getUserById(userId)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        val userMap = mapOf(
                            "name" to cached.name,
                            "email" to cached.email,
                            "photoUrl" to cached.photoUrl.orEmpty()
                        )
                        onSuccess(userMap)
                    }
                }
            } catch (e: Exception) {
                // Ignore local read error
            }
        }

        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (data != null) {
                    scope.launch {
                        try {
                            val name = data["name"] as? String ?: ""
                            val email = data["email"] as? String ?: ""
                            val photoUrl = data["photoUrl"] as? String
                            userDao.insertUser(UserEntity(userId, name, email, photoUrl))
                        } catch (e: Exception) {
                            // Ignore local write error
                        }
                    }
                    onSuccess(data)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onError(it) }
    }

    override fun checkUserExists(userId: String, onResult: (Boolean) -> Unit, onError: (Exception) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val cached = userDao.getUserById(userId)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                // Ignore local read error
            }
        }

        firestore.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { onResult(it.exists()) }
            .addOnFailureListener { onError(it) }
      }
}
