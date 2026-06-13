package com.example.meetloggerv2.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

import com.example.meetloggerv2.core.model.CheckEmailResponse

interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadAudio(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("fileName") fileName: RequestBody,
        @Part("speakers") speakers: RequestBody,
        @Part("followUpFileName") followUpFileName: RequestBody,
        @Part("autoSendEmail") autoSendEmail: RequestBody,
        @Part("userEmail") userEmail: RequestBody,
        @Part("userName") userName: RequestBody
    ): Response<ResponseBody>

    @POST("support")
    suspend fun submitSupport(
        @Header("Authorization") authorization: String,
        @Body request: SupportRequest
    ): Response<ResponseBody>

    @POST("check-email")
    suspend fun checkEmail(
        @Body request: Map<String, String>
    ): Response<CheckEmailResponse>

    // --- File Operations ---

    @GET("files/{userId}")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<List<Map<String, Any>>>

    @GET("files/{userId}/{fileName}")
    suspend fun getFileDetails(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<Map<String, Any>>

    @POST("files/rename")
    suspend fun renameFile(
        @Header("Authorization") authorization: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @POST("files/delete")
    suspend fun deleteFile(
        @Header("Authorization") authorization: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @POST("files/copy")
    suspend fun copyFile(
        @Header("Authorization") authorization: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @POST("files/save-as-new")
    suspend fun saveAsNewCopy(
        @Header("Authorization") authorization: String,
        @Body request: SaveAsNewRequest
    ): Response<Map<String, String>>

    @POST("files/update")
    suspend fun updateFileContent(
        @Header("Authorization") authorization: String,
        @Body request: FileUpdateRequest
    ): Response<Map<String, String>>

    @POST("files/upload-url")
    suspend fun getUploadUrl(
        @Header("Authorization") authorization: String,
        @Body request: Map<String, String>
    ): Response<Map<String, String>>

    @GET("files/playback-url/{userId}/{fileName}")
    suspend fun getPlaybackUrl(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<Map<String, String>>

    @GET("files/download/{userId}/{fileName}")
    suspend fun downloadAudioFile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String,
        @Path("fileName") fileName: String
    ): Response<ResponseBody>

    @GET("files/raw/{userId}")
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

    @GET("user/{userId}")
    suspend fun getUserProfile(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: String
    ): Response<Map<String, Any>>

    @POST("user/update")
    suspend fun updateUserProfile(
        @Header("Authorization") authorization: String,
        @Body request: ProfileUpdateRequest
    ): Response<Map<String, String>>

    // --- FCM Token ---

    @POST("user/fcm-token")
    suspend fun registerFcmToken(
        @Header("Authorization") authorization: String,
        @Body request: FcmTokenRequest
    ): Response<Map<String, String>>
}

data class FileUpdateRequest(
    val userId: String,
    val fileName: String,
    val updates: Map<String, Any>
)

data class SaveAsNewRequest(
    val userId: String,
    val fileName: String,
    val data: Map<String, Any>
)

data class ProfileUpdateRequest(
    val userId: String,
    val updates: Map<String, Any>
)

data class SupportRequest(
    val userId: String,
    val email: String,
    val name: String,
    val subject: String,
    val body: String,
    val token: String
)

data class FcmTokenRequest(
    val userId: String,
    val token: String,
    val deviceId: String
)

