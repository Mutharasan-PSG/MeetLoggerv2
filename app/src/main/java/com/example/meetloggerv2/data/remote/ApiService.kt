package com.example.meetloggerv2.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("fileName") fileName: RequestBody,
        @Part("speakers") speakers: RequestBody,
        @Part("followUpFileName") followUpFileName: RequestBody
    ): Response<ResponseBody>
}
