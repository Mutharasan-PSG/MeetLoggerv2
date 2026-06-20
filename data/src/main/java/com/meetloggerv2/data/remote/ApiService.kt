package com.meetloggerv2.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

import com.meetloggerv2.core.model.CheckEmailResponse

interface ApiService {
    @Multipart
    @POST("users/{userId}/files")
    suspend fun uploadAudio(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Part file: MultipartBody.Part,
        @Part("fileName") fileName: RequestBody,
        @Part("speakers") speakers: RequestBody,
        @Part("followUpFileName") followUpFileName: RequestBody,
        @Part("autoSendEmail") autoSendEmail: RequestBody,
        @Part("userEmail") userEmail: RequestBody,
        @Part("userName") userName: RequestBody
    ): Response<ResponseBody>

    @POST("users/{userId}/support")
    suspend fun submitSupport(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Body request: SupportRequest
    ): Response<ResponseBody>

    @POST("auth/check-email")
    suspend fun checkEmail(
        @Body request: Map<String, String>
    ): Response<CheckEmailResponse>

    // --- File Operations ---

    @GET("users/{userId}/files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<List<Map<String, Any>>>

    @GET("users/{userId}/history")
    suspend fun listHistory(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<List<Map<String, Any>>>

    @GET("users/{userId}/files/{fileName}")
    suspend fun getFileDetails(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<Map<String, Any>>

    @PATCH("users/{userId}/files/{fileName}/rename")
    suspend fun renameFile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String,
        @Body request: Map<String, String>,
        @Query("target") target: String? = null
    ): Response<Map<String, String>>

    @DELETE("users/{userId}/files/{fileName}")
    suspend fun deleteFile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String,
        @Query("target") target: String? = null
    ): Response<Map<String, String>>

    @POST("users/{userId}/files/{fileName}/copy")
    suspend fun copyFile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @POST("users/{userId}/files/{fileName}/save-as-new")
    suspend fun saveAsNewCopy(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String,
        @Body request: SaveAsNewRequest
    ): Response<Map<String, String>>

    @PATCH("users/{userId}/files/{fileName}")
    suspend fun updateFileContent(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String,
        @Body request: FileUpdateRequest
    ): Response<Map<String, String>>

    @POST("users/{userId}/files/presigned-upload-url")
    suspend fun getUploadUrl(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @GET("users/{userId}/files/{fileName}/playback-url")
    suspend fun getPlaybackUrl(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<Map<String, String>>

    @GET("users/{userId}/files/{fileName}/download")
    suspend fun downloadAudioFile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<ResponseBody>

    @GET("users/{userId}/files/raw")
    suspend fun listRawFiles(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<List<String>>

    @PUT
    suspend fun uploadToSignedUrl(
        @Url url: String,
        @Body file: RequestBody,
        @Header("Content-Type") contentType: String
    ): Response<Void>

    // --- User Profile ---

    @GET("users/{userId}")
    suspend fun getUserProfile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<Map<String, Any>>

    @PATCH("users/{userId}")
    suspend fun updateUserProfile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Body request: ProfileUpdateRequest
    ): Response<Map<String, String>>

    // --- FCM Token ---

    @POST("users/{userId}/fcm-tokens")
    suspend fun registerFcmToken(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Body request: FcmTokenRequest
    ): Response<Map<String, String>>
}

data class FileUpdateRequest(
    val updates: Map<String, Any>
)

data class SaveAsNewRequest(
    val data: Map<String, Any>
)

data class ProfileUpdateRequest(
    val updates: Map<String, Any>
)

data class SupportRequest(
    val email: String,
    val name: String,
    val subject: String,
    val body: String,
    val token: String
)

data class FcmTokenRequest(
    val token: String,
    val deviceId: String
)

